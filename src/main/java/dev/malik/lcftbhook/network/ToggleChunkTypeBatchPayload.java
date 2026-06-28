package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.LandChunkService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ToggleChunkTypeBatchPayload(List<String> chunkKeys) implements CustomPacketPayload {
    public static final Type<ToggleChunkTypeBatchPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "toggle_chunk_type_batch"));
    public static final StreamCodec<FriendlyByteBuf, ToggleChunkTypeBatchPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.chunkKeys.size());
                for (String key : payload.chunkKeys) {
                    buffer.writeUtf(key);
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<String> keys = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    keys.add(buffer.readUtf());
                }
                return new ToggleChunkTypeBatchPayload(keys);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ToggleChunkTypeBatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                LandChunkService.handleToggleBatch(player, payload.chunkKeys);
            }
        });
    }
}
