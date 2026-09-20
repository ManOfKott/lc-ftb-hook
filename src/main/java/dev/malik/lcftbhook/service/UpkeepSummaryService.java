package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import net.minecraft.server.MinecraftServer;

/**
 * Team-wide upkeep totals for the Region list screen's header: protection
 * (summed across every region), force-load, and war (incoming + outgoing)
 * upkeep, each as a current/pending pair. "Current" always uses an empty
 * {@link TeamPendingState} (the true live baseline, unaffected by anything
 * queued), "pending" uses the team's real queued state - same current-vs-
 * projected convention as the per-region upkeep display.
 */
public final class UpkeepSummaryService {
    private UpkeepSummaryService() {
    }

    public record UpkeepSummary(
            long protectionCurrentCopper,
            long protectionPendingCopper,
            long forceLoadCurrentCopper,
            long forceLoadPendingCopper,
            int forceLoadCurrentCount,
            int forceLoadPendingCount,
            long warIncomingCurrentCopper,
            long warIncomingPendingCopper,
            long warOutgoingCurrentCopper,
            long warOutgoingPendingCopper,
            int warIncomingCount,
            int warOutgoingCount
    ) {
        public long totalCurrentCopper() {
            return protectionCurrentCopper + forceLoadCurrentCopper + warIncomingCurrentCopper + warOutgoingCurrentCopper;
        }

        public long totalPendingCopper() {
            return protectionPendingCopper + forceLoadPendingCopper + warIncomingPendingCopper + warOutgoingPendingCopper;
        }
    }

    public static UpkeepSummary compute(MinecraftServer server, Team team) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());
        TeamPendingState empty = new TeamPendingState();

        ChunkTeamData chunkData = FTBChunksAPI.api().isManagerLoaded()
                ? FTBChunksAPI.api().getManager().getOrCreateData(team)
                : null;

        int forceLoadCurrentCount = chunkData != null ? ProtectionPricing.countEffectiveForceLoads(chunkData, empty) : 0;
        int forceLoadPendingCount = chunkData != null ? ProtectionPricing.countEffectiveForceLoads(chunkData, pendingState) : 0;
        long forceLoadCurrent = UpkeepGating.shouldChargeForceLoad(server, team)
                ? ProtectionPricing.calculateForceLoadCopper(forceLoadCurrentCount) : 0L;
        long forceLoadPending = UpkeepGating.shouldChargeForceLoad(server, team)
                ? ProtectionPricing.calculateForceLoadCopper(forceLoadPendingCount) : 0L;

        // baseUpkeepCopper is protection + force-load combined - subtracting
        // the independently-computed force-load piece (above) isolates
        // protection alone, using calculateWarCosts as the single source of
        // truth for the combined figure rather than re-deriving it a second,
        // potentially inconsistent way.
        WarService.WarCostBreakdown warCurrent = WarService.calculateWarCosts(server, team, empty);
        WarService.WarCostBreakdown warPending = WarService.calculateWarCosts(server, team, pendingState);
        long protectionCurrent = warCurrent.baseUpkeepCopper() - forceLoadCurrent;
        long protectionPending = warPending.baseUpkeepCopper() - forceLoadPending;

        return new UpkeepSummary(
                protectionCurrent, protectionPending,
                forceLoadCurrent, forceLoadPending,
                forceLoadCurrentCount, forceLoadPendingCount,
                warCurrent.incomingWarCopper(), warPending.incomingWarCopper(),
                warCurrent.outgoingWarCopper(), warPending.outgoingWarCopper(),
                warCurrent.incomingWarCount(), warCurrent.outgoingWarCount()
        );
    }
}
