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
    private long nextUpkeepTick = -1L;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long periodTicks = LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get() * 60L * 20L;

        if (nextUpkeepTick < 0L) {
            nextUpkeepTick = server.getTickCount() + periodTicks;
            return;
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
            } catch (Exception e) {
                LCFtbHook.LOGGER.error("Failed to process upkeep for team {}", team.getId(), e);
            }
        }
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

        MoneyValue projectedCost = WarService.calculateTotalUpkeepCost(server, team, pendingState);
        LCFtbHook.LOGGER.info("[PendingDebug] Upkeep for team {}: chunks={}, forceLoads={}, cost={}, pendingEmpty={}",
                team.getShortName(), chunkCount, forceLoadCount, projectedCost.getString(), pendingState.isEmpty());

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

        if (result.charged().isEmpty()) {
            ProtectionService.tryUnlock(server, team);
            return;
        }

        ProtectionService.tryUnlock(server, team);
        ProtectionService.notifyTeamManagers(server, team, UpkeepMessageBuilder.buildSummary(breakdown));
    }
}
