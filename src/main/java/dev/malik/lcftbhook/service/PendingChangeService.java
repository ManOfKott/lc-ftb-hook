package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.teams.FtbTeamCatalog;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.service.WarStateSync;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PendingChangeService {
    private PendingChangeService() {
    }

    public static void applyPendingChanges(MinecraftServer server, Team team) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());
        if (pendingState.isEmpty()) {
            return;
        }

        LCFtbHook.LOGGER.info("[PendingDebug] Team {}: applying pending changes: properties={}, forceLoads={}, forceUnloads={}, warDeclares={}, warEnds={}",
                team.getShortName(),
                pendingState.pendingProperties(),
                pendingState.pendingForceLoads(),
                pendingState.pendingForceUnloads(),
                pendingState.pendingWarDeclares(),
                pendingState.pendingWarEnds());

        ProtectionService.setApplying(true);
        try {
            applyPendingProperties(server, team, pendingState);
            applyPendingForceLoads(server, team, pendingState);
            applyPendingWars(server, team, pendingState);
            savedData.setPendingState(team.getTeamId(), pendingState.cleared());
            PendingStateSync.syncTeam(server, team);
            WarStateSync.onUpkeepFactorsChanged(server, team);
        } finally {
            ProtectionService.setApplying(false);
        }
    }

    public static void clearPendingChanges(MinecraftServer server, Team team) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        if (!savedData.getPendingState(team.getTeamId()).isEmpty()) {
            savedData.setPendingState(team.getTeamId(), new TeamPendingState());
            PendingStateSync.syncTeam(server, team);
        }
    }

    public static void removeAllForceLoads(MinecraftServer server, Team team) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        CommandSourceStack source = server.createCommandSourceStack();
        List<ClaimedChunk> loaded = new ArrayList<>(chunkData.getForceLoadedChunks());
        for (ClaimedChunk chunk : loaded) {
            chunkData.unForceLoad(source, chunk.getPos(), false);
        }
    }

    private static void applyPendingProperties(MinecraftServer server, Team team, TeamPendingState pendingState) {
        for (var entry : pendingState.pendingProperties().entrySet()) {
            TeamProperty<?> property = findProperty(entry.getKey());
            if (property == null) {
                LCFtbHook.LOGGER.warn("[PendingDebug] Team {}: unknown pending property key '{}', skipping",
                        team.getShortName(), entry.getKey());
                continue;
            }
            applyPropertyValue(server, team, property, entry.getValue());
        }
    }

    public static void applyPendingForceLoadsOnly(MinecraftServer server, Team team, TeamPendingState pendingState) {
        applyPendingForceLoads(server, team, pendingState);
    }

    private static void applyPendingForceLoads(MinecraftServer server, Team team, TeamPendingState pendingState) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        CommandSourceStack source = server.createCommandSourceStack();

        for (String chunkKey : pendingState.pendingForceUnloads()) {
            chunkData.unForceLoad(source, ChunkPosKey.toChunkDimPos(chunkKey), false);
        }
        for (String chunkKey : pendingState.pendingForceLoads()) {
            chunkData.forceLoad(source, ChunkPosKey.toChunkDimPos(chunkKey), false);
        }
    }

    private static void applyPendingWars(MinecraftServer server, Team team, TeamPendingState pendingState) {
        if (pendingState.pendingWarDeclares().isEmpty() && pendingState.pendingWarEnds().isEmpty()) {
            return;
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        Set<UUID> partners = new HashSet<>();

        for (UUID targetId : pendingState.pendingWarDeclares()) {
            if (savedData.setWarTarget(teamId, targetId, true)) {
                partners.add(targetId);
                LCFtbHook.LOGGER.info("[PendingDebug] Team {}: applied pending war declare on {}", team.getShortName(), targetId);
            }
        }
        for (UUID targetId : pendingState.pendingWarEnds()) {
            if (savedData.setWarTarget(teamId, targetId, false)) {
                partners.add(targetId);
                LCFtbHook.LOGGER.info("[PendingDebug] Team {}: applied pending war end with {}", team.getShortName(), targetId);
            }
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

    private static <T> void applyPropertyValue(MinecraftServer server, Team team, TeamProperty<T> property, String serialized) {
        T value = ProtectionPricing.deserializePropertyValue(property, serialized, team.getProperty(property));
        team.setProperty(property, value);
        // Protection properties are not flagged shouldSyncToAll, so
        // syncOnePropertyToAll would be a no-op. syncOnePropertyToTeam pushes
        // the new value unconditionally to all online team members, which is
        // exactly who needs to see the applied change in their properties menu.
        team.syncOnePropertyToTeam(property, value);
        LCFtbHook.LOGGER.info("[PendingDebug] Team {}: applied pending {} = {} (synced to team)",
                team.getShortName(), ProtectionPricing.propertyKey(property), value);
    }

    private static TeamProperty<?> findProperty(String propertyKey) {
        for (TeamProperty<?> property : ProtectionPricing.PROTECTION_PROPERTIES) {
            if (ProtectionPricing.propertyKey(property).equals(propertyKey)) {
                return property;
            }
        }
        return null;
    }
}
