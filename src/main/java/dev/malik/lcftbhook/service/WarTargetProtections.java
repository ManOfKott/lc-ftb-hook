package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import net.minecraft.server.MinecraftServer;

/**
 * Live protections on a war opponent, aggregated across every one of their
 * regions. A team is vulnerable in a given aspect the moment ANY of its
 * regions is unprotected there (even if others are protected).
 */
public record WarTargetProtections(
        boolean blockEditProtected,
        boolean explosionProtected,
        boolean pvpProtected
) {
    public boolean hasWarVulnerability() {
        return !blockEditProtected || !explosionProtected || !pvpProtected;
    }

    public static WarTargetProtections live(MinecraftServer server, Team team) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        boolean blockEditProtected = true;
        boolean explosionProtected = true;
        boolean pvpProtected = true;
        for (Region region : savedData.getRegions(team.getTeamId()).values()) {
            if (region.isAtMinimum(ProtectionProperty.BLOCK_EDIT_MODE)) {
                blockEditProtected = false;
            }
            if (region.isAtMinimum(ProtectionProperty.ALLOW_EXPLOSIONS)) {
                explosionProtected = false;
            }
            if (region.isAtMinimum(ProtectionProperty.ALLOW_PVP)) {
                pvpProtected = false;
            }
        }
        return new WarTargetProtections(blockEditProtected, explosionProtected, pvpProtected);
    }
}
