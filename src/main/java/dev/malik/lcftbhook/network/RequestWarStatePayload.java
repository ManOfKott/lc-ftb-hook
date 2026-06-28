package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.WarStateSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestWarStatePayload() implements CustomPacketPayload {
    public static final Type<RequestWarStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "request_war_state"));
    public static final StreamCodec<FriendlyByteBuf, RequestWarStatePayload> STREAM_CODEC = StreamCodec.unit(new RequestWarStatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestWarStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarStateSync.syncToPlayer(player);
            }
        });
    }
}
