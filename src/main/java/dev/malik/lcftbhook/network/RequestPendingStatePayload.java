package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestPendingStatePayload() implements CustomPacketPayload {
    public static final Type<RequestPendingStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "request_pending_state"));
    public static final StreamCodec<FriendlyByteBuf, RequestPendingStatePayload> STREAM_CODEC = StreamCodec.unit(new RequestPendingStatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestPendingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                PendingStateSync.syncToPlayer(player);
            }
        });
    }
}
