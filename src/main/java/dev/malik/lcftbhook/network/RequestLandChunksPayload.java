package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.LandChunkService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestLandChunksPayload() implements CustomPacketPayload {
    public static final Type<RequestLandChunksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "request_land_chunks"));
    public static final StreamCodec<FriendlyByteBuf, RequestLandChunksPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestLandChunksPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestLandChunksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                LandChunkService.syncToPlayer(player);
            }
        });
    }
}
