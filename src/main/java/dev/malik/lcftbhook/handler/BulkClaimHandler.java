package dev.malik.lcftbhook.handler;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbchunks.net.ChunkChangeResponsePacket;
import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.bank.BulkInsufficientFundsClaimResult;
import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.service.FreeChunkAllowance;
import dev.malik.lcftbhook.util.MoneyMessageUtil;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BulkClaimHandler {
    private BulkClaimHandler() {
    }

    public static boolean rejectIfInsufficientFunds(
            RequestChunkChangePacket message,
            ServerPlayer player,
            CommandSourceStack source,
            ChunkTeamData chunkTeamData
    ) {
        if (message.action() != RequestChunkChangePacket.ChunkChangeOp.CLAIM || message.chunks().size() <= 1) {
            return false;
        }

        long unitPrice = LCFtbHookConfig.SERVER.claimPrice.get();
        if (unitPrice <= 0L) {
            return false;
        }

        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return false;
        }

        if (!BankAccountHelper.canPurchaseForTeam(team, player.getUUID())) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        int claimableCount = countClaimableChunks(source, chunkTeamData, message.chunks(), level);
        if (claimableCount <= 1) {
            return false;
        }

        int paidClaims = FreeChunkAllowance.countPaidClaimsInBatch(chunkTeamData.getClaimedChunks().size(), claimableCount);
        if (paidClaims <= 0) {
            return false;
        }

        BankAccountHelper.ensurePartyAccountExists(player.server, team);
        IBankAccount account = BankAccountHelper.getAccountForPlayer(player.server, player);
        MoneyValue totalCost = MoneyUtil.fromCopper(unitPrice * paidClaims);
        if (account.getMoneyStorage().containsValue(totalCost)) {
            return false;
        }

        Component balance = MoneyMessageUtil.formatBalance(account);
        Component priceText = MoneyMessageUtil.formatValue(totalCost);
        Component chatMessage = Component.translatable(
                BulkInsufficientFundsClaimResult.RESULT_ID,
                priceText,
                paidClaims,
                balance
        );
        player.displayClientMessage(chatMessage, false);
        ClaimPriceSync.syncToPlayer(player);

        Map<String, Integer> problems = new HashMap<>();
        problems.put(BulkInsufficientFundsClaimResult.RESULT_ID, paidClaims);
        PacketDistributor.sendToPlayer(
                player,
                new ChunkChangeResponsePacket(message.chunks().size(), 0, problems)
        );
        return true;
    }

    public static ChunkTeamData resolveTeamData(RequestChunkChangePacket message, ServerPlayer player) {
        ChunkTeamData chunkTeamData = null;
        if (message.teamId().isPresent()) {
            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamByID(message.teamId().get());
            if (team.isEmpty()) {
                return null;
            }
            chunkTeamData = ClaimedChunkManagerImpl.getInstance().getOrCreateData(team.get());
        }
        if (chunkTeamData == null) {
            chunkTeamData = ClaimedChunkManagerImpl.getInstance().getOrCreateData(player);
        }
        return chunkTeamData;
    }

    private static int countClaimableChunks(
            CommandSourceStack source,
            ChunkTeamData chunkTeamData,
            Set<XZ> chunks,
            ServerLevel level
    ) {
        ClaimBatchContext.beginValidation();
        try {
            int claimableCount = 0;
            for (XZ pos : chunks) {
                ClaimResult result = chunkTeamData.claim(source, pos.dim(level), true);
                if (result.isSuccess()) {
                    claimableCount++;
                }
            }
            return claimableCount;
        } finally {
            ClaimBatchContext.endValidation();
        }
    }
}
