package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.data.Region;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * Every visible team's regions (name + protection values only, no chunk
 * counts or prices - see {@link dev.malik.lcftbhook.network.SyncPublicRegionsPayload}),
 * so hovering ANY team's chunk shows that team's own actual protection
 * status rather than the viewer's own team's data.
 */
public final class ClientPublicRegions {
    private static Map<UUID, Region> regionsById = Map.of();
    private static Map<UUID, UUID> defaultRegionByTeam = Map.of();

    private ClientPublicRegions() {
    }

    public static void update(Map<UUID, Region> regions, Map<UUID, UUID> defaults) {
        regionsById = Map.copyOf(regions);
        defaultRegionByTeam = Map.copyOf(defaults);
    }

    @Nullable
    public static Region get(UUID regionId) {
        return regionsById.get(regionId);
    }

    @Nullable
    public static Region getDefaultFor(UUID teamId) {
        UUID defaultId = defaultRegionByTeam.get(teamId);
        return defaultId != null ? regionsById.get(defaultId) : null;
    }
}
