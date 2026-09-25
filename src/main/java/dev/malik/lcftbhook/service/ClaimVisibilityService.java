package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamPropertiesChangedEvent;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;

public final class ClaimVisibilityService {
    public static final String PROPERTY_KEY = "claim_visibility";

    private ClaimVisibilityService() {
    }

    public static void register() {
        TeamEvent.PROPERTIES_CHANGED.register(ClaimVisibilityService::onTeamPropertiesChanged);
    }

    private static void onTeamPropertiesChanged(TeamPropertiesChangedEvent event) {
        if (!dev.malik.lcftbhook.config.LCFtbHookConfig.SERVER.lockClaimVisibilityPublic.get()) {
            return;
        }
        Team team = event.getTeam();
        if (!team.isValid()) {
            return;
        }
        if (team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY) != PrivacyMode.PUBLIC) {
            ensurePublic(team);
            notifyTeam(team);
        }
    }

    public static void ensurePublic(Team team) {
        if (team == null || !team.isValid()) {
            return;
        }
        if (!dev.malik.lcftbhook.config.LCFtbHookConfig.SERVER.lockClaimVisibilityPublic.get()) {
            return;
        }
        if (team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY) != PrivacyMode.PUBLIC) {
            team.setProperty(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
            team.syncOnePropertyToTeam(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
        }
    }

    private static void notifyTeam(Team team) {
        net.minecraft.network.chat.Component message =
                net.minecraft.network.chat.Component.translatable("message.lc_ftb_hook.claim_visibility_locked");
        for (net.minecraft.server.level.ServerPlayer member : team.getOnlineMembers()) {
            member.displayClientMessage(message, false);
        }
    }

}
