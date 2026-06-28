package dev.malik.lcftbhook.mixin.client;

import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;
import dev.malik.lcftbhook.client.ClientWarState;
import dev.malik.lcftbhook.client.WarIcons;
import dev.malik.lcftbhook.client.gui.WarScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MyTeamScreen.class, remap = false)
public class MyTeamScreenMixin {
    private static final int TOOLBAR_BUTTON_SIZE = 16;
    private static final int TOOLBAR_BUTTON_Y = 3;
    private static final int TOOLBAR_BUTTON_SPACING = 18;

    @Shadow(remap = false)
    private Button settingsButton;

    @Shadow(remap = false)
    private Button inviteButton;

    @Shadow(remap = false)
    private Button allyButton;

    @Shadow(remap = false)
    private Button toggleChatButton;

    @Unique
    private SimpleButton lcFtbHook$warButton;

    @Inject(method = "addWidgets", at = @At("RETURN"), remap = false)
    private void lcFtbHook$addWarButton(CallbackInfo ci) {
        if (!ClientWarState.warModuleEnabled()) {
            lcFtbHook$warButton = null;
            return;
        }

        MyTeamScreen screen = (MyTeamScreen) (Object) this;
        lcFtbHook$warButton = new SimpleButton(
                screen,
                Component.translatable("gui.lc_ftb_hook.war.title"),
                WarIcons.SWORD,
                (button, mouseButton) -> new WarScreen(screen).openGui()
        );
        screen.add(lcFtbHook$warButton);
    }

    @Inject(method = "alignWidgets", at = @At("RETURN"), remap = false)
    private void lcFtbHook$alignWarButton(CallbackInfo ci) {
        if (!ClientWarState.warModuleEnabled() || lcFtbHook$warButton == null || settingsButton == null) {
            return;
        }

        lcFtbHook$warButton.setPosAndSize(
                settingsButton.getPosX() - TOOLBAR_BUTTON_SPACING,
                TOOLBAR_BUTTON_Y,
                TOOLBAR_BUTTON_SIZE,
                TOOLBAR_BUTTON_SIZE
        );

        // Make room for the war button in FTB's right-side toolbar.
        lcFtbHook$shiftToolbarButton(inviteButton, TOOLBAR_BUTTON_SPACING);
        lcFtbHook$shiftToolbarButton(allyButton, TOOLBAR_BUTTON_SPACING);
        lcFtbHook$shiftToolbarButton(toggleChatButton, TOOLBAR_BUTTON_SPACING);
    }

    @Unique
    private void lcFtbHook$shiftToolbarButton(Button button, int amount) {
        if (button != null) {
            button.setPosAndSize(
                    button.getPosX() - amount,
                    button.getPosY(),
                    button.getWidth(),
                    button.getHeight()
            );
        }
    }
}
