package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.util.MoneyMessageUtil;
import dev.malik.lcftbhook.util.MoneyUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyJoinClaimSettlementService {
    private static final Set<UUID> SETTLING = ConcurrentHashMap.newKeySet();

    private PartyJoinClaimSettlementService() {
    }

    public static void settle(MinecraftServer server, ServerPlayer player, Team previousTeam) {
        if (player == null || previousTeam == null || previousTeam.isPartyTeam()) {
            return;
        }
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }

        UUID playerId = player.getUUID();
        if (!SETTLING.add(playerId)) {
            return;
        }

        try {
            ChunkTeamData personalChunkData = FTBChunksAPI.api().getManager().getOrCreateData(previousTeam);
            if (personalChunkData.getClaimedChunks().isEmpty() && personalChunkData.getForceLoadedChunks().isEmpty()) {
                return;
            }

            FtbHookSavedData savedData = FtbHookSavedData.get(server);
            savedData.setPendingState(previousTeam.getTeamId(), savedData.getPendingState(previousTeam.getTeamId()).cleared());

            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            PendingChangeService.removeAllForceLoads(server, previousTeam);

            int claimedBefore = personalChunkData.getClaimedChunks().size();
            int[] unclaimed = {0};
            ClaimBatchContext.runPersonalRefundSettlement(playerId, () ->
                    unclaimed[0] = ClaimSettlementHelper.unclaimAll(personalChunkData, source, false)
            );

            if (unclaimed[0] <= 0) {
                return;
            }

            long refundCopper = (long) FreeChunkAllowance.billableChunkCount(claimedBefore) * ClaimSettlementHelper.refundPerChunk();
            Component refund = MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(refundCopper));
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.party_join_claim_refund", refund, unclaimed[0]),
                    false
            );
            ClaimPriceSync.syncToPlayer(player);

            LCFtbHook.LOGGER.info(
                    "Dissolved {} personal claims for {} when joining a party (refund {})",
                    unclaimed[0],
                    playerId,
                    refundCopper
            );
        } catch (Exception exception) {
            LCFtbHook.LOGGER.error("Failed to dissolve personal claims for {} on party join", playerId, exception);
        } finally {
            SETTLING.remove(playerId);
        }
    }
}
