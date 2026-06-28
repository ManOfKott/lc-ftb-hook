package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.teams.LcTeamSyncService;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Central point for all cleanup that must happen when an FTB team ceases to
 * exist. Every code path that handles team deletion — event listeners,
 * reconcile, and the disband settlement — must go through here so nothing
 * is left behind.
 *
 * <p>Financial settlement (chunk refunds, balance transfer to owner) is handled
 * separately by {@link PartyDisbandSettlementService} <em>before</em> calling
 * this service, because it requires the team object and its members to still
 * be resolvable. This service only does the structural cleanup that works even
 * when the FTB team object is no longer available.
 */
public final class TeamDeletionService {
    private TeamDeletionService() {
    }

    /**
     * Full structural cleanup for a deleted team. Safe to call even when the
     * FTB {@link Team} object is no longer available (pass {@code null}).
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Dissolve all active and pending war links, refresh partner upkeep.</li>
     *   <li>Clear pending protection / force-load / war state.</li>
     *   <li>Delete the linked LC bank-team account (parties only).</li>
     *   <li>Remove the SavedData entry for this team.</li>
     * </ol>
     */
    public static void purge(MinecraftServer server, UUID teamId, @Nullable Team teamForLcCleanup) {
        if (server == null || teamId == null) {
            return;
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);

        // 1. War cleanup — dissolves outgoing and incoming war refs and syncs partners.
        WarService.cleanupTeamWars(server, teamId);

        // 2. Pending state
        if (!savedData.getPendingState(teamId).isEmpty()) {
            savedData.setPendingState(teamId, new TeamPendingState());
        }

        // 3. LC bank-team deletion — delegate to LcTeamSyncService which has
        //    package access to LcTeamAccess. Passing null when no Team object is
        //    available (reconcile path) is handled gracefully inside.
        if (teamForLcCleanup != null) {
            LcTeamSyncService.onTeamDeleted(server, teamForLcCleanup);
        } else {
            // No Team object — remove the SavedData link directly so the orphaned
            // LC account reference doesn't accumulate. The LC team itself is left
            // in place; a future reconcile of the LC side will clean it up.
            if (savedData.removeLink(teamId) != null) {
                LCFtbHook.LOGGER.info("Reconcile: removed stale SavedData entry for team {}", teamId);
            }
        }
    }

    /**
     * Convenience overload used from event handlers where the {@link Team}
     * object is still available.
     */
    public static void purge(MinecraftServer server, Team team) {
        purge(server, team.getTeamId(), team);
    }
}
