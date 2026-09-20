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

/** "Buy" ({@code asCountry=false}, from the buyer's personal account) or "Buy as Country" (from the team account, resets to state ownership). */
public record BuyChunksPayload(List<String> chunkKeys, boolean asCountry) implements CustomPacketPayload {
    public static final Type<BuyChunksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "buy_chunks"));
    public static final StreamCodec<FriendlyByteBuf, BuyChunksPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.chunkKeys.size());
                for (String key : payload.chunkKeys) {
                    buffer.writeUtf(key);
                }
                buffer.writeBoolean(payload.asCountry);
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<String> keys = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    keys.add(buffer.readUtf());
                }
                return new BuyChunksPayload(keys, buffer.readBoolean());
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BuyChunksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarketplaceService.buy(player, payload.chunkKeys, payload.asCountry);
            }
        });
    }
}
