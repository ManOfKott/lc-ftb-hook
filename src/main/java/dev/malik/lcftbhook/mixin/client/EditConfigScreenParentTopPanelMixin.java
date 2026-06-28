package dev.malik.lcftbhook.mixin.client;

import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractThreePanelScreen;
import dev.malik.lcftbhook.client.EditConfigScreenUiHelper;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.misc.AbstractThreePanelScreen$TopPanel", remap = false)
public class EditConfigScreenParentTopPanelMixin {
    @Shadow(remap = false)
    @Final
    AbstractThreePanelScreen this$0;

    @Inject(method = "drawBackground", at = @At("TAIL"), remap = false)
    private void lcFtbHook$drawProtectionPricesNote(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int w,
            int h,
            CallbackInfo ci
    ) {
        if (!(this$0 instanceof EditConfigScreen editScreen)) {
            return;
        }
        if (!EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(editScreen.getTitle())) {
            return;
        }
        EditConfigScreenUiHelper.drawProtectionPricesNote(graphics, theme, x, y, w);
    }
}
