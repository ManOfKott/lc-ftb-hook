package dev.malik.lcftbhook.service;

/**
 * Carries the land/build status of the chunk currently being evaluated in
 * {@code shouldPreventInteraction} down to {@code canPlayerUse}, which does not
 * receive the chunk itself. Set before the protection check runs and cleared
 * when it returns.
 */
public final class LandProtectionContext {
    private static final ThreadLocal<Boolean> CURRENT_IS_LAND = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private LandProtectionContext() {
    }

    public static void set(boolean land) {
        CURRENT_IS_LAND.set(land);
    }

    public static boolean isLand() {
        return CURRENT_IS_LAND.get();
    }

    public static void clear() {
        CURRENT_IS_LAND.remove();
    }
}
