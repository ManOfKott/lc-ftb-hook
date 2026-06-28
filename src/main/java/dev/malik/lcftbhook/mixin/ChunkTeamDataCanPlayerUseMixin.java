package dev.malik.lcftbhook.mixin;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.malik.lcftbhook.service.LandProtectionContext;
import dev.malik.lcftbhook.teams.LandProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * When the chunk under evaluation is a land chunk (flagged by
 * {@link LandProtectionContext}), only block edit/interact are protected and
 * read from their land counterpart. Every other privacy protection (e.g. entity
 * interaction) is unprotected on land chunks, so it resolves to PUBLIC.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl", remap = false)
public abstract class ChunkTeamDataCanPlayerUseMixin {
    @Redirect(
            method = "canPlayerUse",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbteams/api/Team;getProperty(Ldev/ftb/mods/ftbteams/api/property/TeamProperty;)Ljava/lang/Object;"
            ),
            remap = false
    )
    private Object lcFtbHook$useLandProperty(Team team, TeamProperty<?> property) {
        if (LandProtectionContext.isLand() && property instanceof PrivacyProperty privacy) {
            PrivacyProperty landProperty = LandProperties.landCounterpart(privacy);
            if (landProperty != privacy) {
                return team.getProperty(landProperty);
            }
            // No land counterpart: this protection is not configurable for land
            // chunks, so treat it as unprotected (anyone may use it).
            return PrivacyMode.PUBLIC;
        }
        return team.getProperty(property);
    }
}
