package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;

import javax.annotation.Nullable;

/**
 * A chunk's effective protection value is its Region's own stored value,
 * further loosened by the chunk's private-owner override if present (never
 * tightened - that invariant is enforced at write time in
 * {@link MarketplaceService#setChunkOverride}, not here).
 */
public final class ProtectionResolution {
    private ProtectionResolution() {
    }

    public static String effectiveValue(Region region, @Nullable ChunkOwnership ownership, ProtectionProperty property) {
        if (ownership != null && ownership.protectionOverride().containsKey(property.id())) {
            return ownership.protectionOverride().get(property.id());
        }
        return region.propertyValue(property);
    }

    public static boolean isAtMinimum(Region region, @Nullable ChunkOwnership ownership, ProtectionProperty property) {
        return property.isAtMinimum(effectiveValue(region, ownership, property));
    }

    /**
     * 0 = loosest (unprotected/PUBLIC) upward. Allies/Team/Private all share
     * the same tier (all three cost the same to enable, per
     * {@link ProtectionProperty}'s single config price regardless of which
     * of the three is picked) - freely switchable between each other without
     * it counting as tightening/loosening, so a region baseline of Allies
     * doesn't block overriding to Team or Private (or vice versa).
     */
    public static int strictness(ProtectionProperty property, String value) {
        if (property.isPrivacyMode()) {
            return switch (PrivacyLevel.valueOf(value)) {
                case PUBLIC -> 0;
                case ALLIES, TEAM, PRIVATE -> 1;
            };
        }
        return "true".equals(value) ? 0 : 1;
    }

    public static boolean isLooserOrEqual(ProtectionProperty property, String candidate, String baseline) {
        return strictness(property, candidate) <= strictness(property, baseline);
    }
}
