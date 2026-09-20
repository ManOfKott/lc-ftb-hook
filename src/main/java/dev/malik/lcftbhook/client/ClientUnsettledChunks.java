package dev.malik.lcftbhook.client;

import java.util.Set;

/** Client-side cache of chunks not yet settled by an upkeep tick (freshly claimed or freshly bought), synced from the server. */
public final class ClientUnsettledChunks {
    private static Set<String> unsettled = Set.of();

    private ClientUnsettledChunks() {
    }

    public static void update(Set<String> keys) {
        unsettled = Set.copyOf(keys);
    }

    public static boolean isUnsettled(String chunkKey) {
        return unsettled.contains(chunkKey);
    }
}
