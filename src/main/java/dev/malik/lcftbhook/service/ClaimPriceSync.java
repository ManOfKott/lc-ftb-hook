package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.network.SyncClaimPricesPayload;
import dev.malik.lcftbhook.service.WarService;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClaimPriceSync {
    private ClaimPriceSync() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createPayload(player));
    }

    public static SyncClaimPricesPayload createPayload(ServerPlayer player) {
        boolean balanceSynced = false;
        boolean balanceEmpty = true;
        String balanceText = "";
        int claimedChunks = 0;

        if (FTBTeamsAPI.api().isManagerLoaded()) {
            Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
            if (team != null) {
                BankAccountHelper.ensurePartyAccountExists(player.server, team);
                IBankAccount account = BankAccountHelper.getAccountForPlayer(player.server, player);
                balanceSynced = true;
                balanceEmpty = account.getMoneyStorage().isEmpty();
                if (!balanceEmpty) {
                    balanceText = account.getMoneyStorage().getAllValueText().getString();
                }
                if (FTBChunksAPI.api().isManagerLoaded()) {
                    claimedChunks = FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size();
                }
            }
        }

        return new SyncClaimPricesPayload(
                LCFtbHookConfig.SERVER.claimPrice.get(),
                LCFtbHookConfig.SERVER.forceLoadUpkeepPrice.get(),
                LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get(),
                LCFtbHookConfig.SERVER.freeChunks.get(),
                claimedChunks,
                balanceSynced,
                balanceEmpty,
                balanceText,
                LCFtbHookConfig.SERVER.mobGriefProtectionPrice.get(),
                LCFtbHookConfig.SERVER.explosionProtectionPrice.get(),
                LCFtbHookConfig.SERVER.pvpDisablePrice.get(),
                LCFtbHookConfig.SERVER.blockInteractProtectionPrice.get(),
                LCFtbHookConfig.SERVER.blockEditProtectionPrice.get(),
                LCFtbHookConfig.SERVER.entityInteractProtectionPrice.get(),
                ProtectionPricing.landChunkGroupSize(),
                WarService.isEnabled()
        );
    }
}
