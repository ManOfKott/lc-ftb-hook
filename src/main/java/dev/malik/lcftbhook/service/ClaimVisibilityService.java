package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;

public final class ClaimVisibilityService {
    public static final String PROPERTY_KEY = "claim_visibility";

    private ClaimVisibilityService() {
    }

    public static void ensurePublic(Team team) {
        if (team == null || !team.isValid()) {
            return;
        }
        if (team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY) != PrivacyMode.PUBLIC) {
            team.setProperty(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
        }
    }

    public static boolean isClaimVisibilityConfigId(String configId) {
        return PROPERTY_KEY.equals(ProtectionPriceDisplay.normalizePropertyKey(configId));
    }
}
