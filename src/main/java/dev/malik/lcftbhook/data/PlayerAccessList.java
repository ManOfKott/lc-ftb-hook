package dev.malik.lcftbhook.data;

import java.util.Set;
import java.util.UUID;

/**
 * Per-property custom player list for a privately-owned chunk, layered on
 * top of the base access check (see {@code ChunkTeamDataRegionPrivacyMixin}):
 * a whitelist ADDS access for the listed players even if they wouldn't
 * otherwise qualify; a blacklist REMOVES access for the listed players even
 * if they would otherwise qualify. Only takes effect while the property's
 * effective value isn't Public (i.e. Allies, Team, or Private) - meaningless
 * (and not consulted) at Public.
 */
public record PlayerAccessList(boolean whitelist, Set<UUID> players) {
    public static final PlayerAccessList EMPTY = new PlayerAccessList(true, Set.of());

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public boolean apply(boolean baseAllowed, UUID player) {
        boolean inList = players.contains(player);
        return whitelist ? (baseAllowed || inList) : (baseAllowed && !inList);
    }
}
