package dev.malik.lcftbhook.network;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PendingStateSync {
    private PendingStateSync() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            LCFtbHook.LOGGER.info("[PendingDebug] syncToPlayer {}: team manager not loaded", player.getScoreboardName());
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            LCFtbHook.LOGGER.info("[PendingDebug] syncToPlayer {}: no team, sending EMPTY", player.getScoreboardName());
            PacketDistributor.sendToPlayer(player, SyncPendingStatePayload.EMPTY);
            return;
        }
        SyncPendingStatePayload payload = createPayload(player.server, team);
        LCFtbHook.LOGGER.info("[PendingDebug] syncToPlayer {}: team={}, properties={}, forceLoads={}, forceUnloads={}",
                player.getScoreboardName(), team.getShortName(),
                payload.pendingProperties(), payload.pendingForceLoads(), payload.pendingForceUnloads());
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void syncTeam(MinecraftServer server, Team team) {
        SyncPendingStatePayload payload = createPayload(server, team);
        LCFtbHook.LOGGER.info("[PendingDebug] syncTeam {}: properties={}, forceLoads={}, forceUnloads={}, recipients={}",
                team.getShortName(), payload.pendingProperties(), payload.pendingForceLoads(),
                payload.pendingForceUnloads(), team.getOnlineMembers().size());
        for (ServerPlayer member : team.getOnlineMembers()) {
            PacketDistributor.sendToPlayer(member, payload);
        }
    }

    public static SyncPendingStatePayload createPayload(MinecraftServer server, Team team) {
        TeamPendingState pendingState = FtbHookSavedData.get(server).getPendingState(team.getTeamId());
        return new SyncPendingStatePayload(
                pendingState.pendingProperties(),
                pendingState.pendingForceLoads(),
                pendingState.pendingForceUnloads()
        );
    }
}
