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
    // FTB Chunks fires BEFORE_CLAIM/BEFORE_UNCLAIM strictly before the claim
    // registry is mutated, and only afterwards clears ChunkTeamData's cached
    // getClaimedChunks() collection. Reading the chunk count in the AFTER_*
    // handlers therefore risks a stale cache still including (or excluding)
    // the chunk that just changed. We capture the accurate pre-mutation
    // count here instead of reconstructing it after the fact.
    private int pendingClaimCount = -1;
    private int pendingUnclaimCount = -1;

    public ChunkClaimHandler() {
        ClaimedChunkEvent.BEFORE_CLAIM.register(this::beforeClaim);
        ClaimedChunkEvent.BEFORE_UNCLAIM.register(this::beforeUnclaim);
        ClaimedChunkEvent.AFTER_CLAIM.register(this::afterClaim);
        ClaimedChunkEvent.AFTER_UNCLAIM.register(this::afterUnclaim);
    }

    private void afterClaim(CommandSourceStack source, ClaimedChunk chunk) {
        Team claimingTeam = chunk.getTeamData().getTeam();
        if (claimingTeam != null) {
            MinecraftServer server = source.getServer();
            String claimedChunkKey = dev.malik.lcftbhook.data.ChunkPosKey.encode(chunk.getPos());
            dev.malik.lcftbhook.data.FtbHookSavedData.get(server).markChunkUnsettled(
                    claimingTeam.getTeamId(), claimedChunkKey
            );
            // Marketplace-only cooldown - a fresh CLAIM can't be listed until
            // settled, unlike a fresh private buy (see FtbHookSavedData's
            // freshly-claimed-chunks section).
            dev.malik.lcftbhook.data.FtbHookSavedData.get(server).markChunkFreshlyClaimed(
                    claimingTeam.getTeamId(), claimedChunkKey
            );
            // Deferred to a single broadcast in ClaimBatchContext.flush() during a
            // bulk claim (mirrors syncClaimUi's own batch-vs-single handling below) -
            // otherwise a large claim batch would broadcast the full unsettled-chunk
            // set to every player once per chunk claimed.
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordUnsettledChunk();
            } else {
                dev.malik.lcftbhook.service.MarketplaceService.broadcastUnsettledChunks(server);
            }
        }
        if (ClaimBatchContext.isExecuting()) {
            int countBeforeClaim = pendingClaimCount >= 0
                    ? pendingClaimCount
                    : chunk.getTeamData().getClaimedChunks().size() - 1;
            pendingClaimCount = -1;
            if (FreeChunkAllowance.isClaimFree(countBeforeClaim)) {
                ClaimBatchContext.recordClaimFree();
            }
            return;
        }
        syncClaimUi(source);
    }

    private CompoundEventResult<ClaimResult> beforeClaim(CommandSourceStack source, ClaimedChunk chunk) {
        int currentCount = chunk.getTeamData().getClaimedChunks().size();
        pendingClaimCount = currentCount;
        if (FreeChunkAllowance.isClaimFree(currentCount)) {
            return CompoundEventResult.pass();
        }
        return handlePurchase(source, dev.malik.lcftbhook.service.ClaimPricingService.priceForClaim(currentCount));
    }

    private CompoundEventResult<ClaimResult> beforeUnclaim(CommandSourceStack source, ClaimedChunk chunk) {
        pendingUnclaimCount = chunk.getTeamData().getClaimedChunks().size();
        return CompoundEventResult.pass();
    }

    private void afterUnclaim(CommandSourceStack source, ClaimedChunk chunk) {
        MinecraftServer unclaimServer = source.getServer();
        if (unclaimServer != null) {
            dev.malik.lcftbhook.service.RegionService.onChunkUnclaimed(unclaimServer, chunk);
        }

        long refundAmount = calculateUnclaimRefund(chunk);
        if (refundAmount <= 0) {
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordUnclaim(0);
            } else if (!ClaimBatchContext.suppressNotifications()) {
                syncClaimUi(source);
            }
            return;
        }

        Team team = chunk.getTeamData().getTeam();
        MinecraftServer server = source.getServer();
        if (team == null || server == null) {
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordUnclaim(0);
            } else if (!ClaimBatchContext.suppressNotifications()) {
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
                if (ClaimBatchContext.isExecuting()) {
                    ClaimBatchContext.recordUnclaim(0);
                }
                return;
            }
        } else {
            account = BankAccountHelper.getAccountForTeam(server, team);
        }
        account.depositMoney(refund);

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordUnclaim(refundAmount);
            } else if (!ClaimBatchContext.suppressNotifications()) {
                int refundPercent = (int) Math.round(LCFtbHookConfig.SERVER.unclaimRefundRatio.get() * 100.0D);
                player.displayClientMessage(
                        Component.translatable(
                                "message.lc_ftb_hook.unclaim_refund",
                                MoneyMessageUtil.formatValue(refund),
                                refundPercent
                        ),
                        false
                );
                syncClaimUi(source);
            }
            return;
        }
        syncClaimUi(source);
    }

    private long calculateUnclaimRefund(ClaimedChunk chunk) {
        int countBeforeUnclaim = pendingUnclaimCount >= 0
                ? pendingUnclaimCount
                : chunk.getTeamData().getClaimedChunks().size() + 1;
        pendingUnclaimCount = -1;
        if (!FreeChunkAllowance.shouldRefundOnUnclaim(countBeforeUnclaim)) {
            return 0L;
        }

        long claimPrice = dev.malik.lcftbhook.service.ClaimPricingService.priceForClaim(countBeforeUnclaim - 1);
        double refundRatio = LCFtbHookConfig.SERVER.unclaimRefundRatio.get();
        if (claimPrice <= 0 || refundRatio <= 0) {
            return 0L;
        }

        return (long) Math.floor(claimPrice * refundRatio);
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

        if (!BankAccountHelper.canPurchaseForTeam(player.server, team, player.getUUID())) {
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
        if (ClaimBatchContext.isExecuting()) {
            ClaimBatchContext.recordClaimSpend(priceAmount);
        } else {
            ServerPlayer payingPlayer = source.getPlayer();
            if (payingPlayer != null) {
                payingPlayer.displayClientMessage(
                        Component.translatable(
                                "message.lc_ftb_hook.claim_paid",
                                MoneyMessageUtil.formatValue(price)
                        ),
                        false
                );
                ClaimPriceSync.syncToPlayer(payingPlayer);
            }
        }
        return CompoundEventResult.pass();
    }
}
