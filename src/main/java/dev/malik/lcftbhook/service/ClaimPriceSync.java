package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.network.SyncClaimPricesPayload;
import dev.malik.lcftbhook.service.WarService;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
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
        boolean privateBalanceEmpty = true;
        String privateBalanceText = "";
        boolean hasCountryBalance = false;
        boolean countryBalanceEmpty = true;
        String countryBalanceText = "";
        int claimedChunks = 0;

        if (FTBTeamsAPI.api().isManagerLoaded()) {
            Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
            if (team != null) {
                BankAccountHelper.ensurePartyAccountExists(player.server, team);
                IBankAccount account = BankAccountHelper.getAccountForPlayer(player.server, player);
                balanceSynced = true;
                MoneyValue balance = MoneyUtil.mainChainValue(account);
                balanceEmpty = balance.isEmpty();
                if (!balanceEmpty) {
                    balanceText = balance.getText().getString();
                }

                IBankAccount privateAccount = PlayerBankReference.of(player.getUUID()).get();
                if (privateAccount != null) {
                    MoneyValue privateBalance = MoneyUtil.mainChainValue(privateAccount);
                    privateBalanceEmpty = privateBalance.isEmpty();
                    if (!privateBalanceEmpty) {
                        privateBalanceText = privateBalance.getText().getString();
                    }
                }

                // Only shown to officers/the owner of an actual party - a
                // solo player's own "team" IS their personal account (same
                // UUID), so there's nothing distinct to show as a "country"
                // balance in that case.
                if (team.isPartyTeam() && team.getRankForPlayer(player.getUUID()).isOfficerOrBetter()) {
                    IBankAccount countryAccount = BankAccountHelper.getAccountForTeam(player.server, team);
                    hasCountryBalance = true;
                    MoneyValue countryBalance = MoneyUtil.mainChainValue(countryAccount);
                    countryBalanceEmpty = countryBalance.isEmpty();
                    if (!countryBalanceEmpty) {
                        countryBalanceText = countryBalance.getText().getString();
                    }
                }

                if (FTBChunksAPI.api().isManagerLoaded()) {
                    claimedChunks = FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size();
                }
            }
        }

        return new SyncClaimPricesPayload(
                ClaimPricingService.priceForClaim(claimedChunks),
                LCFtbHookConfig.SERVER.forceLoadUpkeepPrice.get(),
                LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get(),
                UpkeepService.minutesUntilNextUpkeep(player.server),
                UpkeepService.secondsUntilNextUpkeep(player.server),
                LCFtbHookConfig.SERVER.freeChunks.get(),
                claimedChunks,
                balanceSynced,
                balanceEmpty,
                balanceText,
                privateBalanceEmpty,
                privateBalanceText,
                hasCountryBalance,
                countryBalanceEmpty,
                countryBalanceText,
                LCFtbHookConfig.SERVER.mobGriefProtectionPrice.get(),
                LCFtbHookConfig.SERVER.explosionProtectionPrice.get(),
                LCFtbHookConfig.SERVER.pvpDisablePrice.get(),
                LCFtbHookConfig.SERVER.blockInteractProtectionPrice.get(),
                LCFtbHookConfig.SERVER.blockEditProtectionPrice.get(),
                LCFtbHookConfig.SERVER.entityInteractProtectionPrice.get(),
                WarService.isEnabled(),
                LCFtbHookConfig.SERVER.lockClaimVisibilityPublic.get()
        );
    }
}
