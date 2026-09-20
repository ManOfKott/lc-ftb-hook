package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Assigns a selection of chunks (from the FTB Chunks screen or Xaero's map) to a region, queued until the next upkeep settlement. */
public record RequestRegionAssignmentPayload(List<String> chunkKeys, UUID targetRegion) implements CustomPacketPayload {
    public static final Type<RequestRegionAssignmentPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "request_region_assignment"));
    public static final StreamCodec<FriendlyByteBuf, RequestRegionAssignmentPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.chunkKeys.size());
                for (String key : payload.chunkKeys) {
                    buffer.writeUtf(key);
                }
                buffer.writeUUID(payload.targetRegion);
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<String> keys = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    keys.add(buffer.readUtf());
                }
                return new RequestRegionAssignmentPayload(keys, buffer.readUUID());
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestRegionAssignmentPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RegionService.handleAssignmentRequest(player, payload.chunkKeys, payload.targetRegion);
            }
        });
    }
}
