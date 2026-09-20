package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientPlayerProfiles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server's answer to {@link RequestPlayerProfilePayload} - a resolved name
 * (+ optional skin texture property, empty string if none available) for a
 * player UUID the requesting client's own tab-list-backed {@code PlayerInfo}
 * doesn't know about. Broadcast globally (like every other sync payload
 * here) rather than unicast to the requester, so a second client that needed
 * the same profile doesn't have to ask again.
 */
public record SyncPlayerProfilePayload(UUID playerId, String name, String textureValue, String textureSignature) implements CustomPacketPayload {
    public static final Type<SyncPlayerProfilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_player_profile"));
    public static final StreamCodec<FriendlyByteBuf, SyncPlayerProfilePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.playerId);
                buffer.writeUtf(payload.name);
                buffer.writeUtf(payload.textureValue);
                buffer.writeUtf(payload.textureSignature);
            },
            buffer -> new SyncPlayerProfilePayload(buffer.readUUID(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncPlayerProfilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPlayerProfiles.update(
                payload.playerId(), payload.name(), payload.textureValue(), payload.textureSignature()));
    }
}
