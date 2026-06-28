package dev.malik.lcftbhook.teams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.service.TeamDeletionService;
import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import io.github.lightman314.lightmanscurrency.api.teams.TeamAPI;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TeamLinkRegistry {
    private TeamLinkRegistry() {
    }

    @Nullable
    public static FtbHookSavedData.TeamLinkEntry findByLcTeamId(MinecraftServer server, long lcTeamId) {
        if (lcTeamId <= 0) {
            return null;
        }
        return FtbHookSavedData.get(server).findByLcTeamId(lcTeamId);
    }

    @Nullable
    public static FtbHookSavedData.TeamLinkEntry findByFtbTeamId(MinecraftServer server, UUID ftbTeamId) {
        return FtbHookSavedData.get(server).get(ftbTeamId);
    }

    /** Any valid FTB team (solo player team or party) stored under this ID. */
    @Nullable
    public static Team findStoredTeam(MinecraftServer server, UUID ftbTeamId) {
        if (server == null || ftbTeamId == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        return FTBTeamsAPI.api().getManager().getTeamByID(ftbTeamId)
                .filter(Team::isValid)
                .orElse(null);
    }

    @Nullable
    public static Team findFtbParty(MinecraftServer server, UUID ftbTeamId) {
        Team team = findStoredTeam(server, ftbTeamId);
        return team != null && team.isPartyTeam() ? team : null;
    }

    public static boolean isFtbPartyInUse(MinecraftServer server, Team party) {
        if (server == null || party == null || !party.isPartyTeam() || !party.isValid()) {
            return false;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return false;
        }

        TeamManager manager = FTBTeamsAPI.api().getManager();
        for (UUID memberId : party.getMembers()) {
            if (manager.getTeamForPlayerID(memberId)
                    .map(activeTeam -> activeTeam.getId().equals(party.getId()))
                    .orElse(false)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Team findActiveFtbParty(MinecraftServer server, UUID ftbTeamId) {
        Team party = findFtbParty(server, ftbTeamId);
        return party != null && isFtbPartyInUse(server, party) ? party : null;
    }

    @Nullable
    public static ITeam findLcTeam(long lcTeamId) {
        if (lcTeamId <= 0) {
            return null;
        }
        return TeamAPI.getApi().GetTeam(false, lcTeamId);
    }

    public static boolean isOrphanedLink(MinecraftServer server, FtbHookSavedData.TeamLinkEntry entry) {
        return findActiveFtbParty(server, entry.ftbTeamId()) == null;
    }

    public static boolean isLinkedToActiveFtbParty(MinecraftServer server, long lcTeamId) {
        FtbHookSavedData.TeamLinkEntry entry = findByLcTeamId(server, lcTeamId);
        if (entry == null) {
            return false;
        }
        return findActiveFtbParty(server, entry.ftbTeamId()) != null;
    }

    public static boolean shouldBlockLcTeamRemoval(MinecraftServer server, long lcTeamId) {
        refreshLcTeamLink(server, lcTeamId);
        return isLinkedToActiveFtbParty(server, lcTeamId);
    }

    public static boolean shouldBlockLcTeamRoleChanges(MinecraftServer server, long lcTeamId) {
        refreshLcTeamLink(server, lcTeamId);
        return isLinkedToActiveFtbParty(server, lcTeamId);
    }

    public static void refreshLcTeamLink(MinecraftServer server, long lcTeamId) {
        FtbHookSavedData.TeamLinkEntry entry = findByLcTeamId(server, lcTeamId);
        if (entry == null) {
            return;
        }

        Team ftbParty = findFtbParty(server, entry.ftbTeamId());
        if (ftbParty == null || !isFtbPartyInUse(server, ftbParty)) {
            LCFtbHook.LOGGER.info(
                    "Clearing stale FTB link for LC team {} (party {} is not in use)",
                    lcTeamId,
                    entry.ftbTeamId()
            );
            clearLcTeamLink(server, entry.ftbTeamId());
        }
    }

    public static void unlinkLcTeam(MinecraftServer server, long lcTeamId) {
        FtbHookSavedData data = FtbHookSavedData.get(server);
        FtbHookSavedData.TeamLinkEntry entry = data.findByLcTeamId(lcTeamId);
        if (entry != null && data.clearLcTeamLink(entry.ftbTeamId())) {
            LCFtbHook.LOGGER.info(
                    "Unlinked LC team {} from FTB team {}",
                    lcTeamId,
                    entry.ftbTeamId()
            );
        }
    }

    public static void unlinkFtbParty(MinecraftServer server, UUID ftbTeamId) {
        if (FtbHookSavedData.get(server).clearLcTeamLink(ftbTeamId)) {
            LCFtbHook.LOGGER.info("Removed LC link for FTB team {}", ftbTeamId);
        }
    }

    public static void clearLcTeamLink(MinecraftServer server, UUID ftbTeamId) {
        if (FtbHookSavedData.get(server).clearLcTeamLink(ftbTeamId)) {
            LCFtbHook.LOGGER.info("Cleared LC link for FTB team {}", ftbTeamId);
        }
    }

    public static int reconcile(MinecraftServer server) {
        if (server == null || LcTeamAccess.cache() == null) {
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return 0;
        }

        FtbHookSavedData data = FtbHookSavedData.get(server);
        int changes = 0;

        List<FtbHookSavedData.TeamLinkEntry> links = new ArrayList<>(data.getAllLinks());
        for (FtbHookSavedData.TeamLinkEntry entry : links) {
            Team ftbTeam = findStoredTeam(server, entry.ftbTeamId());

            if (ftbTeam == null) {
                // TeamManagerEvent.CREATED fires before FTB Teams finishes load().
                if (FTBTeamsAPI.api().getManager().getTeams().isEmpty()) {
                    continue;
                }
                // Use the central deletion service so wars, pending state, and the
                // LC account are all cleaned up atomically — same as the event path.
                TeamDeletionService.purge(server, entry.ftbTeamId(), null);
                changes++;
                LCFtbHook.LOGGER.info(
                        "Reconcile: purged stale hook data for deleted FTB team {} (LC team {})",
                        entry.ftbTeamId(),
                        entry.lcTeamId()
                );
                continue;
            }

            if (entry.lcTeamId() > 0 && findLcTeam(entry.lcTeamId()) == null) {
                if (data.clearLcTeamLink(entry.ftbTeamId())) {
                    changes++;
                    LCFtbHook.LOGGER.info(
                            "Cleared stale LC link {} for FTB team {}",
                            entry.lcTeamId(),
                            entry.ftbTeamId()
                    );
                }
                continue;
            }

            if (ftbTeam.isPartyTeam() && entry.lcTeamId() > 0 && ftbTeam.getMembers().isEmpty()) {
                if (data.clearLcTeamLink(entry.ftbTeamId())) {
                    changes++;
                    LCFtbHook.LOGGER.info(
                            "Cleared LC link for empty FTB party {}",
                            entry.ftbTeamId()
                    );
                }
            }
        }

        for (Team team : FtbTeamCatalog.activeParties(server)) {
            LcTeamSyncService.ensureLinked(server, team);
        }

        return changes;
    }
}
