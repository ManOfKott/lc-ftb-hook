package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;

import javax.annotation.Nullable;

/**
 * A chunk's effective protection value is its Region's own current value,
 * further loosened by the chunk's private-owner override if present.
 */
public final class ProtectionResolution {
    private ProtectionResolution() {
    }

    /**
     * The private owner's stored preference for this property, regardless of
     * whether it's currently achievable against the region's own value right
     * now. A private owner may always set this (see
     * {@link MarketplaceService#setChunkOverride} - no longer refused at
     * write time), so it can silently diverge from {@link #effectiveValue}
     * whenever the owning region's own protection has since dropped below
     * it (e.g. dismantled for non-payment) - it stays stored and
     * automatically resumes taking effect once the region's value is
     * restored, rather than being lost. Returns {@code null} if no override
     * is stored for this property.
     */
    @Nullable
    public static String storedOverrideValue(@Nullable ChunkOwnership ownership, ProtectionProperty property) {
        return ownership != null ? ownership.protectionOverride().get(property.id()) : null;
    }

    /**
     * What's ACTUALLY enforced right now - the stored override only counts
     * if it's still looser-or-equal to the region's CURRENT value. A stored
     * override is never re-tightened or deleted by this falling back - it's
     * simply not consulted while it can't apply (see
     * {@link #storedOverrideValue} for the raw stored preference, e.g. for
     * display).
     */
    public static String effectiveValue(Region region, @Nullable ChunkOwnership ownership, ProtectionProperty property) {
        String regionValue = region.propertyValue(property);
        String override = storedOverrideValue(ownership, property);
        if (override != null && isLooserOrEqual(property, override, regionValue)) {
            return override;
        }
        return regionValue;
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
