package dev.malik.lcftbhook.network;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.service.UpkeepSummaryService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PendingStateSync {
    private PendingStateSync() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            PacketDistributor.sendToPlayer(player, SyncPendingStatePayload.EMPTY);
            return;
        }
        PacketDistributor.sendToPlayer(player, createPayload(player.server, team));
        PacketDistributor.sendToPlayer(player, new SyncUpkeepSummaryPayload(UpkeepSummaryService.compute(player.server, team)));
    }

    public static void syncTeam(MinecraftServer server, Team team) {
        SyncPendingStatePayload payload = createPayload(server, team);
        SyncUpkeepSummaryPayload upkeepPayload = new SyncUpkeepSummaryPayload(UpkeepSummaryService.compute(server, team));
        for (ServerPlayer member : team.getOnlineMembers()) {
            PacketDistributor.sendToPlayer(member, payload);
            PacketDistributor.sendToPlayer(member, upkeepPayload);
        }
    }

    public static SyncPendingStatePayload createPayload(MinecraftServer server, Team team) {
        TeamPendingState pendingState = FtbHookSavedData.get(server).getPendingState(team.getTeamId());
        return new SyncPendingStatePayload(
                pendingState.pendingProperties(),
                pendingState.pendingForceLoads(),
                pendingState.pendingForceUnloads(),
                pendingState.pendingRegionAssignments()
        );
    }
}
