package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.config.ForceLoadUpkeepMode;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.config.ProtectionUpkeepMode;
import net.minecraft.server.MinecraftServer;

/**
 * Whether force-load / protection upkeep should actually be charged (and
 * thus can be dismantled for non-payment) for the current period, per
 * {@link LCFtbHookConfig.Server#forceLoadUpkeepMode}/{@link LCFtbHookConfig.Server#protectionUpkeepMode}.
 * When a category isn't chargeable this period its cost is treated as zero
 * everywhere (billing, UI, dismantle/restore affordability checks) - it is
 * simply frozen until someone relevant comes online again.
 */
public final class UpkeepGating {
    private UpkeepGating() {
    }

    public static boolean shouldChargeForceLoad(MinecraftServer server, Team team) {
        ForceLoadUpkeepMode mode = LCFtbHookConfig.SERVER.forceLoadUpkeepMode.get();
        return switch (mode) {
            case ALWAYS -> true;
            case DEFAULT -> isAnyTeamMemberOnline(team);
        };
    }

    public static boolean shouldChargeProtection(MinecraftServer server, Team team) {
        ProtectionUpkeepMode mode = LCFtbHookConfig.SERVER.protectionUpkeepMode.get();
        return switch (mode) {
            case HEAVY -> true;
            case DEFAULT -> isAnyPlayerOnlineOnServer(server);
            case LIGHT -> isAnyTeamMemberOnline(team);
        };
    }

    private static boolean isAnyTeamMemberOnline(Team team) {
        return !team.getOnlineMembers().isEmpty();
    }

    private static boolean isAnyPlayerOnlineOnServer(MinecraftServer server) {
        return server.getPlayerList().getPlayerCount() > 0;
    }
}
