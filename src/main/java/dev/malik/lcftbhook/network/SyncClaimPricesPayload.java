package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientClaimPrices;
import dev.malik.lcftbhook.client.ClientWarState;
import dev.malik.lcftbhook.client.PendingStateUiRefresh;
import dev.malik.lcftbhook.client.TeamUiRefresh;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncClaimPricesPayload(
        long claimPrice,
        long forceLoadUpkeepPrice,
        int upkeepPeriodMinutes,
        int minutesUntilNextUpkeep,
        int freeChunks,
        int claimedChunks,
        boolean balanceSynced,
        boolean balanceEmpty,
        String balanceText,
        boolean privateBalanceEmpty,
        String privateBalanceText,
        boolean hasCountryBalance,
        boolean countryBalanceEmpty,
        String countryBalanceText,
        long mobGriefProtectionPrice,
        long explosionProtectionPrice,
        long pvpDisablePrice,
        long blockInteractProtectionPrice,
        long blockEditProtectionPrice,
        long entityInteractProtectionPrice,
        boolean warEnabled
) implements CustomPacketPayload {
    public static final Type<SyncClaimPricesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_claim_prices"));
    public static final StreamCodec<FriendlyByteBuf, SyncClaimPricesPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.claimPrice);
                buffer.writeLong(payload.forceLoadUpkeepPrice);
                buffer.writeVarInt(payload.upkeepPeriodMinutes);
                buffer.writeVarInt(payload.minutesUntilNextUpkeep);
                buffer.writeVarInt(payload.freeChunks);
                buffer.writeVarInt(payload.claimedChunks);
                buffer.writeBoolean(payload.balanceSynced);
                buffer.writeBoolean(payload.balanceEmpty);
                buffer.writeUtf(payload.balanceText);
                buffer.writeBoolean(payload.privateBalanceEmpty);
                buffer.writeUtf(payload.privateBalanceText);
                buffer.writeBoolean(payload.hasCountryBalance);
                buffer.writeBoolean(payload.countryBalanceEmpty);
                buffer.writeUtf(payload.countryBalanceText);
                buffer.writeLong(payload.mobGriefProtectionPrice);
                buffer.writeLong(payload.explosionProtectionPrice);
                buffer.writeLong(payload.pvpDisablePrice);
                buffer.writeLong(payload.blockInteractProtectionPrice);
                buffer.writeLong(payload.blockEditProtectionPrice);
                buffer.writeLong(payload.entityInteractProtectionPrice);
                buffer.writeBoolean(payload.warEnabled());
            },
            buffer -> new SyncClaimPricesPayload(
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readUtf(),
                    buffer.readBoolean(),
                    buffer.readUtf(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readUtf(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncClaimPricesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientClaimPrices.update(
                    payload.claimPrice(),
                    payload.forceLoadUpkeepPrice(),
                    payload.upkeepPeriodMinutes(),
                    payload.minutesUntilNextUpkeep(),
                    payload.freeChunks(),
                    payload.claimedChunks(),
                    payload.balanceSynced(),
                    payload.balanceEmpty(),
                    payload.balanceText(),
                    payload.privateBalanceEmpty(),
                    payload.privateBalanceText(),
                    payload.hasCountryBalance(),
                    payload.countryBalanceEmpty(),
                    payload.countryBalanceText(),
                    payload.mobGriefProtectionPrice(),
                    payload.explosionProtectionPrice(),
                    payload.pvpDisablePrice(),
                    payload.blockInteractProtectionPrice(),
                    payload.blockEditProtectionPrice(),
                    payload.entityInteractProtectionPrice()
            );
            ClientWarState.setWarModuleEnabled(payload.warEnabled());
            PendingStateUiRefresh.refreshOpenScreens();
            TeamUiRefresh.refreshMyTeamScreenIfOpen();
        });
    }
}
