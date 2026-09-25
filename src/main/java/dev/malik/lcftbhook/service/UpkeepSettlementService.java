package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.network.chat.Component;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.service.ProtectionDismantleOrder.DismantleStep;
import dev.malik.lcftbhook.teams.FtbTeamCatalog;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Upkeep settlement: restore protections before wars; dismantle wars before
 * protections. Queued and paused protections share pendingProperties and
 * follow the configured per-region dismantle order (regionOrder index 0
 * dismantled first - RegionListScreen displays this reversed, see its class
 * doc - then the configured property order within it).
 */
public final class UpkeepSettlementService {
    public record SettlementResult(
            boolean paid,
            MoneyValue charged,
            TeamPendingState pendingState,
            int forceLoadCount,
            List<DismantleStep> suspendedProtections,
            List<String> suspendedWarNames,
            List<DismantleStep> restoredProtections,
            List<String> restoredWarNames,
            List<DismantleStep> unaffordableRestorations,
            List<String> unaffordableWarNames
    ) {
        public static SettlementResult skipped() {
            return new SettlementResult(false, MoneyValue.empty(), new TeamPendingState(), 0,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        public boolean anythingSuspended() {
            return !suspendedProtections.isEmpty() || !suspendedWarNames.isEmpty();
        }

        public boolean anythingRestored() {
            return !restoredProtections.isEmpty() || !restoredWarNames.isEmpty();
        }
    }

    private UpkeepSettlementService() {
    }

    public static SettlementResult settle(MinecraftServer server, Team team) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        TeamPendingState pendingState = savedData.getPendingState(teamId);
        IBankAccount account = BankAccountHelper.getAccountForTeam(server, team);

        List<DismantleStep> suspended = new ArrayList<>();
        List<String> suspendedWarNames = new ArrayList<>();
        List<DismantleStep> restored = new ArrayList<>();
        List<String> restoredWarNames = new ArrayList<>();
        List<DismantleStep> unaffordable = new ArrayList<>();
        List<String> unaffordableWarNames = new ArrayList<>();

        boolean chargeProtection = UpkeepGating.shouldChargeProtection(server, team);
        boolean chargeForceLoad = UpkeepGating.shouldChargeForceLoad(server, team);

        ProtectionService.setApplying(true);
        try {
            pendingState = RegionService.applyPendingRegionAssignments(server, team, pendingState);
            pendingState = applyUserWarEnds(server, team, savedData, pendingState);
            if (chargeProtection) {
                // Frozen while paused: no restores are attempted (protection
                // costs are already gated to 0 inside ProtectionPricing, but
                // that alone doesn't stop these loops from running against
                // whatever forceload/war cost remains, so gate explicitly).
                pendingState = restorePendingProtections(server, team, pendingState, account, restored, unaffordable);
            }
            pendingState = restorePendingWars(server, team, savedData, pendingState, account, restoredWarNames, unaffordableWarNames);
            pendingState = dismantleOutgoingWarsUntilAffordable(server, team, savedData, pendingState, account, suspendedWarNames);
            // Force-loads sit between wars and protections in dismantle
            // priority - wars are cut first, then force-loads one chunk at a
            // time (stopping as soon as affordable, same as protections and
            // wars), and protections are cut last (only once neither of
            // those alone was enough). Same single settlement, same period
            // as everything else - not a separate cycle. Restore already
            // matched this symmetrically without needing a change: it's the
            // tail of restorePendingProtections, i.e. protections restored
            // first, force-loads second, wars last (see that method).
            if (chargeForceLoad) {
                pendingState = dismantleForceLoadsUntilAffordable(server, team, pendingState, account);
            }
            if (chargeProtection) {
                pendingState = dismantleProtectionsUntilAffordable(server, team, pendingState, account, suspended);
            }

            MoneyValue cost = MoneyUtil.fromCopper(WarService.calculateTotalUpkeepCostCopper(server, team, pendingState));
            if (cost.isEmpty()) {
                savedData.setPendingState(teamId, pendingState);
                savedData.setProtectionLocked(teamId, false);
                syncState(server, team);
                return new SettlementResult(true, MoneyValue.empty(), pendingState, forceLoadCount(team), List.copyOf(suspended), List.copyOf(suspendedWarNames), List.copyOf(restored), List.copyOf(restoredWarNames), List.copyOf(unaffordable), List.copyOf(unaffordableWarNames));
            }

            if (!cost.isEmpty() && !account.getMoneyStorage().containsValue(cost)) {
                savedData.setPendingState(teamId, pendingState);
                savedData.setProtectionLocked(teamId, true);
                ProtectionService.notifyTeam(server, team, "message.lc_ftb_hook.upkeep_unpaid_frozen");
                syncState(server, team);
                return new SettlementResult(false, MoneyValue.empty(), pendingState, forceLoadCount(team), List.copyOf(suspended), List.copyOf(suspendedWarNames), List.copyOf(restored), List.copyOf(restoredWarNames), List.copyOf(unaffordable), List.copyOf(unaffordableWarNames));
            }

            if (!cost.isEmpty()) {
                MoneyValue withdrawn = account.withdrawMoney(cost);
                dev.malik.lcftbhook.bank.BankTransactionLog.logWithdraw(account, "Upkeep Payment", withdrawn);
                dev.malik.lcftbhook.bank.ServerAccountDeposit.deposit(withdrawn, WarService.displayName(team) + " (upkeep)");
            }
            savedData.setPendingState(teamId, pendingState);
            savedData.setProtectionLocked(teamId, false);
            syncState(server, team);
            return new SettlementResult(true, cost, pendingState, forceLoadCount(team), List.copyOf(suspended), List.copyOf(suspendedWarNames), List.copyOf(restored), List.copyOf(restoredWarNames), List.copyOf(unaffordable), List.copyOf(unaffordableWarNames));
        } finally {
            ProtectionService.setApplying(false);
        }
    }

    private static TeamPendingState applyUserWarEnds(
            MinecraftServer server,
            Team team,
            FtbHookSavedData savedData,
            TeamPendingState pendingState
    ) {
        UUID teamId = team.getTeamId();
        Set<UUID> partners = new HashSet<>();
        TeamPendingState updated = pendingState;
        for (UUID targetId : new HashSet<>(pendingState.pendingWarEnds())) {
            if (savedData.setWarTarget(teamId, targetId, false)) {
                partners.add(targetId);
            }
            updated = updated.withoutPendingWarEnd(targetId);
        }
        refreshWarPartners(server, team, teamId, partners);
        return updated;
    }

    private static TeamPendingState restorePendingProtections(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            IBankAccount account,
            List<DismantleStep> restoredOut,
            List<DismantleStep> unaffordableOut
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        TeamPendingState updated = pendingState;

        for (DismantleStep step : ProtectionDismantleOrder.restoreOrder(server, teamId)) {
            if (!ProtectionRollbackService.hasPendingApply(savedData, teamId, step.regionId(), step.property(), updated)) {
                continue;
            }
            if (!WarService.canAffordUpkeepWithPendingProperty(server, team, updated, account, step.regionId(), step.property())) {
                unaffordableOut.add(step);
                continue;
            }
            updated = ProtectionRollbackService.restoreProtection(server, team, step.regionId(), step.property(), updated);
            restoredOut.add(step);
        }

        if (WarService.canAffordUpkeep(server, team, updated, account)) {
            PendingChangeService.applyPendingForceLoadsOnly(server, team, updated);
            updated = clearForceLoadPending(updated);
        }

        return updated;
    }

    private static TeamPendingState restorePendingWars(
            MinecraftServer server,
            Team team,
            FtbHookSavedData savedData,
            TeamPendingState pendingState,
            IBankAccount account,
            List<String> restoredWarNamesOut,
            List<String> unaffordableWarNamesOut
    ) {
        UUID teamId = team.getTeamId();
        List<UUID> restoreOrder = WarService.pendingWarRestoreOrder(server, team, pendingState, savedData);
        TeamPendingState updated = pendingState;
        Set<UUID> partners = new HashSet<>();

        for (UUID targetId : restoreOrder) {
            if (!WarService.canAffordUpkeepWithOutgoingWar(server, team, updated, account, savedData, targetId)) {
                break;
            }
            savedData.setWarTarget(teamId, targetId, true);
            updated = updated.withoutPendingWarDeclare(targetId);
            partners.add(targetId);
            Team target = FtbTeamCatalog.resolve(server, targetId);
            restoredWarNamesOut.add(target != null ? WarService.displayName(target) : targetId.toString());
        }

        if (!partners.isEmpty()) {
            refreshWarPartners(server, team, teamId, partners);
        }
        // Whatever's still pending-declare after the loop above is exactly
        // what couldn't be afforded this pass - restorePendingProtections has
        // an equivalent unaffordableOut bucket that already gets reported to
        // the team; pending war declares had no equivalent until now, so a
        // war stuck waiting on funds silently never showed up anywhere.
        for (UUID targetId : updated.pendingWarDeclares()) {
            Team target = FtbTeamCatalog.resolve(server, targetId);
            unaffordableWarNamesOut.add(target != null ? WarService.displayName(target) : targetId.toString());
        }
        return updated;
    }

    private static TeamPendingState dismantleOutgoingWarsUntilAffordable(
            MinecraftServer server,
            Team team,
            FtbHookSavedData savedData,
            TeamPendingState pendingState,
            IBankAccount account,
            List<String> suspendedWarNamesOut
    ) {
        UUID teamId = team.getTeamId();
        TeamPendingState updated = pendingState;
        List<UUID> dismantleOrder = WarService.outgoingWarDismantleOrder(server, team, savedData);
        Set<UUID> partners = new HashSet<>();

        for (UUID targetId : dismantleOrder) {
            if (WarService.canAffordUpkeep(server, team, updated, account)) {
                break;
            }
            if (updated.isPendingWarEnd(targetId) || !savedData.isAtWarWith(teamId, targetId)) {
                continue;
            }
            savedData.setWarTarget(teamId, targetId, false);
            updated = updated.withPendingWarDeclare(targetId);
            partners.add(targetId);
            Team target = FtbTeamCatalog.resolve(server, targetId);
            suspendedWarNamesOut.add(target != null ? WarService.displayName(target) : targetId.toString());
        }

        if (!partners.isEmpty()) {
            refreshWarPartners(server, team, teamId, partners);
        }
        return updated;
    }

    /**
     * Drops force-loaded chunks one at a time (stopping as soon as
     * affordable), same shape as {@link #dismantleOutgoingWarsUntilAffordable}
     * and {@link #dismantleProtectionsUntilAffordable} - not a hard drop,
     * see {@link PendingChangeService#convertOneForceLoadToPending}.
     */
    private static TeamPendingState dismantleForceLoadsUntilAffordable(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            IBankAccount account
    ) {
        TeamPendingState updated = pendingState;
        for (String chunkKey : PendingChangeService.forceLoadDismantleOrder(team)) {
            if (WarService.canAffordUpkeep(server, team, updated, account)) {
                break;
            }
            updated = PendingChangeService.convertOneForceLoadToPending(server, team, chunkKey, updated);
        }
        return updated;
    }

    private static TeamPendingState dismantleProtectionsUntilAffordable(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            IBankAccount account,
            List<DismantleStep> suspendedOut
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        TeamPendingState updated = pendingState;

        for (DismantleStep step : ProtectionDismantleOrder.fullDismantleOrder(server, teamId)) {
            if (!ProtectionRollbackService.isLiveProtectionBillable(savedData, teamId, step.regionId(), step.property())) {
                continue;
            }
            long reducedCost = WarService.calculateProtectionAndIncomingUpkeepCopper(server, team, updated);
            if (reducedCost <= 0L || account.getMoneyStorage().containsValue(MoneyUtil.fromCopper(reducedCost))) {
                break;
            }
            updated = ProtectionRollbackService.suspendProtection(server, team, step.regionId(), step.property(), updated);
            suspendedOut.add(step);
        }

        return updated;
    }

    private static TeamPendingState clearForceLoadPending(TeamPendingState pendingState) {
        TeamPendingState updated = pendingState;
        for (String key : pendingState.pendingForceLoads()) {
            updated = updated.withoutPendingForceLoad(key);
        }
        for (String key : pendingState.pendingForceUnloads()) {
            updated = updated.withoutPendingForceUnload(key);
        }
        return updated;
    }

    private static int forceLoadCount(Team team) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        return chunkData.getForceLoadedChunks().size();
    }

    private static void refreshWarPartners(MinecraftServer server, Team team, UUID teamId, Set<UUID> partners) {
        if (partners.isEmpty()) {
            return;
        }
        WarStateSync.syncToTeam(server, teamId);
        WarStateSync.onUpkeepFactorsChanged(server, team);
        for (UUID partnerId : partners) {
            WarStateSync.syncToTeam(server, partnerId);
            Team partner = FtbTeamCatalog.resolve(server, partnerId);
            if (partner != null) {
                WarStateSync.onUpkeepFactorsChanged(server, partner);
            }
        }
    }

    private static void syncState(MinecraftServer server, Team team) {
        PendingStateSync.syncTeam(server, team);
        RegionService.syncRegionsToTeam(server, team);
        WarStateSync.onUpkeepFactorsChanged(server, team);
    }
}
