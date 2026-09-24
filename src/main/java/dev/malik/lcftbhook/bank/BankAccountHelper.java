package dev.malik.lcftbhook.bank;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.malik.lcftbhook.teams.LcTeamSyncService;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public final class BankAccountHelper {
    private BankAccountHelper() {
    }

    public static IBankAccount getAccountForTeam(MinecraftServer server, Team team) {
        if (team.isPartyTeam()) {
            IBankAccount account = LcTeamSyncService.getBankAccount(server, team);
            if (account == null) {
                throw new IllegalStateException("Missing LC team bank account for FTB party " + team.getId());
            }
            return account;
        }
        IBankAccount account = PlayerBankReference.of(team.getId()).get();
        if (account == null) {
            throw new IllegalStateException("Missing personal bank account for player team " + team.getId());
        }
        return account;
    }

    public static IBankAccount getAccountForPlayer(MinecraftServer server, ServerPlayer player) {
        Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
        if (team.isPresent()) {
            return getAccountForTeam(server, team.get());
        }
        IBankAccount account = PlayerBankReference.of(player.getUUID()).get();
        if (account == null) {
            throw new IllegalStateException("Missing personal bank account for player " + player.getUUID());
        }
        return account;
    }

    /**
     * Mirrors whatever the team's own Lightman's Currency bank account access
     * level is set to (owner-only by default, but the owner can open it up to
     * admins or all members via LC's own team management screen) - not a
     * separate, fixed officer-or-better rule of our own.
     */
    public static boolean canPurchaseForTeam(MinecraftServer server, Team team, UUID playerId) {
        if (!team.isPartyTeam()) {
            return true;
        }
        LcTeamSyncService.ensureLinked(server, team);
        ITeam lcTeam = LcTeamSyncService.getLcTeam(server, team.getId());
        if (lcTeam == null) {
            // Not linked yet (e.g. LC team creation hasn't happened for this
            // party) - fall back to the old, safe default until it is.
            TeamRank rank = team.getRankForPlayer(playerId);
            return rank.isOfficerOrBetter();
        }
        int limit = lcTeam.getBankLimit();
        if (limit < 1) {
            return lcTeam.isMember(playerId);
        } else if (limit < 2) {
            return lcTeam.isAdmin(playerId);
        }
        return lcTeam.isOwner(playerId);
    }

    public static void ensurePartyAccountExists(MinecraftServer server, Team team) {
        if (!team.isPartyTeam() || !team.isValid()) {
            return;
        }
        LcTeamSyncService.ensureLinked(server, team);
    }

}
