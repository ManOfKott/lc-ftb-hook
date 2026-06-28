package dev.malik.lcftbhook.mixin;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.malik.lcftbhook.service.LandChunkService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Land chunks can only be protected against block editing/interaction, so
 * explosions and mob griefing are always allowed (unprotected) on them,
 * regardless of the team's build protection settings.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl", remap = false)
public abstract class ClaimedChunkProtectionMixin {
    @Inject(method = "allowExplosions", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$landExplosions(CallbackInfoReturnable<Boolean> cir) {
        if (LandChunkService.isLandChunk((ClaimedChunk) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "allowMobGriefing", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$landMobGriefing(CallbackInfoReturnable<Boolean> cir) {
        if (LandChunkService.isLandChunk((ClaimedChunk) this)) {
            cir.setReturnValue(true);
        }
    }
}
