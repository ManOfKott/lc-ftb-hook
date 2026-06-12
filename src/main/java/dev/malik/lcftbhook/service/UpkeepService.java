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
import dev.malik.lcftbhook.teams.TeamLinkRegistry;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
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

        // Never charge immediately after a restart; wait one full period first.
        if (nextUpkeepTick < 0L) {
            nextUpkeepTick = server.getTickCount() + periodTicks;
            return;
        }

        if (server.getPlayerList().getPlayerCount() <= 0) {
            // Pause the countdown while the server is empty.
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

        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
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
        if (team.isPartyTeam() && !TeamLinkRegistry.isFtbPartyInUse(server, team)) {
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

        MoneyValue cost = ProtectionPricing.calculateTotalUpkeepCost(team, pendingState);
        LCFtbHook.LOGGER.info("[PendingDebug] Upkeep for team {}: chunks={}, forceLoads={}, cost={}, pendingEmpty={}",
                team.getShortName(), chunkCount, forceLoadCount, cost.getString(), pendingState.isEmpty());

        if (cost.isEmpty()) {
            if (!pendingState.isEmpty()) {
                PendingChangeService.applyPendingChanges(server, team);
            }
            ProtectionService.tryUnlock(server, team);
            return;
        }

        IBankAccount account = BankAccountHelper.getAccountForTeam(server, team);
        if (!account.getMoneyStorage().containsValue(cost)) {
            LCFtbHook.LOGGER.info("[PendingDebug] Team {}: insufficient funds for upkeep (cost={}, balance={})",
                    team.getShortName(), cost.getString(), account.getMoneyStorage().getAllValueText().getString());
            ProtectionService.enforceInsufficientFunds(server, team);
            return;
        }

        if (!pendingState.isEmpty()) {
            PendingChangeService.applyPendingChanges(server, team);
            chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
            forceLoadCount = chunkData.getForceLoadedChunks().size();
            cost = ProtectionPricing.calculateTotalUpkeepCost(team, chunkCount, forceLoadCount);
        }

        account.withdrawMoney(cost);
        savedData.setProtectionLocked(team.getTeamId(), false);
        ProtectionService.tryUnlock(server, team);

        UpkeepBreakdown breakdown = UpkeepBreakdown.capture(team, chunkCount, forceLoadCount, cost);
        UpkeepBreakdownStore.store(breakdown);
        ProtectionService.notifyTeamManagers(server, team, UpkeepMessageBuilder.buildSummary(breakdown));
    }
}
