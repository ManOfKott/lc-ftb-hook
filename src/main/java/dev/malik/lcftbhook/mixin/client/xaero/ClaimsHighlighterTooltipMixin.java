package dev.malik.lcftbhook.mixin.client.xaero;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses ftbxaerocompat's plain bottom-center team-name tooltip
 * entirely - the same info (plus owner/for-sale/protection details) is
 * shown instead in a fixed-position side panel, see
 * {@link GuiMapClaimInfoPanelMixin}.
 */
@Mixin(targets = "dev.satherov.ftbxaerocompat.ClaimsHighlighter", remap = false)
public abstract class ClaimsHighlighterTooltipMixin {
    @Inject(method = "getChunkHighlightSubtleTooltip", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$suppress(ResourceKey<Level> key, int x, int z, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(Component.empty());
    }
}
