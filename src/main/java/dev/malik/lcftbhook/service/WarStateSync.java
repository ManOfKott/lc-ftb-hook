package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.network.SyncWarStatePayload;
import dev.malik.lcftbhook.network.WarTeamEntry;
import dev.malik.lcftbhook.teams.FtbTeamCatalog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WarStateSync {
    private WarStateSync() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, createPayload(player.server, team, player.getUUID()));
    }

    public static void syncToTeam(MinecraftServer server, UUID teamId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = FtbTeamCatalog.resolve(server, teamId);
        if (team == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Team playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
            if (playerTeam != null && playerTeam.getTeamId().equals(teamId)) {
                PacketDistributor.sendToPlayer(player, createPayload(server, team, player.getUUID()));
            }
        }
    }

    /**
     * War costs are derived from each team's live upkeep (including queued
     * protection changes). Push fresh values to every team whose war UI or
     * next upkeep charge depends on the changed team.
     */
    public static void onUpkeepFactorsChanged(MinecraftServer server, Team changedTeam) {
        if (!WarService.isEnabled()) {
            return;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded() || !changedTeam.isValid()) {
            return;
        }

        UUID teamId = changedTeam.getTeamId();
        Set<UUID> synced = new HashSet<>();
        syncToTeam(server, teamId);
        synced.add(teamId);

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (entry.warTargets().contains(teamId) && synced.add(entry.ftbTeamId())) {
                syncToTeam(server, entry.ftbTeamId());
            }
        }

        if (!WarService.isClaimTeam(server, changedTeam)) {
            return;
        }
        for (Team team : FtbTeamCatalog.trackedTeams(server)) {
            if (team.getTeamId().equals(teamId)) {
                continue;
            }
            if (!WarService.isClaimTeam(server, team) || team.getOnlineMembers().isEmpty()) {
                continue;
            }
            if (synced.add(team.getTeamId())) {
                syncToTeam(server, team.getTeamId());
            }
        }
    }

    private static SyncWarStatePayload createPayload(MinecraftServer server, Team team, UUID viewerId) {
        WarService.WarCostBreakdown costs = WarService.calculateWarCosts(server, team);
        return new SyncWarStatePayload(
                costs.baseUpkeepCopper(),
                costs.incomingWarCopper(),
                costs.outgoingWarCopper(),
                WarService.warMultiplier(),
                toEntries(WarService.buildIncomingViews(server, team)),
                toEntries(WarService.buildOutgoingViews(server, team)),
                toEntries(WarService.buildAvailableTargets(server, team)),
                viewerId != null && WarService.canManageWar(team, viewerId)
        );
    }

    private static List<WarTeamEntry> toEntries(List<WarService.WarTeamView> views) {
        return views.stream()
                .map(view -> new WarTeamEntry(
                        view.teamId(),
                        view.displayName(),
                        view.targetBaseUpkeepCopper(),
                        view.warCostCopper(),
                        view.status(),
                        view.opponentPendingDeclareOnViewer(),
                        view.blockEditProtected(),
                        view.explosionProtected(),
                        view.pvpProtected()
                ))
                .toList();
    }
}
