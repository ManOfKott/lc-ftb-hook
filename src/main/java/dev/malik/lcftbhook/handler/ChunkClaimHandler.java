package dev.malik.lcftbhook.handler;

import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.bank.InsufficientFundsClaimResult;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.service.FreeChunkAllowance;
import dev.malik.lcftbhook.util.MoneyMessageUtil;
import dev.malik.lcftbhook.util.MoneyUtil;
import dev.architectury.event.CompoundEventResult;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ChunkClaimHandler {
    public ChunkClaimHandler() {
        ClaimedChunkEvent.BEFORE_CLAIM.register(this::beforeClaim);
        ClaimedChunkEvent.AFTER_CLAIM.register(this::afterClaim);
        ClaimedChunkEvent.AFTER_UNCLAIM.register(this::afterUnclaim);
    }

    private void afterClaim(CommandSourceStack source, ClaimedChunk chunk) {
        if (ClaimBatchContext.isExecuting()) {
            ClaimBatchContext.markUiSyncNeeded();
            return;
        }
        syncClaimUi(source);
    }

    private CompoundEventResult<ClaimResult> beforeClaim(CommandSourceStack source, ClaimedChunk chunk) {
        int currentCount = chunk.getTeamData().getClaimedChunks().size();
        if (FreeChunkAllowance.isClaimFree(currentCount)) {
            return CompoundEventResult.pass();
        }
        return handlePurchase(source, LCFtbHookConfig.SERVER.claimPrice.get());
    }

    private void afterUnclaim(CommandSourceStack source, ClaimedChunk chunk) {
        int countBeforeUnclaim = chunk.getTeamData().getClaimedChunks().size() + 1;
        if (!FreeChunkAllowance.shouldRefundOnUnclaim(countBeforeUnclaim)) {
            if (!ClaimBatchContext.isExecuting() && !ClaimBatchContext.suppressNotifications()) {
                syncClaimUi(source);
            }
            return;
        }

        long claimPrice = LCFtbHookConfig.SERVER.claimPrice.get();
        double refundRatio = LCFtbHookConfig.SERVER.unclaimRefundRatio.get();
        if (claimPrice <= 0 || refundRatio <= 0) {
            if (!ClaimBatchContext.isExecuting()) {
                syncClaimUi(source);
            }
            return;
        }

        long refundAmount = (long) Math.floor(claimPrice * refundRatio);
        if (refundAmount <= 0) {
            if (!ClaimBatchContext.isExecuting()) {
                syncClaimUi(source);
            }
            return;
        }

        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            if (!ClaimBatchContext.isExecuting()) {
                syncClaimUi(source);
            }
            return;
        }

        MinecraftServer server = source.getServer();
        if (server == null) {
            if (!ClaimBatchContext.isExecuting()) {
                syncClaimUi(source);
            }
            return;
        }

        BankAccountHelper.ensurePartyAccountExists(server, team);
        MoneyValue refund = MoneyUtil.fromCopper(refundAmount);
        UUID personalRefundPlayer = ClaimBatchContext.personalRefundPlayerId();
        IBankAccount account;
        if (personalRefundPlayer != null) {
            account = PlayerBankReference.of(personalRefundPlayer).get();
            if (account == null) {
                LCFtbHook.LOGGER.warn("Missing personal bank account for refund on party join: {}", personalRefundPlayer);
                return;
            }
        } else {
            account = BankAccountHelper.getAccountForTeam(server, team);
        }
        account.depositMoney(refund);

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordUnclaimRefund(refundAmount);
            } else if (!ClaimBatchContext.suppressNotifications()) {
                player.displayClientMessage(
                        Component.translatable("message.lc_ftb_hook.unclaim_refund", MoneyMessageUtil.formatValue(refund)),
                        false
                );
                syncClaimUi(source);
            }
            return;
        }
        syncClaimUi(source);
    }

    private void syncClaimUi(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            ClaimPriceSync.syncToPlayer(player);
        }
    }

    private CompoundEventResult<ClaimResult> handlePurchase(CommandSourceStack source, long priceAmount) {
        if (ClaimBatchContext.isValidating()) {
            return CompoundEventResult.pass();
        }

        ServerPlayer player = source.getPlayer();
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return CompoundEventResult.pass();
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return CompoundEventResult.pass();
        }

        if (!BankAccountHelper.canPurchaseForTeam(team, player.getUUID())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_ftb_hook.claim_rank_denied"));
        }

        MoneyValue price = MoneyUtil.fromCopper(priceAmount);
        if (price.isEmpty()) {
            return CompoundEventResult.pass();
        }

        BankAccountHelper.ensurePartyAccountExists(player.server, team);
        IBankAccount account = BankAccountHelper.getAccountForPlayer(player.server, player);

        if (!account.getMoneyStorage().containsValue(price)) {
            Component balance = MoneyMessageUtil.formatBalance(account);
            Component priceText = MoneyMessageUtil.formatValue(price);
            Component message = Component.translatable("message.lc_ftb_hook.insufficient_funds", priceText, balance);
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordClaimInsufficientFunds(account, priceAmount);
            } else {
                player.displayClientMessage(message, false);
                ClaimPriceSync.syncToPlayer(player);
            }
            return CompoundEventResult.interruptFalse(new InsufficientFundsClaimResult(message.copy()));
        }

        account.withdrawMoney(price);
        return CompoundEventResult.pass();
    }
}
