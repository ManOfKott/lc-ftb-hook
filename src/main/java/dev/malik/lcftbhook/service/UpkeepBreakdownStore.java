package dev.malik.lcftbhook.service;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UpkeepBreakdownStore {
    private static final Map<UUID, UpkeepBreakdown> LAST_BY_TEAM = new ConcurrentHashMap<>();

    private UpkeepBreakdownStore() {
    }

    public static void store(UpkeepBreakdown breakdown) {
        LAST_BY_TEAM.put(breakdown.teamId(), breakdown);
    }

    @Nullable
    public static UpkeepBreakdown get(UUID teamId) {
        return LAST_BY_TEAM.get(teamId);
    }
}
