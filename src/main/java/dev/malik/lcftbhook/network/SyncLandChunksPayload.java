package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientLandChunks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

public record SyncLandChunksPayload(Set<String> landChunkKeys) implements CustomPacketPayload {
    public static final Type<SyncLandChunksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_land_chunks"));
    public static final StreamCodec<FriendlyByteBuf, SyncLandChunksPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeCollection(payload.landChunkKeys, FriendlyByteBuf::writeUtf),
            buffer -> new SyncLandChunksPayload(buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncLandChunksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientLandChunks.update(payload.landChunkKeys));
    }
}
