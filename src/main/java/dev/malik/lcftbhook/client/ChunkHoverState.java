package dev.malik.lcftbhook.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Tracks which chunk button is currently hovered in FTB Chunks' claim
 * manager screen, for the fixed side info panel. Reset once per frame at the
 * start of the panel's own background draw, then set (at most once) by
 * whichever button reports itself hovered later that same frame.
 */
public final class ChunkHoverState {
    @Nullable
    private static ResourceKey<Level> dimension;
    private static int chunkX;
    private static int chunkZ;
    private static boolean present;

    private ChunkHoverState() {
    }

    public static void reset() {
        present = false;
    }

    public static void markHovered(ResourceKey<Level> dim, int x, int z) {
        dimension = dim;
        chunkX = x;
        chunkZ = z;
        present = true;
    }

    public static boolean isPresent() {
        return present;
    }

    @Nullable
    public static ResourceKey<Level> dimension() {
        return dimension;
    }

    public static int chunkX() {
        return chunkX;
    }

    public static int chunkZ() {
        return chunkZ;
    }
}
