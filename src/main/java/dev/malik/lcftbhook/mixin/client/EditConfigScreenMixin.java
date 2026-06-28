package dev.malik.lcftbhook.mixin.client;

import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.malik.lcftbhook.client.EditConfigScreenUiHelper;
import dev.malik.lcftbhook.client.PendingStateUiRefresh;
import dev.malik.lcftbhook.network.RequestClaimPricesPayload;
import dev.malik.lcftbhook.network.RequestLandChunksPayload;
import dev.malik.lcftbhook.network.RequestPendingStatePayload;
import dev.malik.lcftbhook.network.RequestWarStatePayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen", remap = false)
public class EditConfigScreenMixin {
    @Shadow(remap = false)
    @Final
    private Component title;
    @Shadow(remap = false)
    private boolean autoclose;
    @Shadow(remap = false)
    private boolean changed;

    @Unique
    private boolean lcFtbHook$suppressedAutoclose;

    @Inject(method = "onInit", at = @At("RETURN"), remap = false)
    private void lcFtbHook$requestPendingState(CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            PacketDistributor.sendToServer(new RequestClaimPricesPayload());
            PacketDistributor.sendToServer(new RequestPendingStatePayload());
            PacketDistributor.sendToServer(new RequestLandChunksPayload());
            PacketDistributor.sendToServer(new RequestWarStatePayload());
            // Pre-fill from the already-cached pending state right away so the
            // screen shows the queued (new) values from the first frame. The
            // request above refreshes this once the server's reply arrives.
            PendingStateUiRefresh.syncScreenValues((EditConfigScreen) (Object) this);
        }
    }

    @Inject(method = "doAccept", at = @At("HEAD"), remap = false)
    private void lcFtbHook$stayOpenOnAccept(CallbackInfo ci) {
        // Keep the properties screen open after Accept instead of jumping
        // back to the team screen.
        if (title != null && EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(title) && autoclose) {
            autoclose = false;
            lcFtbHook$suppressedAutoclose = true;
        }
    }

    @Inject(method = "doAccept", at = @At("TAIL"), remap = false)
    private void lcFtbHook$refreshPendingAfterAccept(CallbackInfo ci) {
        if (lcFtbHook$suppressedAutoclose) {
            // Restore so Cancel/ESC still closes the screen as usual, and
            // clear the dirty flag so a later Cancel does not warn about
            // unsaved changes that were in fact accepted.
            autoclose = true;
            lcFtbHook$suppressedAutoclose = false;
            changed = false;
        }
        PacketDistributor.sendToServer(new RequestClaimPricesPayload());
        PacketDistributor.sendToServer(new RequestPendingStatePayload());
        PacketDistributor.sendToServer(new RequestLandChunksPayload());
        PacketDistributor.sendToServer(new RequestWarStatePayload());
    }

    @Inject(method = "doCancel", at = @At("HEAD"), remap = false)
    private void lcFtbHook$discardWithoutConfirm(CallbackInfo ci) {
        // Cancel/ESC should always discard silently instead of asking
        // "Discard unsaved changes?". Clearing the dirty flag up front makes
        // doCancel take the direct close path without the confirmation popup.
        if (title != null && EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(title)) {
            changed = false;
        }
    }

    @Inject(method = "getTopPanelHeight", at = @At("RETURN"), cancellable = true, remap = false)
    private void lcFtbHook$expandTopPanelForNote(CallbackInfoReturnable<Integer> callback) {
        if (title != null && EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(title)) {
            callback.setReturnValue(callback.getReturnValue() + EditConfigScreenUiHelper.TOP_PANEL_EXTRA_HEIGHT);
        }
    }
}
