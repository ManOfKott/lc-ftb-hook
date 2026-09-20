package dev.malik.lcftbhook.mixin.client.xaero;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

import java.lang.reflect.Method;

/**
 * Fixed-position info panel on the World Map while hovering a claimed chunk
 * - see {@code XaeroClaimInfoPanel} for the actual rendered content. This
 * injected method deliberately does nothing but delegate via reflection -
 * see {@code XaeroMarketplaceMenu}'s javadoc for why direct references to
 * lc_ftb_hook classes from a method Mixin merges into {@code GuiMap} are
 * unsafe.
 * <p>
 * This runs every frame the World Map is open, so any failure here (e.g. an
 * internal Xaero API this integration relies on changing shape in a future
 * version) is caught as {@link Throwable}, not just {@link ReflectiveOperationException}
 * - a reflective {@code Method.invoke} only wraps exceptions thrown FROM
 * INSIDE the invoked method as {@code InvocationTargetException}; a
 * {@code LinkageError}/{@code NoSuchMethodError} from the invoked class
 * itself failing to link against a changed Xaero API is thrown directly,
 * not wrapped, and would otherwise propagate out of {@code GuiMap.render}
 * every single frame. Once that happens, {@link #lcFtbHook$disabled} stops
 * retrying (and re-logging) for the rest of this game session, since a
 * broken integration isn't going to fix itself mid-session.
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapClaimInfoPanelMixin {
    private static boolean lcFtbHook$disabled = false;

    @Shadow
    private int mouseBlockPosX;
    @Shadow
    private int mouseBlockPosZ;

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void lcFtbHook$drawClaimInfoPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (lcFtbHook$disabled) {
            return;
        }
        try {
            Class<?> cls = Class.forName("dev.malik.lcftbhook.client.xaero.XaeroClaimInfoPanel");
            Method method = cls.getMethod("render", GuiGraphics.class, int.class, int.class);
            method.invoke(null, graphics, mouseBlockPosX, mouseBlockPosZ);
        } catch (Throwable e) {
            lcFtbHook$disabled = true;
            org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").error(
                    "Failed to render Xaero claim info panel - disabling it for the rest of this session "
                            + "(likely an incompatible Xaero World Map version)", e);
        }
    }
}
