package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.teams.FtbTeamCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

        for (TeamProperty<?> property : ProtectionDismantleOrder.restoreOrder()) {
            if (!ProtectionRollbackService.isLiveProtectionBillable(team, property)) {
                continue;
            }
            String key = ProtectionPricing.propertyKey(property);
            entries.add(new PriorityEntry(
                    priority++,
                    EntryKind.PROTECTION,
                    key,
                    protectionLabel(key),
                    protectionUpkeepCopper(server, team, pendingState, property)
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

    private static Component protectionLabel(String key) {
        return Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + key);
    }

    private static long protectionUpkeepCopper(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            TeamProperty<?> property
    ) {
        ProtectionPricing.ChunkCounts counts = ProtectionPricing.countBillableChunks(server, team);
        Map<String, String> pricing = ProtectionRollbackService.pricingProperties(team, pendingState);
        long withLive = ProtectionPricing.calculateProtectionCopper(team.getProperties(), pricing, counts);

        String key = ProtectionPricing.propertyKey(property);
        Map<String, String> atMinimum = new HashMap<>(pricing);
        atMinimum.put(key, minimumSerialized(property));

        long atMin = ProtectionPricing.calculateProtectionCopper(team.getProperties(), atMinimum, counts);
        return Math.max(0L, withLive - atMin);
    }

    private static String minimumSerialized(TeamProperty<?> property) {
        if (property instanceof dev.ftb.mods.ftbteams.api.property.BooleanProperty) {
            return ProtectionPricing.serializePropertyValue(property, true);
        }
        return ProtectionPricing.serializePropertyValue(
                property,
                dev.ftb.mods.ftbteams.api.property.PrivacyMode.PUBLIC
        );
    }
}
