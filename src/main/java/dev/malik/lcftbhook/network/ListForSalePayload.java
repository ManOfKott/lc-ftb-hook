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

/** "Sell as Country" ({@code asCountry=true}, state-owned chunks) or "Sell" (a private owner reselling their own chunk). */
public record ListForSalePayload(List<String> chunkKeys, long pricePerChunk, boolean asCountry) implements CustomPacketPayload {
    public static final Type<ListForSalePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "list_for_sale"));
    public static final StreamCodec<FriendlyByteBuf, ListForSalePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.chunkKeys.size());
                for (String key : payload.chunkKeys) {
                    buffer.writeUtf(key);
                }
                buffer.writeLong(payload.pricePerChunk);
                buffer.writeBoolean(payload.asCountry);
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<String> keys = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    keys.add(buffer.readUtf());
                }
                return new ListForSalePayload(keys, buffer.readLong(), buffer.readBoolean());
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ListForSalePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarketplaceService.listForSale(player, payload.chunkKeys, payload.pricePerChunk, payload.asCountry);
            }
        });
    }
}
