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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record SetChunkAccessListPayload(String chunkKey, String propertyId, boolean whitelist, Set<UUID> players) implements CustomPacketPayload {
    public static final Type<SetChunkAccessListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "set_chunk_access_list"));
    public static final StreamCodec<FriendlyByteBuf, SetChunkAccessListPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.chunkKey);
                buffer.writeUtf(payload.propertyId);
                buffer.writeBoolean(payload.whitelist);
                buffer.writeVarInt(payload.players.size());
                for (UUID playerId : payload.players) {
                    buffer.writeUUID(playerId);
                }
            },
            buffer -> {
                String chunkKey = buffer.readUtf();
                String propertyId = buffer.readUtf();
                boolean whitelist = buffer.readBoolean();
                int count = buffer.readVarInt();
                Set<UUID> players = new HashSet<>(count);
                for (int i = 0; i < count; i++) {
                    players.add(buffer.readUUID());
                }
                return new SetChunkAccessListPayload(chunkKey, propertyId, whitelist, players);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetChunkAccessListPayload payload, IPayloadContext context) {
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
            MarketplaceService.setAccessList(player, payload.chunkKey, property, payload.whitelist, payload.players);
        });
    }
}
