package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientUnsettledChunks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Bare chunk keys not yet settled by an upkeep tick (freshly claimed, or
 * freshly bought - see {@code FtbHookSavedData#markChunkUnsettled}),
 * broadcast globally like {@link SyncRegionMembershipPayload} because any
 * viewer's hover panel needs to show "not sellable yet" for any team's
 * chunk, not just the viewer's own.
 */
public record SyncUnsettledChunksPayload(Set<String> chunkKeys) implements CustomPacketPayload {
    public static final Type<SyncUnsettledChunksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_unsettled_chunks"));
    public static final StreamCodec<FriendlyByteBuf, SyncUnsettledChunksPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.chunkKeys.size());
                for (String key : payload.chunkKeys) {
                    buffer.writeUtf(key);
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                Set<String> chunkKeys = new HashSet<>(size);
                for (int i = 0; i < size; i++) {
                    chunkKeys.add(buffer.readUtf());
                }
                return new SyncUnsettledChunksPayload(chunkKeys);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncUnsettledChunksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientUnsettledChunks.update(payload.chunkKeys));
    }
}
