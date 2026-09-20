package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.teams.MarketplaceTeamProperties;

import javax.annotation.Nullable;
import java.util.UUID;

/** Whether the local player could actually buy a listed chunk (not already theirs, not blocked by the owning Region's buyer-access setting). */
public final class BuyableChunkChecker {
    private BuyableChunkChecker() {
    }

    public static boolean isBuyable(ChunkOwnership ownership, @Nullable Team team, String chunkKey, @Nullable UUID localPlayer) {
        if (!ownership.isListed() || localPlayer == null) {
            return false;
        }
        if (localPlayer.equals(ownership.privateOwner())) {
            return false;
        }
        if (team != null && !hasAccess(team, chunkKey, localPlayer)) {
            return false;
        }
        return true;
    }

    private static boolean hasAccess(Team team, String chunkKey, UUID player) {
        UUID regionId = ClientRegionMembership.rawRegionOf(chunkKey);
        Region region = regionId != null ? ClientPublicRegions.get(regionId) : ClientPublicRegions.getDefaultFor(team.getTeamId());
        String access = region != null ? region.buyerAccess() : MarketplaceTeamProperties.ACCESS_ALL;
        var rank = team.getRankForPlayer(player);
        return switch (access) {
            case MarketplaceTeamProperties.ACCESS_TEAM -> rank.isMemberOrBetter();
            case MarketplaceTeamProperties.ACCESS_ALLIES -> rank.isAllyOrBetter();
            default -> true;
        };
    }
}
