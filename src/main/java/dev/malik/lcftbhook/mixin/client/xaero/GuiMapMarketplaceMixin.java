package dev.malik.lcftbhook.mixin.client.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Adds region assignment + marketplace entries to Xaero World Map's
 * right-click menu, mirroring the same context menu populated on the FTB
 * Chunks screen (see {@code ChunkScreenPanelChunkButtonMixin}). A second,
 * independent mixin on {@code getRightClickOptions} alongside
 * ftbxaerocompat's own {@code GuiMapMixin} - both injecting into the same
 * method is safe/normal for Mixin.
 * <p>
 * This injected method deliberately does nothing but delegate, via
 * reflection, to the plain (non-mixin) {@code XaeroMarketplaceMenu} class -
 * see that class's javadoc for why every bit of actual menu-building logic
 * (and especially its lambdas) must NOT live directly in this mixin.
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMarketplaceMixin {
    @Shadow
    private MapTileSelection mapTileSelection;

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
    private void lcFtbHook$addMarketplaceOptions(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        if (mapTileSelection == null) {
            return;
        }
        try {
            Class<?> cls = Class.forName("dev.malik.lcftbhook.client.xaero.XaeroMarketplaceMenu");
            Method method = cls.getMethod("populate", GuiMap.class, MapTileSelection.class, ArrayList.class);
            method.invoke(null, (GuiMap) (Object) this, mapTileSelection, cir.getReturnValue());
        } catch (Throwable e) {
            // Throwable, not just ReflectiveOperationException - a reflective
            // invoke() only wraps exceptions thrown FROM INSIDE the invoked
            // method; a LinkageError/NoSuchMethodError from the invoked class
            // itself failing to link against a changed Xaero API is thrown
            // directly instead, and would otherwise break the whole
            // right-click menu (not just our own entries) if left uncaught.
            org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").error(
                    "Failed to populate Xaero marketplace menu (likely an incompatible Xaero World Map version)", e);
        }
    }
}
