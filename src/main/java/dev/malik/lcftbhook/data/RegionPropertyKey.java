package dev.malik.lcftbhook.data;

import java.util.UUID;

/**
 * Composite key encoding ("regionId|propertyId") used as the key of
 * {@link TeamPendingState#pendingProperties()}, so a single flat
 * {@code Map<String,String>} can hold pending edits for every region's
 * properties without changing the pending-state shape itself.
 */
public final class RegionPropertyKey {
    private static final char SEPARATOR = '|';

    private RegionPropertyKey() {
    }

    public static String encode(UUID regionId, String propertyId) {
        return regionId.toString() + SEPARATOR + propertyId;
    }

    public static UUID regionId(String key) {
        return UUID.fromString(key.substring(0, key.indexOf(SEPARATOR)));
    }

    public static String propertyId(String key) {
        return key.substring(key.indexOf(SEPARATOR) + 1);
    }

    public static boolean referencesRegion(String key, UUID regionId) {
        int sep = key.indexOf(SEPARATOR);
        return sep >= 0 && key.substring(0, sep).equals(regionId.toString());
    }
}
