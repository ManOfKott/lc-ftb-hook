package dev.malik.lcftbhook.teams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
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

    @Nullable
    public static Team findFtbParty(MinecraftServer server, UUID ftbTeamId) {
        if (server == null || ftbTeamId == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        return FTBTeamsAPI.api().getManager().getTeamByID(ftbTeamId)
                .filter(team -> team.isPartyTeam() && team.isValid())
                .orElse(null);
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
            unlinkLcTeam(server, lcTeamId);
        }
    }

    public static void unlinkLcTeam(MinecraftServer server, long lcTeamId) {
        FtbHookSavedData data = FtbHookSavedData.get(server);
        FtbHookSavedData.TeamLinkEntry entry = data.findByLcTeamId(lcTeamId);
        if (entry != null) {
            data.removeLink(entry.ftbTeamId());
            LCFtbHook.LOGGER.info(
                    "Unlinked LC team {} from FTB party {}",
                    lcTeamId,
                    entry.ftbTeamId()
            );
        }
    }

    public static void unlinkFtbParty(MinecraftServer server, UUID ftbTeamId) {
        FtbHookSavedData data = FtbHookSavedData.get(server);
        if (data.removeLink(ftbTeamId) != null) {
            LCFtbHook.LOGGER.info("Removed FTB party link {}", ftbTeamId);
        }
    }

    public static int reconcile(MinecraftServer server) {
        if (server == null || LcTeamAccess.cache() == null) {
            return 0;
        }

        FtbHookSavedData data = FtbHookSavedData.get(server);
        int changes = 0;

        List<FtbHookSavedData.TeamLinkEntry> links = new ArrayList<>(data.getAllLinks());
        for (FtbHookSavedData.TeamLinkEntry entry : links) {
            ITeam lcTeam = findLcTeam(entry.lcTeamId());
            Team ftbParty = findFtbParty(server, entry.ftbTeamId());

            if (entry.lcTeamId() > 0 && lcTeam == null) {
                data.removeLink(entry.ftbTeamId());
                changes++;
                LCFtbHook.LOGGER.info(
                        "Removed stale link for deleted LC team {} (FTB party {})",
                        entry.lcTeamId(),
                        entry.ftbTeamId()
                );
                continue;
            }

            if (ftbParty == null) {
                data.removeLink(entry.ftbTeamId());
                changes++;
                LCFtbHook.LOGGER.info(
                        "Unlinked orphaned LC team {} (FTB party {} no longer exists)",
                        entry.lcTeamId(),
                        entry.ftbTeamId()
                );
                continue;
            }

            if (!isFtbPartyInUse(server, ftbParty)) {
                data.removeLink(entry.ftbTeamId());
                changes++;
                LCFtbHook.LOGGER.info(
                        "Unlinked abandoned LC team {} (FTB party {} has no active members)",
                        entry.lcTeamId(),
                        entry.ftbTeamId()
                );
            }
        }

        if (FTBTeamsAPI.api().isManagerLoaded()) {
            for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
                if (team.isPartyTeam() && team.isValid() && isFtbPartyInUse(server, team)) {
                    LcTeamSyncService.ensureLinked(server, team);
                }
            }
        }

        return changes;
    }
}
