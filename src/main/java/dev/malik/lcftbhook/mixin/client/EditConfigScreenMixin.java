package dev.malik.lcftbhook.mixin.client;

import dev.malik.lcftbhook.network.RequestClaimPricesPayload;
import dev.malik.lcftbhook.network.RequestPendingStatePayload;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen", remap = false)
public class EditConfigScreenMixin {
    @Shadow(remap = false)
    @Final
    private Component title;
    @Inject(method = "onInit", at = @At("RETURN"), remap = false)
    private void lcFtbHook$requestPendingState(CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            PacketDistributor.sendToServer(new RequestClaimPricesPayload());
            PacketDistributor.sendToServer(new RequestPendingStatePayload());
        }
    }

    @Inject(method = "doAccept", at = @At("TAIL"), remap = false)
    private void lcFtbHook$refreshPendingAfterAccept(CallbackInfo ci) {
        PacketDistributor.sendToServer(new RequestClaimPricesPayload());
        PacketDistributor.sendToServer(new RequestPendingStatePayload());
    }

    @Inject(method = "getTitle", at = @At("RETURN"), cancellable = true, remap = false)
    private void lcFtbHook$appendUpkeepNote(CallbackInfoReturnable<Component> callback) {
        Component title = callback.getReturnValue();
        if (title == null || !isFtbChunksPropertiesTitle(title)) {
            return;
        }

        callback.setReturnValue(Component.empty()
                .append(title)
                .append(Component.literal("\n"))
                .append(Component.translatable(
                        "gui.lc_ftb_hook.protection_prices_note",
                        ProtectionPriceDisplay.upkeepPeriodLabel()
                ).withStyle(ChatFormatting.GRAY)));
    }

    @Inject(method = "getTopPanelHeight", at = @At("RETURN"), cancellable = true, remap = false)
    private void lcFtbHook$expandTopPanelForNote(CallbackInfoReturnable<Integer> callback) {
        if (title != null && isFtbChunksPropertiesTitle(title)) {
            callback.setReturnValue(callback.getReturnValue() + 10);
        }
    }

    private static boolean isFtbChunksPropertiesTitle(Component title) {
        String text = title.getString().toLowerCase();
        return text.contains("ftb chunks")
                || text.contains("chunk")
                || text.contains("team properties");
    }
}
