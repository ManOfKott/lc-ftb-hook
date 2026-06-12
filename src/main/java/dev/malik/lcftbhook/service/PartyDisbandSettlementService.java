package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.teams.LcTeamSyncService;
import dev.malik.lcftbhook.util.MoneyMessageUtil;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyDisbandSettlementService {
    private static final Set<UUID> SETTLING = ConcurrentHashMap.newKeySet();

    private PartyDisbandSettlementService() {
    }

    public static void settle(MinecraftServer server, Team team) {
        if (!team.isPartyTeam()) {
            return;
        }

        UUID teamId = team.getId();
        if (!SETTLING.add(teamId)) {
            return;
        }

        try {
            UUID ownerId = team.getOwner();
            if (ownerId == null) {
                return;
            }

            IBankAccount teamAccount = LcTeamSyncService.getLinkedBankAccount(server, teamId);
            if (teamAccount == null) {
                LCFtbHook.LOGGER.warn("No linked LC bank account found for disbanding party {}", teamId);
                return;
            }

            IBankAccount ownerAccount = PlayerBankReference.of(ownerId).get();
            if (ownerAccount == null) {
                LCFtbHook.LOGGER.warn(
                        "Could not transfer party funds for {}: missing personal account for owner {}",
                        teamId,
                        ownerId
                );
                return;
            }

            FtbHookSavedData savedData = FtbHookSavedData.get(server);
            savedData.setPendingState(teamId, savedData.getPendingState(teamId).cleared());

            Component accountBalance = MoneyMessageUtil.formatBalance(teamAccount);
            int claimedChunks = 0;
            int unclaimedChunks = 0;
            long refundCopper = 0L;

            if (FTBChunksAPI.api().isManagerLoaded()) {
                PendingChangeService.removeAllForceLoads(server, team);
                ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
                CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
                claimedChunks = chunkData.getClaimedChunks().size();
                unclaimedChunks = ClaimSettlementHelper.unclaimAll(chunkData, source);
                refundCopper = (long) FreeChunkAllowance.billableChunkCount(claimedChunks) * ClaimSettlementHelper.refundPerChunk();
            }

            if (teamAccount.getMoneyStorage().isEmpty() && unclaimedChunks == 0) {
                return;
            }

            Component totalReceived = MoneyMessageUtil.formatBalance(teamAccount);
            transferAllFunds(teamAccount, ownerAccount);
            notifyOwner(server, ownerId, accountBalance, totalReceived, unclaimedChunks, refundCopper);
            LCFtbHook.LOGGER.info(
                    "Settled disbanded party {} for owner {} ({} chunks, {} refund copper)",
                    teamId,
                    ownerId,
                    unclaimedChunks,
                    refundCopper
            );
        } catch (Exception exception) {
            LCFtbHook.LOGGER.error("Failed to settle disbanded party {}", teamId, exception);
        } finally {
            SETTLING.remove(teamId);
        }
    }

    private static void transferAllFunds(IBankAccount teamAccount, IBankAccount ownerAccount) {
        for (MoneyValue value : teamAccount.getMoneyStorage().allValues()) {
            if (value.isEmpty()) {
                continue;
            }
            teamAccount.withdrawMoney(value);
            ownerAccount.depositMoney(value);
        }
    }

    private static void notifyOwner(
            MinecraftServer server,
            UUID ownerId,
            Component accountBalance,
            Component totalReceived,
            int soldChunks,
            long refundCopper
    ) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            return;
        }

        Component chunkRefunds = MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(refundCopper));
        owner.displayClientMessage(
                Component.translatable(
                        "message.lc_ftb_hook.party_disband_settlement",
                        accountBalance,
                        totalReceived,
                        soldChunks,
                        chunkRefunds
                ),
                false
        );
    }
}
