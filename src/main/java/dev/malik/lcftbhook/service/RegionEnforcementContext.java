package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.Region;

import javax.annotation.Nullable;

/**
 * Carries the resolved {@link Region} and {@link ChunkOwnership} of the
 * chunk currently being evaluated in {@code shouldPreventInteraction} down
 * to {@code canPlayerUse} (which only receives the privacy property, not the
 * chunk). Set before the protection check runs and cleared when it returns.
 */
public final class RegionEnforcementContext {
    public record Resolved(Region region, ChunkOwnership ownership) {
    }

    private static final ThreadLocal<Resolved> CURRENT = new ThreadLocal<>();

    private RegionEnforcementContext() {
    }

    public static void set(Region region, ChunkOwnership ownership) {
        CURRENT.set(new Resolved(region, ownership));
    }

    @Nullable
    public static Resolved get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
