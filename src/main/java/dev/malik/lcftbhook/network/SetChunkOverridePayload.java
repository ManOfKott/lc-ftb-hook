package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.service.MarketplaceService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetChunkOverridePayload(String chunkKey, String propertyId, String value) implements CustomPacketPayload {
    public static final Type<SetChunkOverridePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "set_chunk_override"));
    public static final StreamCodec<FriendlyByteBuf, SetChunkOverridePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.chunkKey);
                buffer.writeUtf(payload.propertyId);
                buffer.writeUtf(payload.value);
            },
            buffer -> new SetChunkOverridePayload(buffer.readUtf(), buffer.readUtf(), buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetChunkOverridePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ProtectionProperty property;
            try {
                property = ProtectionProperty.byId(payload.propertyId);
            } catch (IllegalArgumentException e) {
                return;
            }
            MarketplaceService.setChunkOverride(player, payload.chunkKey, property, payload.value);
        });
    }
}
