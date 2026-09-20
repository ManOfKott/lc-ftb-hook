package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record SetRegionPropertyPayload(UUID regionId, String propertyId, String value) implements CustomPacketPayload {
    public static final Type<SetRegionPropertyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "set_region_property"));
    public static final StreamCodec<FriendlyByteBuf, SetRegionPropertyPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.regionId);
                buffer.writeUtf(payload.propertyId);
                buffer.writeUtf(payload.value);
            },
            buffer -> new SetRegionPropertyPayload(buffer.readUUID(), buffer.readUtf(), buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetRegionPropertyPayload payload, IPayloadContext context) {
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
            RegionService.setRegionProperty(player, payload.regionId, property, payload.value);
        });
    }
}
