package dev.malik.lcftbhook.teams;

import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.event.TeamCollectPropertiesEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.malik.lcftbhook.LCFtbHook;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Land chunks (state territory) can only be protected against block editing
 * and block interaction. All other FTB Chunks protections (mob griefing,
 * explosions, PvP, entity interaction) are not configurable for land chunks and
 * are always left unprotected; the enforcement mixins handle that directly.
 */
public final class LandProperties {
    public static final PrivacyProperty LAND_BLOCK_INTERACT_MODE = new PrivacyProperty(
            rl("land_block_interact_mode"), FTBChunksWorldConfig.DEF_BLOCK_INTERACT::get
    );
    public static final PrivacyProperty LAND_BLOCK_EDIT_MODE = new PrivacyProperty(
            rl("land_block_edit_mode"), FTBChunksWorldConfig.DEF_BLOCK_EDIT::get
    );

    public static final List<TeamProperty<?>> ALL = List.of(
            LAND_BLOCK_INTERACT_MODE,
            LAND_BLOCK_EDIT_MODE
    );

    private static final Map<PrivacyProperty, PrivacyProperty> PRIVACY_TO_LAND = Map.of(
            FTBChunksProperties.BLOCK_INTERACT_MODE, LAND_BLOCK_INTERACT_MODE,
            FTBChunksProperties.BLOCK_EDIT_MODE, LAND_BLOCK_EDIT_MODE
    );

    private LandProperties() {
    }

    public static void register() {
        TeamEvent.COLLECT_PROPERTIES.register(LandProperties::collect);
    }

    private static void collect(TeamCollectPropertiesEvent event) {
        ALL.forEach(event::add);
    }

    /**
     * Maps a build privacy protection to its land counterpart. Properties
     * without a land counterpart (fake player settings, claim visibility, ...)
     * are returned unchanged.
     */
    public static PrivacyProperty landCounterpart(PrivacyProperty property) {
        return PRIVACY_TO_LAND.getOrDefault(property, property);
    }

    public static boolean isLandProperty(TeamProperty<?> property) {
        return ALL.contains(property);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, path);
    }
}
