package dev.malik.lcftbhook.mixin.client;

import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.malik.lcftbhook.client.EditConfigScreenUiHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen$CustomTopPanel", remap = false)
public class EditConfigScreenTopPanelMixin {
    @Shadow(remap = false)
    @Final
    EditConfigScreen this$0;

    @Shadow(remap = false)
    TextField titleLabel;

    @Inject(method = "alignWidgets", at = @At("TAIL"), remap = false)
    private void lcFtbHook$reserveTitleRow(CallbackInfo ci) {
        if (!EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(this$0.getTitle())) {
            return;
        }
        titleLabel.setPosAndSize(
                4,
                EditConfigScreenUiHelper.TITLE_Y,
                titleLabel.width,
                EditConfigScreenUiHelper.TITLE_HEIGHT
        );
    }
}
