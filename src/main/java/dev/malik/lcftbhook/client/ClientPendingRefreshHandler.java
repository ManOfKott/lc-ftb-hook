package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.malik.lcftbhook.network.RequestClaimPricesPayload;
import dev.malik.lcftbhook.network.RequestPendingStatePayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientPendingRefreshHandler {
    public ClientPendingRefreshHandler() {
        TeamEvent.CLIENT_PROPERTIES_CHANGED.register(event -> {
            PacketDistributor.sendToServer(new RequestClaimPricesPayload());
            PacketDistributor.sendToServer(new RequestPendingStatePayload());

            // Only push values into an open properties screen when the change
            // concerns the player's own team; properties of other teams also
            // sync to all clients.
            if (isSelfTeam(event.getTeam())) {
                PendingStateUiRefresh.syncOpenScreenValues(event.getTeam());
            }
        });
    }

    private static boolean isSelfTeam(Team team) {
        if (!FTBTeamsAPI.api().isClientManagerLoaded()) {
            return false;
        }
        Team selfTeam = FTBTeamsAPI.api().getClientManager().selfTeam();
        return selfTeam != null && selfTeam.getTeamId().equals(team.getTeamId());
    }
}
