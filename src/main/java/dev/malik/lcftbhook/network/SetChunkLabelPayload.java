package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.MarketplaceService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetChunkLabelPayload(String chunkKey, String label) implements CustomPacketPayload {
    public static final Type<SetChunkLabelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "set_chunk_label"));
    public static final StreamCodec<FriendlyByteBuf, SetChunkLabelPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.chunkKey);
                buffer.writeUtf(payload.label);
            },
            buffer -> new SetChunkLabelPayload(buffer.readUtf(), buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetChunkLabelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MarketplaceService.setLabel(player, payload.chunkKey(), payload.label());
        });
    }
}
