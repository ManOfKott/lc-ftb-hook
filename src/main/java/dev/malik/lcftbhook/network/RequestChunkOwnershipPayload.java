package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.MarketplaceService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestChunkOwnershipPayload() implements CustomPacketPayload {
    public static final Type<RequestChunkOwnershipPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "request_chunk_ownership"));
    public static final StreamCodec<FriendlyByteBuf, RequestChunkOwnershipPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestChunkOwnershipPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestChunkOwnershipPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarketplaceService.syncOwnershipToPlayer(player);
                MarketplaceService.syncUnsettledChunksToPlayer(player);
            }
        });
    }
}
