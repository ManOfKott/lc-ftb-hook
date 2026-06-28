package dev.malik.lcftbhook.bank;

import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.util.MoneyMessageUtil;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

public final class ClaimBatchContext {
    private static final ThreadLocal<Boolean> VALIDATING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SUPPRESS_NOTIFICATIONS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<UUID> PERSONAL_REFUND_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<BatchState> EXECUTING = new ThreadLocal<>();

    private ClaimBatchContext() {
    }

    public static boolean isValidating() {
        return VALIDATING.get();
    }

    public static void beginValidation() {
        VALIDATING.set(true);
    }

    public static void endValidation() {
        VALIDATING.remove();
    }

    public static boolean isExecuting() {
        return EXECUTING.get() != null;
    }

    public static boolean suppressNotifications() {
        return SUPPRESS_NOTIFICATIONS.get();
    }

    public static void runSuppressingNotifications(Runnable action) {
        SUPPRESS_NOTIFICATIONS.set(true);
        try {
            action.run();
        } finally {
            SUPPRESS_NOTIFICATIONS.remove();
        }
    }

    public static void runPersonalRefundSettlement(UUID playerId, Runnable action) {
        SUPPRESS_NOTIFICATIONS.set(true);
        PERSONAL_REFUND_PLAYER.set(playerId);
        try {
            action.run();
        } finally {
            SUPPRESS_NOTIFICATIONS.remove();
            PERSONAL_REFUND_PLAYER.remove();
        }
    }

    @Nullable
    public static UUID personalRefundPlayerId() {
        return PERSONAL_REFUND_PLAYER.get();
    }

    public static void beginExecution(RequestChunkChangePacket.ChunkChangeOp operation, int chunkCount, UUID playerId) {
        if (chunkCount <= 1) {
            return;
        }
        if (operation != RequestChunkChangePacket.ChunkChangeOp.CLAIM
                && operation != RequestChunkChangePacket.ChunkChangeOp.UNCLAIM) {
            return;
        }
        EXECUTING.set(new BatchState(operation, playerId));
    }

    public static void recordClaimSpend(long priceCopper) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.claimPaidCopper += priceCopper;
        state.claimPaidCount++;
        state.uiSyncNeeded = true;
    }

    public static void recordClaimFree() {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.claimFreeCount++;
        state.uiSyncNeeded = true;
    }

    public static void recordUnclaim(long refundCopper) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.unclaimCount++;
        state.refundCopper += refundCopper;
        state.uiSyncNeeded = true;
    }

    public static void recordClaimInsufficientFunds(IBankAccount account, long unitPriceCopper) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.claimInsufficientCount++;
        state.claimUnitPriceCopper = unitPriceCopper;
        state.insufficientBalance = MoneyMessageUtil.formatBalance(account);
        state.uiSyncNeeded = true;
    }

    public static void markUiSyncNeeded() {
        BatchState state = EXECUTING.get();
        if (state != null) {
            state.uiSyncNeeded = true;
        }
    }

    public static void flush(@Nullable ServerPlayer player) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }

        try {
            if (player == null || !player.getUUID().equals(state.playerId)) {
                return;
            }

            if (state.operation == RequestChunkChangePacket.ChunkChangeOp.UNCLAIM && state.unclaimCount > 0) {
                sendUnclaimSummary(player, state);
            }

            if (state.operation == RequestChunkChangePacket.ChunkChangeOp.CLAIM) {
                sendClaimSummary(player, state);
            }

            if (state.uiSyncNeeded) {
                ClaimPriceSync.syncToPlayer(player);
            }
        } finally {
            EXECUTING.remove();
        }
    }

    private static void sendUnclaimSummary(ServerPlayer player, BatchState state) {
        int refundPercent = refundPercent();
        if (state.refundCopper > 0) {
            Component refund = MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(state.refundCopper));
            if (state.unclaimCount == 1) {
                player.displayClientMessage(
                        Component.translatable("message.lc_ftb_hook.unclaim_refund", refund, refundPercent),
                        false
                );
            } else {
                player.displayClientMessage(
                        Component.translatable(
                                "message.lc_ftb_hook.unclaim_refund_bulk",
                                refund,
                                state.unclaimCount,
                                refundPercent
                        ),
                        false
                );
            }
            return;
        }

        if (state.unclaimCount == 1) {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.unclaim_bulk_single"),
                    false
            );
        } else {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.unclaim_bulk", state.unclaimCount),
                    false
            );
        }
    }

    private static void sendClaimSummary(ServerPlayer player, BatchState state) {
        if (state.claimInsufficientCount > 0) {
            Component unitPrice = MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(state.claimUnitPriceCopper));
            Component balance = state.insufficientBalance == null
                    ? Component.translatable("message.lc_ftb_hook.balance_empty")
                    : state.insufficientBalance;
            if (state.claimInsufficientCount == 1) {
                player.displayClientMessage(
                        Component.translatable("message.lc_ftb_hook.insufficient_funds", unitPrice, balance),
                        false
                );
            } else {
                player.displayClientMessage(
                        Component.translatable(
                                "message.lc_ftb_hook.insufficient_funds_bulk_claim",
                                unitPrice,
                                state.claimInsufficientCount,
                                balance
                        ),
                        false
                );
            }
        }

        int claimedCount = state.claimPaidCount + state.claimFreeCount;
        if (claimedCount <= 0) {
            return;
        }

        if (state.claimPaidCopper > 0) {
            Component spent = MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(state.claimPaidCopper));
            if (claimedCount == 1) {
                player.displayClientMessage(
                        Component.translatable("message.lc_ftb_hook.claim_paid", spent),
                        false
                );
            } else {
                player.displayClientMessage(
                        Component.translatable("message.lc_ftb_hook.claim_paid_bulk", spent, claimedCount),
                        false
                );
            }
            return;
        }

        if (claimedCount == 1) {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.claim_free"),
                    false
            );
        } else {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.claim_free_bulk", claimedCount),
                    false
            );
        }
    }

    private static int refundPercent() {
        return (int) Math.round(LCFtbHookConfig.SERVER.unclaimRefundRatio.get() * 100.0D);
    }

    private static final class BatchState {
        private final RequestChunkChangePacket.ChunkChangeOp operation;
        private final UUID playerId;
        private long refundCopper;
        private int unclaimCount;
        private long claimPaidCopper;
        private int claimPaidCount;
        private int claimFreeCount;
        private int claimInsufficientCount;
        private long claimUnitPriceCopper;
        @Nullable
        private Component insufficientBalance;
        private boolean uiSyncNeeded;

        private BatchState(RequestChunkChangePacket.ChunkChangeOp operation, UUID playerId) {
            this.operation = operation;
            this.playerId = playerId;
        }
    }
}
