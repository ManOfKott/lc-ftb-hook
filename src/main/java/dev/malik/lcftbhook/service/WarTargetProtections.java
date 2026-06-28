package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;

/**
 * Live build-chunk protections on a war opponent. A team is vulnerable when
 * block edit is public, explosions are allowed, or PvP is allowed.
 */
public record WarTargetProtections(
        boolean blockEditProtected,
        boolean explosionProtected,
        boolean pvpProtected
) {
    public boolean hasWarVulnerability() {
        return !blockEditProtected || !explosionProtected || !pvpProtected;
    }

    public static WarTargetProtections live(Team team) {
        return new WarTargetProtections(
                team.getProperty(FTBChunksProperties.BLOCK_EDIT_MODE) != PrivacyMode.PUBLIC,
                !team.getProperty(FTBChunksProperties.ALLOW_EXPLOSIONS),
                !team.getProperty(FTBChunksProperties.ALLOW_PVP)
        );
    }

    public static WarTargetProtections allProtected() {
        return new WarTargetProtections(true, true, true);
    }
}
