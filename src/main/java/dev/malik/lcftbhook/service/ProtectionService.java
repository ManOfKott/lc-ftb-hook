package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ProtectionService {
    private static final ThreadLocal<Boolean> REVERTING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    private ProtectionService() {
    }

    public static boolean isReverting() {
        return REVERTING.get();
    }

    public static boolean isApplying() {
        return APPLYING.get();
    }

    public static void setApplying(boolean applying) {
        if (applying) {
            APPLYING.set(true);
        } else {
            APPLYING.remove();
        }
    }

    public static void runReverting(Runnable action) {
        REVERTING.set(true);
        try {
            action.run();
        } finally {
            REVERTING.remove();
        }
    }

    public static boolean canAffordNextPeriod(MinecraftServer server, Team team) {
        TeamPendingState pendingState = FtbHookSavedData.get(server).getPendingState(team.getTeamId());
        return canAffordNextPeriod(server, team, pendingState);
    }

    public static boolean canAffordNextPeriod(MinecraftServer server, Team team, TeamPendingState pendingState) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        int chunkCount = chunkData.getClaimedChunks().size();
        int forceLoadCount = ProtectionPricing.countEffectiveForceLoads(chunkData, pendingState);
        if (chunkCount <= 0 && forceLoadCount <= 0) {
            return true;
        }
        MoneyValue cost = ProtectionPricing.calculateTotalUpkeepCost(team, pendingState.pendingProperties(), chunkCount, forceLoadCount);
        if (cost.isEmpty()) {
            return true;
        }
        IBankAccount account = BankAccountHelper.getAccountForTeam(server, team);
        return account.getMoneyStorage().containsValue(cost);
    }

    public static void enforceInsufficientFunds(MinecraftServer server, Team team) {
        if (isReverting()) {
            return;
        }
        REVERTING.set(true);
        try {
            PendingChangeService.removeAllForceLoads(server, team);
            ProtectionPricing.applyMinimumProtections(server, team);
            FtbHookSavedData.get(server).setProtectionLocked(team.getTeamId(), true);
            notifyTeam(server, team, "message.lc_ftb_hook.protection_locked");
        } finally {
            REVERTING.set(false);
        }
    }

    public static void tryUnlock(MinecraftServer server, Team team) {
        FtbHookSavedData data = FtbHookSavedData.get(server);
        if (!data.isProtectionLocked(team.getTeamId())) {
            return;
        }
        if (canAffordNextPeriod(server, team)) {
            data.setProtectionLocked(team.getTeamId(), false);
            notifyTeam(server, team, "message.lc_ftb_hook.protection_unlocked");
        }
    }

    public static void notifyTeam(MinecraftServer server, Team team, String messageKey) {
        Component message = Component.translatable(messageKey);
        notifyTeam(server, team, message);
    }

    public static void notifyTeam(MinecraftServer server, Team team, Component message) {
        for (ServerPlayer member : team.getOnlineMembers()) {
            member.displayClientMessage(message, false);
        }
    }

    public static void notifyTeamManagers(MinecraftServer server, Team team, Component message) {
        for (ServerPlayer member : team.getOnlineMembers()) {
            if (!team.isPartyTeam() || team.getRankForPlayer(member.getUUID()).isOfficerOrBetter()) {
                member.displayClientMessage(message, false);
            }
        }
    }
}
