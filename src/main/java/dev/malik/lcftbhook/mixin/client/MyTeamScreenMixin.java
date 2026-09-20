package dev.malik.lcftbhook.mixin.client;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;
import dev.malik.lcftbhook.client.ClientWarState;
import dev.malik.lcftbhook.client.WarIcons;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;

/**
 * Both toolbar button callbacks below open a screen (RegionListScreen /
 * WarScreen) via reflection instead of a direct constructor reference. A
 * direct reference compiles into the mixin's lambda body, which Sponge
 * Mixin merges into {@code MyTeamScreen} itself - and since that class gets
 * Mixin-transformed lazily (first time this screen opens, sometimes late in
 * a session), a direct cross-class type reference there intermittently
 * throws {@code ClassNotFoundException} for a class that demonstrably
 * exists and loads fine everywhere else (same failure class already seen
 * and fixed in {@link dev.malik.lcftbhook.mixin.PartyTeamRankSyncMixin} and
 * the {@code EditConfigScreen} mixins). Reflection avoids Mixin needing to
 * resolve the screen class as a bytecode-level type reference at all.
 */
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

    @Unique
    private SimpleButton lcFtbHook$regionsButton;

    @Inject(method = "addWidgets", at = @At("RETURN"), remap = false)
    private void lcFtbHook$addToolbarButtons(CallbackInfo ci) {
        MyTeamScreen screen = (MyTeamScreen) (Object) this;

        // Regions are core (not optional like war), so this button is always shown.
        // Icons.SHIELD (used for War below) reads as a sword-like shape at
        // this icon size, not a shield - LOCK is unambiguous for protection.
        lcFtbHook$regionsButton = new SimpleButton(
                screen,
                Component.translatable("gui.lc_ftb_hook.regions.title"),
                Icons.LOCK,
                (button, mouseButton) -> lcFtbHook$openScreen("dev.malik.lcftbhook.client.gui.RegionListScreen", screen)
        );
        screen.add(lcFtbHook$regionsButton);

        if (!ClientWarState.warModuleEnabled()) {
            lcFtbHook$warButton = null;
            return;
        }

        lcFtbHook$warButton = new SimpleButton(
                screen,
                Component.translatable("gui.lc_ftb_hook.war.title"),
                WarIcons.ICON,
                (button, mouseButton) -> lcFtbHook$openScreen("dev.malik.lcftbhook.client.gui.WarScreen", screen)
        );
        screen.add(lcFtbHook$warButton);
    }

    @Unique
    private static void lcFtbHook$openScreen(String className, MyTeamScreen parent) {
        try {
            Class<?> cls = Class.forName(className);
            Constructor<?> ctor = cls.getConstructor(MyTeamScreen.class);
            Object screen = ctor.newInstance(parent);
            cls.getMethod("openGui").invoke(screen);
        } catch (ReflectiveOperationException e) {
            org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").error("Failed to open {}", className, e);
        }
    }

    @Inject(method = "alignWidgets", at = @At("RETURN"), remap = false)
    private void lcFtbHook$alignToolbarButtons(CallbackInfo ci) {
        if (settingsButton == null) {
            return;
        }

        int shift = TOOLBAR_BUTTON_SPACING;
        if (lcFtbHook$regionsButton != null) {
            lcFtbHook$regionsButton.setPosAndSize(
                    settingsButton.getPosX() - shift,
                    TOOLBAR_BUTTON_Y,
                    TOOLBAR_BUTTON_SIZE,
                    TOOLBAR_BUTTON_SIZE
            );
            shift += TOOLBAR_BUTTON_SPACING;
        }

        if (ClientWarState.warModuleEnabled() && lcFtbHook$warButton != null) {
            lcFtbHook$warButton.setPosAndSize(
                    settingsButton.getPosX() - shift,
                    TOOLBAR_BUTTON_Y,
                    TOOLBAR_BUTTON_SIZE,
                    TOOLBAR_BUTTON_SIZE
            );
            shift += TOOLBAR_BUTTON_SPACING;
        }

        // Make room for the new buttons in FTB's right-side toolbar.
        int totalShift = shift - TOOLBAR_BUTTON_SPACING;
        lcFtbHook$shiftToolbarButton(inviteButton, totalShift);
        lcFtbHook$shiftToolbarButton(allyButton, totalShift);
        lcFtbHook$shiftToolbarButton(toggleChatButton, totalShift);
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
