package dev.malik.lcftbhook.handler;

import dev.architectury.event.CompoundEventResult;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.service.ProtectionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class ForceLoadHandler {
    public ForceLoadHandler() {
        ClaimedChunkEvent.BEFORE_LOAD.register(this::beforeLoad);
        ClaimedChunkEvent.BEFORE_UNLOAD.register(this::beforeUnload);
    }

    private CompoundEventResult<ClaimResult> beforeLoad(CommandSourceStack source, ClaimedChunk chunk) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return CompoundEventResult.pass();
        }

        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return CompoundEventResult.pass();
        }

        if (!BankAccountHelper.canPurchaseForTeam(team, player.getUUID())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_ftb_hook.claim_rank_denied"));
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return CompoundEventResult.pass();
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        if (savedData.isProtectionLocked(team.getTeamId())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_ftb_hook.protection_locked_change"));
        }

        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());
        String chunkKey = ChunkPosKey.encode(chunk.getPos());

        // Toggle off a queued load, or undo a queued unload (same idea as cycling
        // a protection setting back while a change is pending).
        if (pendingState.isPendingForceLoad(chunkKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingForceLoad(chunkKey));
            PendingStateSync.syncTeam(server, team);
            notifyForceLoadPendingCancelled(team);
            return CompoundEventResult.interruptFalse(ClaimResult.success());
        }
        if (pendingState.isPendingForceUnload(chunkKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingForceUnload(chunkKey));
            PendingStateSync.syncTeam(server, team);
            notifyForceLoadPendingCancelled(team);
            return CompoundEventResult.interruptFalse(ClaimResult.success());
        }

        if (chunk.isForceLoaded()) {
            return CompoundEventResult.pass();
        }

        TeamPendingState updated = pendingState.withPendingForceLoad(chunkKey);
        if (!ProtectionService.canAffordNextPeriod(server, team, updated)) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_ftb_hook.insufficient_funds_protection"));
        }

        savedData.setPendingState(team.getTeamId(), updated);
        dev.malik.lcftbhook.LCFtbHook.LOGGER.info("[PendingDebug] Team {}: force-load queued for chunk {}",
                team.getShortName(), chunkKey);
        PendingStateSync.syncTeam(server, team);
        notifyForceLoadPending(team);
        return CompoundEventResult.interruptFalse(ClaimResult.success());
    }

    private CompoundEventResult<ClaimResult> beforeUnload(CommandSourceStack source, ClaimedChunk chunk) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return CompoundEventResult.pass();
        }

        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return CompoundEventResult.pass();
        }

        if (!BankAccountHelper.canPurchaseForTeam(team, player.getUUID())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_ftb_hook.claim_rank_denied"));
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return CompoundEventResult.pass();
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());
        String chunkKey = ChunkPosKey.encode(chunk.getPos());

        if (pendingState.isPendingForceUnload(chunkKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingForceUnload(chunkKey));
            PendingStateSync.syncTeam(server, team);
            notifyForceLoadPendingCancelled(team);
            return CompoundEventResult.interruptFalse(ClaimResult.success());
        }

        if (pendingState.isPendingForceLoad(chunkKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingForceLoad(chunkKey));
            PendingStateSync.syncTeam(server, team);
            notifyForceLoadPendingCancelled(team);
            return CompoundEventResult.interruptFalse(ClaimResult.success());
        }

        if (!chunk.isForceLoaded()) {
            return CompoundEventResult.pass();
        }

        TeamPendingState updated = pendingState.withPendingForceUnload(chunkKey);
        savedData.setPendingState(team.getTeamId(), updated);
        PendingStateSync.syncTeam(server, team);
        notifyForceLoadPending(team);
        return CompoundEventResult.interruptFalse(ClaimResult.success());
    }

    private static void notifyForceLoadPending(Team team) {
        notifyTeam(team, "message.lc_ftb_hook.forceload_change_pending");
    }

    private static void notifyForceLoadPendingCancelled(Team team) {
        notifyTeam(team, "message.lc_ftb_hook.forceload_pending_cancelled");
    }

    private static void notifyTeam(Team team, String messageKey) {
        Component message = Component.translatable(messageKey);
        for (ServerPlayer member : team.getOnlineMembers()) {
            member.displayClientMessage(message, false);
        }
    }
}
