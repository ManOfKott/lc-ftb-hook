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
import dev.malik.lcftbhook.network.WarEntryStatus;
import dev.malik.lcftbhook.teams.FtbTeamCatalog;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WarService {
    public record WarCostBreakdown(
            long baseUpkeepCopper,
            long incomingWarCopper,
            long outgoingWarCopper,
            int incomingWarCount,
            int outgoingWarCount
    ) {
        public long totalWarCopper() {
            return incomingWarCopper + outgoingWarCopper;
        }

        public long totalUpkeepCopper() {
            return baseUpkeepCopper + totalWarCopper();
        }
    }

    public record WarTeamView(
            UUID teamId,
            String displayName,
            long targetBaseUpkeepCopper,
            long warCostCopper,
            WarEntryStatus status,
            boolean opponentPendingDeclareOnViewer,
            boolean blockEditProtected,
            boolean explosionProtected,
            boolean pvpProtected
    ) {
        public WarTeamView(
                UUID teamId,
                String displayName,
                long targetBaseUpkeepCopper,
                long warCostCopper
        ) {
            this(
                    teamId,
                    displayName,
                    targetBaseUpkeepCopper,
                    warCostCopper,
                    WarEntryStatus.ACTIVE,
                    false,
                    true,
                    true,
                    true
            );
        }

        public boolean hasWarVulnerability() {
            return !blockEditProtected || !explosionProtected || !pvpProtected;
        }
    }

    private WarService() {
    }

    public static boolean isEnabled() {
        return LCFtbHookConfig.SERVER.warEnabled.get();
    }

    public static boolean isClaimTeam(MinecraftServer server, Team team) {
        if (!team.isValid() || !FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }
        if (team.isPartyTeam() && !FtbTeamCatalog.isTracked(server, team)) {
            return false;
        }
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        return chunkData.getClaimedChunks().size() > 0;
    }

    public static long baseUpkeepCopper(MinecraftServer server, Team team) {
        return baseUpkeepCopper(server, team, FtbHookSavedData.get(server).getPendingState(team.getTeamId()));
    }

    public static long baseUpkeepCopper(MinecraftServer server, Team team, TeamPendingState pendingState) {
        return ProtectionPricing.calculateTotalUpkeepCopper(server, team, pendingState);
    }

    public static WarCostBreakdown calculateWarCosts(MinecraftServer server, Team team) {
        return calculateWarCosts(server, team, FtbHookSavedData.get(server).getPendingState(team.getTeamId()));
    }

    public static WarCostBreakdown calculateWarCosts(MinecraftServer server, Team team, TeamPendingState pendingState) {
        return calculateWarCosts(server, team, pendingState, ProtectionRollbackService.pricingProperties(team, pendingState));
    }

    public static WarCostBreakdown calculateWarCosts(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            java.util.Map<String, String> pricingOverrides
    ) {
        long base = ProtectionPricing.calculateTotalUpkeepCopper(server, team, pendingState, pricingOverrides);
        if (!isEnabled()) {
            return new WarCostBreakdown(base, 0L, 0L, 0, 0);
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);

        // Only count declarers that still exist and have claimed chunks — the same
        // filter used by buildIncomingViews. Stale references from deleted or
        // claim-less teams would otherwise inflate the billing without showing up
        // in the war screen.
        int incomingCount = countEligibleIncomingWars(server, savedData, team.getTeamId());
        long incoming = WarUpkeepMath.sumOrdinalIncomingTerms(base, incomingCount);

        Set<UUID> targets = savedData.getWarTargets(team.getTeamId());
        long outgoing = 0L;
        for (UUID targetId : targets) {
            Team target = FtbTeamCatalog.resolve(server, targetId);
            if (target != null && isClaimTeam(server, target)) {
                long targetBase = baseUpkeepCopper(server, target, savedData.getPendingState(targetId));
                outgoing += WarUpkeepMath.outgoingWarCostCopper(targetBase);
            }
        }

        return new WarCostBreakdown(base, incoming, outgoing, incomingCount, targets.size());
    }

    public static MoneyValue calculateTotalUpkeepCost(MinecraftServer server, Team team, TeamPendingState pendingState) {
        return MoneyUtil.fromCopper(calculateTotalUpkeepCostCopper(server, team, pendingState));
    }

    public static long calculateTotalUpkeepCostCopper(MinecraftServer server, Team team, TeamPendingState pendingState) {
        return calculateWarCosts(server, team, pendingState).totalUpkeepCopper();
    }

    public static long calculateTotalUpkeepCostCopper(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            java.util.Map<String, String> pricingOverrides
    ) {
        return calculateWarCosts(server, team, pendingState, pricingOverrides).totalUpkeepCopper();
    }

    public static boolean canAffordUpkeepWithPendingProperty(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            IBankAccount account,
            dev.ftb.mods.ftbteams.api.property.TeamProperty<?> property
    ) {
        java.util.Map<String, String> pricing = ProtectionRollbackService.pricingWithAppliedPending(team, pendingState, property);
        long cost = calculateTotalUpkeepCostCopper(server, team, pendingState, pricing);
        if (cost <= 0L) {
            return true;
        }
        return account.getMoneyStorage().containsValue(MoneyUtil.fromCopper(cost));
    }

    public static long calculateProtectionAndIncomingUpkeepCopper(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState
    ) {
        WarCostBreakdown war = calculateWarCosts(server, team, pendingState);
        return war.baseUpkeepCopper() + war.incomingWarCopper();
    }

    public static boolean canAffordUpkeep(MinecraftServer server, Team team, TeamPendingState pendingState, IBankAccount account) {
        long cost = calculateTotalUpkeepCostCopper(server, team, pendingState);
        if (cost <= 0L) {
            return true;
        }
        return account.getMoneyStorage().containsValue(MoneyUtil.fromCopper(cost));
    }

    public static boolean canAffordUpkeepWithOutgoingWar(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            IBankAccount account,
            FtbHookSavedData savedData,
            UUID targetId
    ) {
        UUID teamId = team.getTeamId();
        if (savedData.isAtWarWith(teamId, targetId)) {
            return canAffordUpkeep(server, team, pendingState, account);
        }
        savedData.setWarTarget(teamId, targetId, true);
        boolean affordable = canAffordUpkeep(server, team, pendingState, account);
        savedData.setWarTarget(teamId, targetId, false);
        return affordable;
    }

    public static List<UUID> pendingWarRestoreOrder(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            FtbHookSavedData savedData
    ) {
        UUID teamId = team.getTeamId();
        List<UUID> targets = new ArrayList<>();
        for (UUID targetId : pendingState.pendingWarDeclares()) {
            if (!savedData.isAtWarWith(teamId, targetId)) {
                targets.add(targetId);
            }
        }
        targets.sort(Comparator.comparingLong(id -> {
            Team target = FtbTeamCatalog.resolve(server, id);
            return target == null ? Long.MAX_VALUE : costToDeclareWar(server, team, target);
        }));
        return targets;
    }

    public static List<UUID> outgoingWarDismantleOrder(MinecraftServer server, Team team, FtbHookSavedData savedData) {
        UUID teamId = team.getTeamId();
        List<UUID> targets = new ArrayList<>(savedData.getWarTargets(teamId));
        targets.sort(Comparator.comparing(UUID::toString));

        java.util.Map<UUID, Long> costByTarget = new java.util.HashMap<>();
        for (UUID targetId : targets) {
            Team target = FtbTeamCatalog.resolve(server, targetId);
            long cost = target == null
                    ? 0L
                    : WarUpkeepMath.outgoingWarCostCopper(baseUpkeepCopper(server, target));
            costByTarget.put(targetId, cost);
        }

        targets.sort(Comparator.comparingLong(id -> costByTarget.getOrDefault(id, 0L)).reversed());
        return targets;
    }

    public static long costToDeclareWar(MinecraftServer server, Team declarer, Team target) {
        if (!isEnabled()) {
            return 0L;
        }
        return WarUpkeepMath.outgoingWarCostCopper(baseUpkeepCopper(server, target));
    }

    public static boolean isWarEligibleTeam(MinecraftServer server, Team team) {
        return FtbTeamCatalog.isTracked(server, team);
    }

    public static List<WarTeamView> buildIncomingViews(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID selfId = self.getTeamId();
        long selfBase = baseUpkeepCopper(server, self);
        List<WarTeamView> views = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (!entry.warTargets().contains(selfId)) {
                continue;
            }
            Team declarer = FtbTeamCatalog.resolve(server, entry.ftbTeamId());
            if (declarer == null || !isWarEligibleTeam(server, declarer)) {
                continue;
            }
            seen.add(declarer.getTeamId());
            views.add(opponentView(
                    declarer,
                    baseUpkeepCopper(server, declarer),
                    selfBase,
                    WarEntryStatus.ACTIVE,
                    false
            ));
        }

        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            UUID declarerId = entry.ftbTeamId();
            if (declarerId.equals(selfId) || seen.contains(declarerId)) {
                continue;
            }
            if (!entry.pendingState().isPendingWarDeclare(selfId)) {
                continue;
            }
            Team declarer = FtbTeamCatalog.resolve(server, declarerId);
            if (declarer == null || !isWarEligibleTeam(server, declarer)) {
                continue;
            }
            seen.add(declarerId);
            views.add(opponentView(
                    declarer,
                    baseUpkeepCopper(server, declarer),
                    selfBase,
                    WarEntryStatus.PENDING_DECLARE,
                    false
            ));
        }

        views.sort(Comparator.comparing(WarTeamView::displayName, String.CASE_INSENSITIVE_ORDER));
        double exponent = warMultiplier();
        for (int i = 0; i < views.size(); i++) {
            WarTeamView view = views.get(i);
            long term = WarUpkeepMath.ordinalWarTermCopper(selfBase, i, exponent);
            views.set(i, new WarTeamView(
                    view.teamId(),
                    view.displayName(),
                    view.targetBaseUpkeepCopper(),
                    term,
                    view.status(),
                    view.opponentPendingDeclareOnViewer(),
                    view.blockEditProtected(),
                    view.explosionProtected(),
                    view.pvpProtected()
            ));
        }
        return views;
    }

    /** Active incoming wars only — used for upkeep billing breakdown lines. */
    public static List<WarTeamView> buildBilledIncomingViews(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID selfId = self.getTeamId();
        long selfBase = baseUpkeepCopper(server, self);
        double exponent = warMultiplier();

        List<UUID> declarerIds = new ArrayList<>();
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (!entry.warTargets().contains(selfId)) {
                continue;
            }
            Team declarer = FtbTeamCatalog.resolve(server, entry.ftbTeamId());
            if (declarer == null || !isWarEligibleTeam(server, declarer)) {
                continue;
            }
            declarerIds.add(declarer.getTeamId());
        }
        declarerIds.sort(Comparator.comparing(UUID::toString));

        List<WarTeamView> views = new ArrayList<>();
        int index = 0;
        for (UUID declarerId : declarerIds) {
            Team declarer = FtbTeamCatalog.resolve(server, declarerId);
            if (declarer == null) {
                continue;
            }
            views.add(opponentView(
                    declarer,
                    baseUpkeepCopper(server, declarer),
                    WarUpkeepMath.ordinalWarTermCopper(selfBase, index++, exponent),
                    WarEntryStatus.ACTIVE,
                    false
            ));
        }
        views.sort(Comparator.comparing(WarTeamView::displayName, String.CASE_INSENSITIVE_ORDER));
        return views;
    }

    /** Active outgoing wars only — used for upkeep billing breakdown lines. */
    public static List<WarTeamView> buildBilledOutgoingViews(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID selfId = self.getTeamId();
        TeamPendingState pendingState = savedData.getPendingState(selfId);

        List<UUID> activeTargetIds = new ArrayList<>(savedData.getWarTargets(selfId));
        activeTargetIds.sort(Comparator.comparing(UUID::toString));

        List<WarTeamView> views = new ArrayList<>();
        int index = 0;
        for (UUID targetId : activeTargetIds) {
            Team target = FtbTeamCatalog.resolve(server, targetId);
            if (target == null || !isWarEligibleTeam(server, target)) {
                continue;
            }
            WarEntryStatus status = pendingState.isPendingWarEnd(targetId)
                    ? WarEntryStatus.PENDING_END
                    : WarEntryStatus.ACTIVE;
            long targetBase = baseUpkeepCopper(server, target);
            views.add(opponentView(
                    target,
                    targetBase,
                    WarUpkeepMath.outgoingWarCostCopper(targetBase),
                    status,
                    false
            ));
        }
        views.sort(Comparator.comparing(WarTeamView::displayName, String.CASE_INSENSITIVE_ORDER));
        return views;
    }

    public static List<WarTeamView> buildOutgoingViews(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID selfId = self.getTeamId();
        TeamPendingState pendingState = savedData.getPendingState(selfId);
        List<WarTeamView> views = new ArrayList<>();
        List<UUID> activeTargetIds = new ArrayList<>(savedData.getWarTargets(selfId));
        activeTargetIds.sort(Comparator.comparing(UUID::toString));
        int index = 0;
        for (UUID targetId : activeTargetIds) {
            Team target = FtbTeamCatalog.resolve(server, targetId);
            if (target == null || !isWarEligibleTeam(server, target)) {
                continue;
            }
            WarEntryStatus status = pendingState.isPendingWarEnd(targetId)
                    ? WarEntryStatus.PENDING_END
                    : WarEntryStatus.ACTIVE;
            long targetBase = baseUpkeepCopper(server, target);
            views.add(opponentView(
                    target,
                    targetBase,
                    WarUpkeepMath.outgoingWarCostCopper(targetBase),
                    status,
                    false
            ));
        }
        for (UUID targetId : pendingState.pendingWarDeclares()) {
            if (savedData.isAtWarWith(selfId, targetId)) {
                continue;
            }
            Team target = FtbTeamCatalog.resolve(server, targetId);
            if (target == null || !isWarEligibleTeam(server, target)) {
                continue;
            }
            views.add(opponentView(
                    target,
                    baseUpkeepCopper(server, target),
                    costToDeclareWar(server, self, target),
                    WarEntryStatus.PENDING_DECLARE,
                    false
            ));
        }
        views.sort(Comparator.comparing(WarTeamView::displayName, String.CASE_INSENSITIVE_ORDER));
        return views;
    }

    public static List<WarTeamView> buildAvailableTargets(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        List<WarTeamView> views = new ArrayList<>();
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID selfId = self.getTeamId();
        Set<UUID> activeTargets = savedData.getWarTargets(selfId);
        TeamPendingState selfPending = savedData.getPendingState(selfId);
        for (Team team : FtbTeamCatalog.trackedTeams(server)) {
            UUID teamId = team.getTeamId();
            if (teamId.equals(selfId) || activeTargets.contains(teamId) || selfPending.isPendingWarDeclare(teamId)) {
                continue;
            }
            boolean opponentPending = savedData.getPendingState(teamId).isPendingWarDeclare(selfId);
            WarEntryStatus status = selfPending.isPendingWarDeclare(teamId)
                    ? WarEntryStatus.PENDING_DECLARE
                    : WarEntryStatus.ACTIVE;
            views.add(opponentView(
                    team,
                    baseUpkeepCopper(server, team),
                    costToDeclareWar(server, self, team),
                    status,
                    opponentPending
            ));
        }
        views.sort(Comparator.comparing(WarTeamView::displayName, String.CASE_INSENSITIVE_ORDER));
        return views;
    }

    private static int countEligibleIncomingWars(MinecraftServer server, FtbHookSavedData savedData, UUID targetId) {
        int count = 0;
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (!entry.warTargets().contains(targetId)) {
                continue;
            }
            Team declarer = FtbTeamCatalog.resolve(server, entry.ftbTeamId());
            if (declarer == null || !isWarEligibleTeam(server, declarer)) {
                continue;
            }
            count++;
        }
        return count;
    }

    public static boolean canManageWar(Team team, UUID playerId) {
        return BankAccountHelper.canPurchaseForTeam(team, playerId);
    }

    @Nullable
    public static Component toggleWar(MinecraftServer server, ServerPlayer player, UUID targetTeamId) {
        if (!isEnabled()) {
            return Component.translatable("message.lc_ftb_hook.war_disabled");
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Component.translatable("message.lc_ftb_hook.war_unavailable");
        }

        Team self = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        Team target = FtbTeamCatalog.resolve(server, targetTeamId);
        if (self == null || target == null) {
            return Component.translatable("message.lc_ftb_hook.war_unavailable");
        }
        if (!canManageWar(self, player.getUUID())) {
            return Component.translatable("message.lc_ftb_hook.war_denied");
        }
        if (self.getTeamId().equals(target.getTeamId())) {
            return Component.translatable("message.lc_ftb_hook.war_self");
        }
        if (!isWarEligibleTeam(server, self) || !isWarEligibleTeam(server, target)) {
            return Component.translatable("message.lc_ftb_hook.war_unavailable");
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID selfId = self.getTeamId();
        UUID targetId = target.getTeamId();
        TeamPendingState pendingState = savedData.getPendingState(selfId);

        if (pendingState.isPendingWarDeclare(targetId)) {
            savedData.setPendingState(selfId, pendingState.withoutPendingWarDeclare(targetId));
            return Component.translatable("message.lc_ftb_hook.war_pending_cancelled", displayName(target));
        }

        boolean currentlyAtWar = savedData.isAtWarWith(selfId, targetId);
        if (currentlyAtWar) {
            if (pendingState.isPendingWarEnd(targetId)) {
                savedData.setPendingState(selfId, pendingState.withoutPendingWarEnd(targetId));
                return Component.translatable("message.lc_ftb_hook.war_pending_cancelled", displayName(target));
            }
            savedData.setPendingState(selfId, pendingState.withPendingWarEnd(targetId));
            return Component.translatable("message.lc_ftb_hook.war_end_pending", displayName(target));
        }

        savedData.setPendingState(selfId, pendingState.withPendingWarDeclare(targetId));
        return Component.translatable("message.lc_ftb_hook.war_declare_pending", displayName(target));
    }

    public static void onTeamRemoved(MinecraftServer server, UUID teamId) {
        FtbTeamCatalog.onTeamDeleted(server, teamId);
    }

    /**
     * Removes all wars declared by or against the given team and refreshes
     * war/upkeep state for every team that was involved.
     */
    public static void cleanupTeamWars(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Set<UUID> partners = savedData.collectWarPartnerIds(teamId);
        boolean hadOutgoing = !savedData.getWarTargets(teamId).isEmpty();
        if (partners.isEmpty() && !hadOutgoing) {
            return;
        }

        savedData.clearWarReferences(teamId);
        for (UUID partnerId : partners) {
            WarStateSync.syncToTeam(server, partnerId);
            Team partner = FtbTeamCatalog.resolve(server, partnerId);
            if (partner != null) {
                WarStateSync.onUpkeepFactorsChanged(server, partner);
            }
        }

        LCFtbHook.LOGGER.debug("Cleared war state for team {} and refreshed {} partner team(s)", teamId, partners.size());
    }

    public static double warMultiplier() {
        return LCFtbHookConfig.SERVER.warCostMultiplier.get();
    }

    private static WarTeamView opponentView(
            Team opponent,
            long targetBaseUpkeepCopper,
            long warCostCopper,
            WarEntryStatus status,
            boolean opponentPendingDeclareOnViewer
    ) {
        WarTargetProtections protections = WarTargetProtections.live(opponent);
        return new WarTeamView(
                opponent.getTeamId(),
                displayName(opponent),
                targetBaseUpkeepCopper,
                warCostCopper,
                status,
                opponentPendingDeclareOnViewer,
                protections.blockEditProtected(),
                protections.explosionProtected(),
                protections.pvpProtected()
        );
    }

    public static String displayName(Team team) {
        return team.getName().getString();
    }
}
