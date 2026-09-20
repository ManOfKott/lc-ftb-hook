package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.RegionPropertyKey;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.service.ProtectionDismantleOrder.DismantleStep;
import dev.malik.lcftbhook.teams.FtbTeamCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the dismantle/restore priority list for a team. Priority 1 is the
 * protection dismantled last (restored first); the highest number is the most
 * expensive active outgoing war (dismantled first).
 */
public final class UpkeepPriorityService {
    public record PriorityEntry(
            int priority,
            EntryKind kind,
            String id,
            Component label,
            long costCopper
    ) {
    }

    public enum EntryKind {
        PROTECTION,
        OUTGOING_WAR
    }

    private UpkeepPriorityService() {
    }

    public static List<PriorityEntry> buildOrder(MinecraftServer server, Team team) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());
        UUID teamId = team.getTeamId();
        List<PriorityEntry> entries = new ArrayList<>();
        int priority = 1;

        for (DismantleStep step : ProtectionDismantleOrder.restoreOrder(server, teamId)) {
            if (!ProtectionRollbackService.isLiveProtectionBillable(savedData, teamId, step.regionId(), step.property())) {
                continue;
            }
            String key = RegionPropertyKey.encode(step.regionId(), step.property().id());
            entries.add(new PriorityEntry(
                    priority++,
                    EntryKind.PROTECTION,
                    key,
                    protectionLabel(savedData, teamId, step),
                    protectionUpkeepCopper(server, team, pendingState, step)
            ));
        }

        List<UUID> outgoing = new ArrayList<>(savedData.getWarTargets(teamId));
        outgoing.sort(Comparator.comparingLong(targetId -> {
            Team target = FtbTeamCatalog.resolve(server, targetId);
            return target == null ? 0L : WarService.costToDeclareWar(server, team, target);
        }));

        for (UUID targetId : outgoing) {
            Team target = FtbTeamCatalog.resolve(server, targetId);
            if (target == null) {
                continue;
            }
            entries.add(new PriorityEntry(
                    priority++,
                    EntryKind.OUTGOING_WAR,
                    targetId.toString(),
                    Component.literal(WarService.displayName(target)),
                    WarService.costToDeclareWar(server, team, target)
            ));
        }

        return entries;
    }

    private static Component protectionLabel(FtbHookSavedData savedData, UUID teamId, DismantleStep step) {
        Region region = savedData.getRegion(teamId, step.regionId());
        String regionName = region != null ? region.name() : Region.DEFAULT_NAME;
        return Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + step.property().id())
                .append(Component.literal(" (" + regionName + ")"));
    }

    private static long protectionUpkeepCopper(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            DismantleStep step
    ) {
        ProtectionPricing.ChunkCounts counts = ProtectionPricing.countBillableChunks(server, team, pendingState);
        Map<String, String> pricing = ProtectionRollbackService.pricingProperties(server, team, pendingState);
        long withLive = ProtectionPricing.calculateProtectionCopper(server, team.getTeamId(),
                new TeamPendingState(pricing, pendingState.pendingForceLoads(), pendingState.pendingForceUnloads(),
                        pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds()),
                counts);

        String key = RegionPropertyKey.encode(step.regionId(), step.property().id());
        java.util.Map<String, String> atMinimum = new java.util.HashMap<>(pricing);
        atMinimum.put(key, step.property().minimumSerialized());

        long atMin = ProtectionPricing.calculateProtectionCopper(server, team.getTeamId(),
                new TeamPendingState(atMinimum, pendingState.pendingForceLoads(), pendingState.pendingForceUnloads(),
                        pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds()),
                counts);
        return Math.max(0L, withLive - atMin);
    }
}
