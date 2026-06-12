package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import dev.malik.lcftbhook.service.ProtectionPricing;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ClientPendingState {
    private static Map<String, String> pendingProperties = Map.of();
    private static Set<String> pendingForceLoads = Set.of();
    private static Set<String> pendingForceUnloads = Set.of();

    private ClientPendingState() {
    }

    public static void update(
            Map<String, String> properties,
            Set<String> forceLoads,
            Set<String> forceUnloads
    ) {
        pendingProperties = Map.copyOf(properties);
        pendingForceLoads = Set.copyOf(forceLoads);
        pendingForceUnloads = Set.copyOf(forceUnloads);
    }

    public static boolean hasPendingProperty(String propertyId) {
        return resolvePendingPropertyKey(propertyId) != null;
    }

    public static boolean hasVisiblePendingProperty(String propertyId, Object currentValue) {
        if (!hasPendingProperty(propertyId)) {
            return false;
        }
        return !Objects.equals(currentValue, getDisplayValue(propertyId, currentValue));
    }

    @Nullable
    public static String resolvePendingPropertyKey(String propertyId) {
        String normalized = ProtectionPriceDisplay.normalizePropertyKey(propertyId);
        if (normalized == null) {
            return null;
        }

        if (pendingProperties.containsKey(normalized)) {
            return normalized;
        }

        for (String pendingKey : pendingProperties.keySet()) {
            if (normalized.equals(pendingKey)) {
                return pendingKey;
            }
        }

        for (TeamProperty<?> property : ProtectionPricing.PROTECTION_PROPERTIES) {
            String key = ProtectionPricing.propertyKey(property);
            if (key.equals(normalized) && pendingProperties.containsKey(key)) {
                return key;
            }
        }

        return null;
    }

    @Nullable
    public static String getPendingProperty(String propertyId) {
        String key = resolvePendingPropertyKey(propertyId);
        return key == null ? null : pendingProperties.get(key);
    }

    public static boolean isPendingForceLoad(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingForceLoads.contains(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    public static boolean isPendingForceUnload(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingForceUnloads.contains(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDisplayValue(TeamProperty<T> property, T currentValue) {
        String pending = getPendingProperty(ProtectionPricing.propertyKey(property));
        if (pending == null) {
            return currentValue;
        }
        return ProtectionPricing.deserializePropertyValue(property, pending, currentValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object getDisplayValue(String propertyId, Object currentValue) {
        String pendingKey = resolvePendingPropertyKey(propertyId);
        if (pendingKey == null) {
            return currentValue;
        }

        TeamProperty property = findProperty(pendingKey);
        if (property == null) {
            return currentValue;
        }

        String pending = pendingProperties.get(pendingKey);
        if (pending == null) {
            return currentValue;
        }
        return ProtectionPricing.deserializePropertyValue(property, pending, currentValue);
    }

    @Nullable
    private static TeamProperty<?> findProperty(String propertyId) {
        String normalized = ProtectionPriceDisplay.normalizePropertyKey(propertyId);
        if (normalized == null) {
            return null;
        }

        for (TeamProperty<?> property : ProtectionPricing.PROTECTION_PROPERTIES) {
            if (ProtectionPricing.propertyKey(property).equals(normalized)) {
                return property;
            }
        }
        return null;
    }

    private static final class ChunkPosKeyClient {
        private static String encode(net.minecraft.resources.ResourceLocation dimension, int x, int z) {
            return dimension + "#" + x + "#" + z;
        }
    }
}
