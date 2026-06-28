package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.WarService;
import dev.malik.lcftbhook.service.WarStateSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ToggleWarPayload(UUID targetTeamId) implements CustomPacketPayload {
    public static final Type<ToggleWarPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "toggle_war"));
    public static final StreamCodec<FriendlyByteBuf, ToggleWarPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUUID(payload.targetTeamId),
            buffer -> new ToggleWarPayload(buffer.readUUID())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ToggleWarPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var message = WarService.toggleWar(player.server, player, payload.targetTeamId());
            if (message != null) {
                player.displayClientMessage(message, false);
            }
            WarStateSync.syncToPlayer(player);
            WarStateSync.syncToTeam(player.server, payload.targetTeamId());
        });
    }
}
