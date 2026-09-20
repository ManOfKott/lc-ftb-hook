package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.teams.FtbTeamCatalog;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class UpkeepService {
    // Static, not instance state - there's only ever one UpkeepService
    // instance (registered once in LCFtbHook), and every team shares the
    // same next-settlement tick since processTeamUpkeep below runs for all
    // of them in a single pass. Static makes it queryable from anywhere
    // (e.g. ClaimPriceSync, for "next payment due" display) without needing
    // a reference to that one instance.
    private static volatile long nextUpkeepTick = -1L;

    private static volatile boolean forceNextTick = false;

    /** Debug/testing hook (see {@code /lc_ftb_hook force_upkeep}): makes every tracked team's upkeep due on the very next server tick. */
    public static void forceNextUpkeep() {
        forceNextTick = true;
    }

    /** Minutes until the next settlement pass, or -1 if not yet known (server just started). Pauses while no players are online, same as the settlement loop itself. */
    public static int minutesUntilNextUpkeep(MinecraftServer server) {
        if (nextUpkeepTick < 0L) {
            return -1;
        }
        long remainingTicks = Math.max(0L, nextUpkeepTick - server.getTickCount());
        return (int) Math.ceil(remainingTicks / 20.0 / 60.0);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long periodTicks = LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get() * 60L * 20L;

        if (nextUpkeepTick < 0L) {
            nextUpkeepTick = server.getTickCount() + periodTicks;
            return;
        }

        if (forceNextTick) {
            forceNextTick = false;
            nextUpkeepTick = server.getTickCount();
        }

        if (server.getPlayerList().getPlayerCount() <= 0) {
            nextUpkeepTick++;
            return;
        }

        if (server.getTickCount() < nextUpkeepTick) {
            return;
        }

        nextUpkeepTick = server.getTickCount() + periodTicks;

        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }

        for (Team team : FtbTeamCatalog.trackedTeams(server)) {
            try {
                processTeamUpkeep(server, team);
            } catch (Throwable e) {
                // Throwable (not just Exception): a transient classloading hiccup
                // (NoClassDefFoundError - see PartyTeamRankSyncMixin's javadoc for
                // the general pattern) must never kill the whole server tick loop.
                LCFtbHook.LOGGER.error("Failed to process upkeep for team {}", team.getId(), e);
            }
        }
        // One broadcast for the whole settlement pass (this only runs once per
        // upkeep period, so doing it unconditionally rather than tracking a
        // per-team dirty flag is negligible overhead) - covers every team whose
        // unsettled chunks just got cleared by processTeamUpkeep above.
        dev.malik.lcftbhook.service.MarketplaceService.broadcastUnsettledChunks(server);
    }

    private void processTeamUpkeep(MinecraftServer server, Team team) {
        if (!team.isValid()) {
            return;
        }

        BankAccountHelper.ensurePartyAccountExists(server, team);
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());

        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        int chunkCount = chunkData.getClaimedChunks().size();
        int forceLoadCount = ProtectionPricing.countEffectiveForceLoads(chunkData, pendingState);

        if (chunkCount <= 0 && forceLoadCount <= 0 && pendingState.isEmpty()) {
            ProtectionService.tryUnlock(server, team);
            return;
        }

        UpkeepSettlementService.SettlementResult result = UpkeepSettlementService.settle(server, team);

        if (result.anythingRestored()) {
            ProtectionService.notifyTeam(server, team,
                    UpkeepMessageBuilder.buildRestorationSummary(result.restoredProtections(), result.restoredWarNames()));
        }

        if (result.anythingSuspended()) {
            ProtectionService.notifyTeam(server, team,
                    UpkeepMessageBuilder.buildSuspensionSummary(result.suspendedProtections(), result.warsSuspended()));
        }

        if (!result.unaffordableRestorations().isEmpty()) {
            ProtectionService.notifyTeam(server, team,
                    UpkeepMessageBuilder.buildUnaffordableRestorationMessage(result.unaffordableRestorations()));
        }

        UpkeepBreakdown breakdown = UpkeepBreakdown.capture(
                server,
                team,
                result.forceLoadCount(),
                result.paid() ? result.charged() : MoneyValue.empty(),
                result.pendingState()
        );
        UpkeepBreakdownStore.store(breakdown);

        if (!result.paid()) {
            return;
        }

        // Upkeep successfully settled - every chunk claimed/bought since the
        // last settlement now uses its Region's real protection instead of
        // the "no protection at all yet" minimum.
        FtbHookSavedData.get(server).clearUnsettledChunks(team.getTeamId());
        // Also lifts the marketplace "not sellable yet" cooldown on chunks
        // freshly claimed since the last settlement.
        FtbHookSavedData.get(server).clearFreshlyClaimedChunks(team.getTeamId());
        dev.malik.lcftbhook.service.MarketplaceService.broadcastUnsettledChunks(server);

        if (result.charged().isEmpty()) {
            ProtectionService.tryUnlock(server, team);
            return;
        }

        ProtectionService.tryUnlock(server, team);
        ProtectionService.notifyTeamManagers(server, team, UpkeepMessageBuilder.buildSummary(breakdown));
    }
}
