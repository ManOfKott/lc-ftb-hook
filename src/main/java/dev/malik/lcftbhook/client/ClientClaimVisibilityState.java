package dev.malik.lcftbhook.client;

/** Mirrors {@code LCFtbHookConfig.SERVER.lockClaimVisibilityPublic} client-side, synced via SyncClaimPricesPayload. */
public final class ClientClaimVisibilityState {
    private static boolean locked = true;

    private ClientClaimVisibilityState() {
    }

    public static void setLocked(boolean value) {
        locked = value;
    }

    public static boolean isLocked() {
        return locked;
    }
}
