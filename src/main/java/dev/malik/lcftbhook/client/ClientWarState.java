package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.network.SyncWarStatePayload;
import dev.malik.lcftbhook.network.WarTeamEntry;

import java.util.List;

public final class ClientWarState {
    private static long baseUpkeepCopper;
    private static long incomingWarCopper;
    private static long outgoingWarCopper;
    private static double warCostMultiplier = 1.2D;
    private static List<WarTeamEntry> incoming = List.of();
    private static List<WarTeamEntry> outgoing = List.of();
    private static List<WarTeamEntry> availableTargets = List.of();
    private static boolean canManageWar;
    private static boolean warModuleEnabled = true;

    private ClientWarState() {
    }

    public static void setWarModuleEnabled(boolean enabled) {
        warModuleEnabled = enabled;
    }

    public static boolean warModuleEnabled() {
        return warModuleEnabled;
    }

    public static void update(SyncWarStatePayload payload) {
        baseUpkeepCopper = payload.baseUpkeepCopper();
        incomingWarCopper = payload.incomingWarCopper();
        outgoingWarCopper = payload.outgoingWarCopper();
        warCostMultiplier = payload.warCostMultiplier();
        incoming = List.copyOf(payload.incoming());
        outgoing = List.copyOf(payload.outgoing());
        availableTargets = List.copyOf(payload.availableTargets());
        canManageWar = payload.canManageWar();
    }

    public static long baseUpkeepCopper() {
        return baseUpkeepCopper;
    }

    public static long incomingWarCopper() {
        return incomingWarCopper;
    }

    public static long outgoingWarCopper() {
        return outgoingWarCopper;
    }

    public static long totalWarCopper() {
        return incomingWarCopper + outgoingWarCopper;
    }

    public static double warCostMultiplier() {
        return warCostMultiplier;
    }

    public static List<WarTeamEntry> incoming() {
        return incoming;
    }

    public static List<WarTeamEntry> outgoing() {
        return outgoing;
    }

    public static List<WarTeamEntry> availableTargets() {
        return availableTargets;
    }

    public static boolean canManageWar() {
        return canManageWar;
    }
}
