package dev.malik.lcftbhook.handler;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.PlayerChangedTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerJoinedPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerLeftPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerLoggedInAfterTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerTransferredTeamOwnershipEvent;
import dev.ftb.mods.ftbteams.api.event.TeamCreatedEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.service.ClaimVisibilityService;
import dev.malik.lcftbhook.service.PartyDisbandSettlementService;
import dev.malik.lcftbhook.service.TeamDeletionService;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.service.WarStateSync;
import dev.malik.lcftbhook.teams.FtbTeamCatalog;
import dev.malik.lcftbhook.teams.LcTeamSyncService;
import dev.malik.lcftbhook.teams.TeamLinkRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class TeamLifecycleHandler {
    public TeamLifecycleHandler() {
        TeamEvent.CREATED.register(this::onTeamCreated);
        TeamEvent.LOADED.register(this::onTeamLoaded);
        TeamEvent.DELETED.register(this::onTeamDeleted);
        TeamEvent.PLAYER_JOINED_PARTY.register(this::onPlayerJoinedParty);
        TeamEvent.PLAYER_LEFT_PARTY.register(this::onPlayerLeftParty);
        TeamEvent.OWNERSHIP_TRANSFERRED.register(this::onOwnershipTransferred);
        TeamEvent.PLAYER_CHANGED.register(this::onPlayerChanged);
        TeamEvent.PLAYER_LOGGED_IN.register(this::onPlayerLoggedInAfterTeam);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        reconcileTeams(event.getServer());
        // ServerAccountHelper.get() self-heals a stale "Unknown" owner name,
        // but only when OUR code happens to touch the account (a sink
        // deposit, or /lc_ftb_hook server_account) - an account created (or
        // last saved) before that fix existed would keep showing "Unknown"
        // in LC's own UI indefinitely if nobody happened to trigger either
        // first. Touching it once here guarantees it's already correct the
        // moment the server is up, not dependent on some other trigger
        // happening first.
        //
        // Deferred one tick (server.execute) rather than called directly
        // here - LC's own bank data (CustomSaveData "lightmanscurrency:bank_accounts")
        // isn't marked ready yet at the exact moment ServerStartedEvent
        // fires (confirmed live: LC logs "Attempted to get custom data
        // ... before the server started!" with a full stack trace pointing
        // straight at this call when it runs synchronously here). Harmless
        // in practice (the account access just silently no-ops that one
        // attempt), but noisy and fragile to rely on - by the next tick
        // every ServerStartedEvent listener, LC's own included, has
        // finished.
        event.getServer().execute(dev.malik.lcftbhook.bank.ServerAccountHelper::get);
    }

    private void onTeamCreated(TeamCreatedEvent event) {
        ClaimVisibilityService.ensurePublic(event.getTeam());
        ensureAccount(event.getTeam());
    }

    private void onTeamLoaded(TeamEvent event) {
        ClaimVisibilityService.ensurePublic(event.getTeam());
        ensureAccount(event.getTeam());
    }

    private void onTeamDeleted(TeamEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            TeamDeletionService.purge(server, event.getTeam());
        }
    }

    private void onPlayerJoinedParty(PlayerJoinedPartyTeamEvent event) {
        ensureAccount(event.getTeam());
    }

    private void onPlayerLeftParty(PlayerLeftPartyTeamEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && event.getTeamDeleted()) {
            PartyDisbandSettlementService.settle(server, event.getTeam());
            if (event.getPlayer() != null) {
                WarStateSync.syncToPlayer(event.getPlayer());
            }
        }

        if (!event.getTeamDeleted()) {
            ensureAccount(event.getTeam());
        }
        ensureAccount(event.getPlayerTeam());
    }

    private void onOwnershipTransferred(PlayerTransferredTeamOwnershipEvent event) {
        ensureAccount(event.getTeam());
    }

    private void onPlayerChanged(PlayerChangedTeamEvent event) {
        ensureAccount(event.getTeam());
        event.getPreviousTeam().ifPresent(this::ensureAccount);
    }

    private void onPlayerLoggedInAfterTeam(PlayerLoggedInAfterTeamEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            TeamLinkRegistry.reconcile(server);
        }
        ensureAccount(event.getTeam());
        if (event.getPlayer() != null) {
            ServerPlayer player = event.getPlayer();
            ClaimPriceSync.syncToPlayer(player);
            PendingStateSync.syncToPlayer(player);
            dev.malik.lcftbhook.service.RegionService.syncRegionsToPlayer(player);
            dev.malik.lcftbhook.service.RegionService.syncMembershipToPlayer(player);
            dev.malik.lcftbhook.service.RegionService.syncPublicRegionsToPlayer(player);
            WarStateSync.syncToPlayer(player);
            // Defer once: login sync can race the client's play handler registration
            // after a full server restart + reconnect.
            server.execute(() -> {
                dev.malik.lcftbhook.service.RegionService.syncRegionsToPlayer(player);
                dev.malik.lcftbhook.service.RegionService.syncMembershipToPlayer(player);
                dev.malik.lcftbhook.service.RegionService.syncPublicRegionsToPlayer(player);
            });
        }
    }

    private void reconcileTeams(MinecraftServer server) {
        TeamLinkRegistry.reconcile(server);
    }

    private void ensureAccount(Team team) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || team == null || !team.isValid()) {
            return;
        }
        BankAccountHelper.ensurePartyAccountExists(server, team);
    }
}
