package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientRegionMembership;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bare chunkKey -&gt; regionId map, broadcast globally (unlike
 * {@link SyncRegionsPayload}) because every client's map needs to render
 * region borders for every visible team's claims, not just the viewer's own.
 */
public record SyncRegionMembershipPayload(Map<String, UUID> chunkRegions) implements CustomPacketPayload {
    public static final Type<SyncRegionMembershipPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_region_membership"));
    public static final StreamCodec<FriendlyByteBuf, SyncRegionMembershipPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.chunkRegions.size());
                for (var entry : payload.chunkRegions.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeUUID(entry.getValue());
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                Map<String, UUID> chunkRegions = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    chunkRegions.put(buffer.readUtf(), buffer.readUUID());
                }
                return new SyncRegionMembershipPayload(chunkRegions);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncRegionMembershipPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRegionMembership.update(payload.chunkRegions));
    }
}
