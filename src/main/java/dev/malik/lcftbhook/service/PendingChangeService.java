package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.TeamPendingState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public final class PendingChangeService {
    private PendingChangeService() {
    }

    public static void removeAllForceLoads(MinecraftServer server, Team team) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        CommandSourceStack source = server.createCommandSourceStack();
        List<ClaimedChunk> loaded = new ArrayList<>(chunkData.getForceLoadedChunks());
        for (ClaimedChunk chunk : loaded) {
            chunkData.unForceLoad(source, chunk.getPos(), false);
        }
    }

    public static void applyPendingForceLoadsOnly(MinecraftServer server, Team team, TeamPendingState pendingState) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        CommandSourceStack source = server.createCommandSourceStack();

        for (String chunkKey : pendingState.pendingForceUnloads()) {
            chunkData.unForceLoad(source, ChunkPosKey.toChunkDimPos(chunkKey), false);
        }
        for (String chunkKey : pendingState.pendingForceLoads()) {
            chunkData.forceLoad(source, ChunkPosKey.toChunkDimPos(chunkKey), false);
        }
    }
}
