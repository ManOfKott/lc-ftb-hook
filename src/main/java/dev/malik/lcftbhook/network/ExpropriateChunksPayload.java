package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.MarketplaceService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A team officer/owner force-buys back one or more privately-owned chunks
 * for a set compensation, paid per chunk to each chunk's own private owner.
 * State-owned chunks in the selection are simply ignored (not an error).
 */
public record ExpropriateChunksPayload(List<String> chunkKeys, long compensationPerChunk) implements CustomPacketPayload {
    public static final Type<ExpropriateChunksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "expropriate_chunks"));
    public static final StreamCodec<FriendlyByteBuf, ExpropriateChunksPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.chunkKeys.size());
                for (String key : payload.chunkKeys) {
                    buffer.writeUtf(key);
                }
                buffer.writeLong(payload.compensationPerChunk);
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<String> keys = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    keys.add(buffer.readUtf());
                }
                return new ExpropriateChunksPayload(keys, buffer.readLong());
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ExpropriateChunksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarketplaceService.expropriate(player, payload.chunkKeys, payload.compensationPerChunk);
            }
        });
    }
}
