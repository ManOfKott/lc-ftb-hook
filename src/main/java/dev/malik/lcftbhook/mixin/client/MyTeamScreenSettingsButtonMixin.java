package dev.malik.lcftbhook.mixin.client;

import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * FTB Teams' settings button installs a saved-callback on the properties
 * screen that always navigates back to the team screen, both on accept and
 * on cancel. Keep the player on the settings page when they accept; cancel
 * still returns to the team screen.
 */
@Mixin(targets = "dev.ftb.mods.ftbteams.client.gui.MyTeamScreen$SettingsButton", remap = false)
public class MyTeamScreenSettingsButtonMixin {
    @Redirect(
            method = "lambda$new$0",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbteams/client/gui/MyTeamScreen;openGui()V"
            ),
            remap = false
    )
    private static void lcFtbHook$stayOnSettingsAfterAccept(MyTeamScreen screen, MyTeamScreen capturedScreen, boolean accepted) {
        if (!accepted) {
            screen.openGui();
        }
    }
}
