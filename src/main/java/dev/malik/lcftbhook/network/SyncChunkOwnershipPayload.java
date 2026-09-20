package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.PlayerAccessList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bare chunkKey -&gt; ownership/listing map, broadcast globally (needed by both the FTB screen tooltip and Xaero's map/tooltip client-side). */
public record SyncChunkOwnershipPayload(Map<String, ChunkOwnership> chunkOwnership) implements CustomPacketPayload {
    public static final Type<SyncChunkOwnershipPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_chunk_ownership"));
    public static final StreamCodec<FriendlyByteBuf, SyncChunkOwnershipPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.chunkOwnership.size());
                for (var entry : payload.chunkOwnership.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    ChunkOwnership ownership = entry.getValue();
                    buffer.writeBoolean(ownership.privateOwner() != null);
                    if (ownership.privateOwner() != null) {
                        buffer.writeUUID(ownership.privateOwner());
                    }
                    buffer.writeBoolean(ownership.listingPricePerChunk() != null);
                    if (ownership.listingPricePerChunk() != null) {
                        buffer.writeLong(ownership.listingPricePerChunk());
                    }
                    buffer.writeVarInt(ownership.protectionOverride().size());
                    for (var overrideEntry : ownership.protectionOverride().entrySet()) {
                        buffer.writeUtf(overrideEntry.getKey());
                        buffer.writeUtf(overrideEntry.getValue());
                    }
                    buffer.writeVarInt(ownership.accessLists().size());
                    for (var accessEntry : ownership.accessLists().entrySet()) {
                        buffer.writeUtf(accessEntry.getKey());
                        PlayerAccessList list = accessEntry.getValue();
                        buffer.writeBoolean(list.whitelist());
                        buffer.writeVarInt(list.players().size());
                        for (UUID playerId : list.players()) {
                            buffer.writeUUID(playerId);
                        }
                    }
                    buffer.writeUtf(ownership.label() == null ? "" : ownership.label());
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                Map<String, ChunkOwnership> ownershipMap = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    String key = buffer.readUtf();
                    var owner = buffer.readBoolean() ? buffer.readUUID() : null;
                    Long price = buffer.readBoolean() ? buffer.readLong() : null;
                    int overrideCount = buffer.readVarInt();
                    Map<String, String> overrides = new HashMap<>(overrideCount);
                    for (int j = 0; j < overrideCount; j++) {
                        overrides.put(buffer.readUtf(), buffer.readUtf());
                    }
                    int accessCount = buffer.readVarInt();
                    Map<String, PlayerAccessList> accessLists = new HashMap<>(accessCount);
                    for (int j = 0; j < accessCount; j++) {
                        String propertyId = buffer.readUtf();
                        boolean whitelist = buffer.readBoolean();
                        int playerCount = buffer.readVarInt();
                        Set<UUID> players = new HashSet<>(playerCount);
                        for (int k = 0; k < playerCount; k++) {
                            players.add(buffer.readUUID());
                        }
                        accessLists.put(propertyId, new PlayerAccessList(whitelist, players));
                    }
                    String label = buffer.readUtf();
                    ownershipMap.put(key, new ChunkOwnership(owner, price, overrides, accessLists, label.isEmpty() ? null : label));
                }
                return new SyncChunkOwnershipPayload(ownershipMap);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncChunkOwnershipPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientChunkOwnership.update(payload.chunkOwnership));
    }
}
