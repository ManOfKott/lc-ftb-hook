package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftbchunks.client.map.MapManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Set;

/** Client-side cache of all land chunk positions, synced from the server. */
public final class ClientLandChunks {
    private static Set<String> landChunks = Set.of();

    private ClientLandChunks() {
    }

    public static void update(Set<String> keys) {
        landChunks = Set.copyOf(keys);
        // Claim tints are baked into the map texture, re-render so the land
        // chunk opacity change shows up immediately.
        MapManager.getInstance().ifPresent(manager -> manager.updateAllRegions(false));
    }

    public static boolean isLand(ResourceLocation dimension, int x, int z) {
        return landChunks.contains(dimension + "#" + x + "#" + z);
    }

    public static boolean isLand(ResourceKey<Level> dimension, int x, int z) {
        return isLand(dimension.location(), x, z);
    }
}
