package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public final class PendingChangeService {
    private PendingChangeService() {
    }

    public static void applyPendingChanges(MinecraftServer server, Team team) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());
        if (pendingState.isEmpty()) {
            return;
        }

        LCFtbHook.LOGGER.info("[PendingDebug] Team {}: applying pending changes: properties={}, forceLoads={}, forceUnloads={}",
                team.getShortName(),
                pendingState.pendingProperties(),
                pendingState.pendingForceLoads(),
                pendingState.pendingForceUnloads());

        ProtectionService.setApplying(true);
        try {
            applyPendingProperties(server, team, pendingState);
            applyPendingForceLoads(server, team, pendingState);
            savedData.setPendingState(team.getTeamId(), pendingState.cleared());
            PendingStateSync.syncTeam(server, team);
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

    @SuppressWarnings("unchecked")
    private static <T> void applyPropertyValue(MinecraftServer server, Team team, TeamProperty<T> property, String serialized) {
        T value = ProtectionPricing.deserializePropertyValue(property, serialized, team.getProperty(property));
        team.setProperty(property, value);
        team.syncOnePropertyToAll(server, property, value);
        LCFtbHook.LOGGER.info("[PendingDebug] Team {}: applied pending {} = {} (synced to clients)",
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
