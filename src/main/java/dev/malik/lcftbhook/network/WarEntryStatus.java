package dev.malik.lcftbhook.network;

public enum WarEntryStatus {
    ACTIVE,
    PENDING_DECLARE,
    PENDING_END;

    public static WarEntryStatus fromId(int id) {
        WarEntryStatus[] values = values();
        if (id < 0 || id >= values.length) {
            return ACTIVE;
        }
        return values[id];
    }

    public int id() {
        return ordinal();
    }

    public boolean isPending() {
        return this != ACTIVE;
    }
}
