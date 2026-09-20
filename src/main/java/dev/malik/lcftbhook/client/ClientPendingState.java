package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.RegionPropertyKey;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClientPendingState {
    private static Map<String, String> pendingProperties = Map.of();
    private static Set<String> pendingForceLoads = Set.of();
    private static Set<String> pendingForceUnloads = Set.of();
    private static Map<String, UUID> pendingRegionAssignments = Map.of();

    private ClientPendingState() {
    }

    public static void update(
            Map<String, String> properties,
            Set<String> forceLoads,
            Set<String> forceUnloads,
            Map<String, UUID> regionAssignments
    ) {
        pendingProperties = Map.copyOf(properties);
        pendingForceLoads = Set.copyOf(forceLoads);
        pendingForceUnloads = Set.copyOf(forceUnloads);
        pendingRegionAssignments = Map.copyOf(regionAssignments);
        MapManager.getInstance().ifPresent(manager -> manager.updateAllRegions(false));
    }

    public static boolean isPendingForceLoad(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingForceLoads.contains(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    public static boolean isPendingForceUnload(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingForceUnloads.contains(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    public static boolean isPendingRegionAssignment(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingRegionAssignments.containsKey(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    @Nullable
    public static UUID pendingRegionAssignmentTarget(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingRegionAssignments.get(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    @Nullable
    public static String getPendingRegionProperty(UUID regionId, ProtectionProperty property) {
        return pendingProperties.get(RegionPropertyKey.encode(regionId, property.id()));
    }

    public static boolean hasPendingRegionProperty(UUID regionId, ProtectionProperty property) {
        return pendingProperties.containsKey(RegionPropertyKey.encode(regionId, property.id()));
    }

    private static final class ChunkPosKeyClient {
        private static String encode(net.minecraft.resources.ResourceLocation dimension, int x, int z) {
            return dimension + "#" + x + "#" + z;
        }
    }
}
