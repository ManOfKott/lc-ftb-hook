package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.bank.ClaimBatchContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.List;

public final class ClaimSettlementHelper {
    private ClaimSettlementHelper() {
    }

    /** Total refund for unclaiming ALL {@code claimedChunksBeforeUnclaim} chunks a team/player currently holds, summed per-chunk over whichever price tiers those chunks actually fall into (see {@link ClaimPricingService}), then the usual refund ratio applied once to the total. */
    public static long refundForFullUnclaim(int claimedChunksBeforeUnclaim) {
        double refundRatio = LCFtbHookConfig.SERVER.unclaimRefundRatio.get();
        if (refundRatio <= 0) {
            return 0;
        }
        long total = ClaimPricingService.sumPriceForRange(0, claimedChunksBeforeUnclaim);
        return (long) Math.floor(total * refundRatio);
    }

    public static int unclaimAll(ChunkTeamData chunkData, CommandSourceStack source) {
        return unclaimAll(chunkData, source, true);
    }

    public static int unclaimAll(ChunkTeamData chunkData, CommandSourceStack source, boolean suppressNotifications) {
        List<ClaimedChunk> claimedChunks = new ArrayList<>(chunkData.getClaimedChunks());
        if (claimedChunks.isEmpty()) {
            return 0;
        }

        int[] unclaimed = {0};
        Runnable action = () -> {
            for (ClaimedChunk chunk : claimedChunks) {
                if (chunkData.unclaim(source, chunk.getPos(), false).isSuccess()) {
                    unclaimed[0]++;
                }
            }
        };
        if (suppressNotifications) {
            ClaimBatchContext.runSuppressingNotifications(action);
        } else {
            action.run();
        }
        return unclaimed[0];
    }
}
