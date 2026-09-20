package dev.malik.lcftbhook.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A named, ordered subdivision of a team's claimed chunks ("chunk set" in
 * the design discussion, "Region" in all player-facing text). Every team
 * always has exactly one non-deletable, non-renameable {@code isDefault}
 * region that implicitly catches any chunk not explicitly assigned
 * elsewhere (see {@code FtbHookSavedData.getChunkRegion}).
 * <p>
 * {@code properties} is a sparse map: propertyId -&gt; non-minimum serialized
 * value. An absent key means that property is at its minimum (unprotected)
 * value for this region, matching the sparse-map convention already used by
 * {@link TeamPendingState#pendingProperties()}.
 * <p>
 * {@code buyerAccess} is one of {@link dev.malik.lcftbhook.teams.MarketplaceTeamProperties}'s
 * {@code ACCESS_ALL}/{@code ACCESS_ALLIES}/{@code ACCESS_TEAM} constants -
 * who may buy a marketplace listing within this region (state-owned "sell as
 * country" or a private resale). Per-region rather than team-wide so
 * different parts of a team's territory can open up to different buyers.
 */
public record Region(
        UUID id,
        String name,
        boolean isDefault,
        Map<String, String> properties,
        boolean allowPrivateSelling,
        String buyerAccess
) {

    public static final String DEFAULT_NAME = "Default";

    public static Region createDefault() {
        return new Region(UUID.randomUUID(), DEFAULT_NAME, true, Map.of(), true, dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALL);
    }

    public static Region create(String name) {
        return new Region(UUID.randomUUID(), name, false, Map.of(), true, dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALL);
    }

    public Region withName(String newName) {
        return new Region(id, newName, isDefault, properties, allowPrivateSelling, buyerAccess);
    }

    public Region withProperty(String propertyId, String value) {
        Map<String, String> updated = new HashMap<>(properties);
        updated.put(propertyId, value);
        return new Region(id, name, isDefault, Map.copyOf(updated), allowPrivateSelling, buyerAccess);
    }

    public Region withoutProperty(String propertyId) {
        if (!properties.containsKey(propertyId)) {
            return this;
        }
        Map<String, String> updated = new HashMap<>(properties);
        updated.remove(propertyId);
        return new Region(id, name, isDefault, Map.copyOf(updated), allowPrivateSelling, buyerAccess);
    }

    public Region withAllowPrivateSelling(boolean allow) {
        return new Region(id, name, isDefault, properties, allow, buyerAccess);
    }

    public Region withBuyerAccess(String access) {
        return new Region(id, name, isDefault, properties, allowPrivateSelling, access);
    }

    public String propertyValue(ProtectionProperty property) {
        return properties.getOrDefault(property.id(), property.minimumSerialized());
    }

    public boolean isAtMinimum(ProtectionProperty property) {
        return property.isAtMinimum(propertyValue(property));
    }
}
