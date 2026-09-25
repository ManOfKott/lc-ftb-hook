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

    /**
     * Currently force-loaded chunks, in a stable order - the same one used
     * both to dismantle them one at a time during upkeep settlement (see
     * {@code UpkeepSettlementService.dismantleForceLoadsUntilAffordable})
     * and to list them in {@code /upkeep_priority}'s combined priority list,
     * so the displayed order always matches what actually gets dropped
     * first. No per-chunk priority concept exists beyond this (unlike
     * protection properties' configurable dismantle order) - sorted purely
     * for determinism.
     */
    public static List<String> forceLoadDismantleOrder(Team team) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        List<String> keys = new ArrayList<>();
        for (ClaimedChunk chunk : chunkData.getForceLoadedChunks()) {
            keys.add(ChunkPosKey.encode(chunk.getPos()));
        }
        java.util.Collections.sort(keys);
        return keys;
    }

    /**
     * Same real-world effect as un-force-loading a chunk directly (FTB
     * Chunks actually stops force-loading it - upkeep can't afford it right
     * now), but keeps the player's INTENT: it's queued in
     * {@code pendingForceLoads} exactly like a brand-new request that
     * couldn't apply immediately would be, so it silently resumes the
     * moment upkeep can afford it again (see {@code UpkeepSettlementService
     * .restorePendingProtections} -> {@link #applyPendingForceLoadsOnly}),
     * instead of being lost outright and requiring the player to notice and
     * re-request it. A chunk the player had ALREADY separately queued to
     * unload is left alone - they don't want it back, so there's nothing to
     * preserve intent for.
     */
    public static TeamPendingState convertOneForceLoadToPending(MinecraftServer server, Team team, String chunkKey, TeamPendingState pendingState) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        CommandSourceStack source = server.createCommandSourceStack();
        chunkData.unForceLoad(source, ChunkPosKey.toChunkDimPos(chunkKey), false);
        return pendingState.isPendingForceUnload(chunkKey) ? pendingState : pendingState.withPendingForceLoad(chunkKey);
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
