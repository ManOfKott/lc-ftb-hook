package dev.malik.lcftbhook.mixin.client.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.map.WorldMapSession;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;

/**
 * Registers {@code BuyableChunkHighlighter} into Xaero's own
 * {@link HighlighterRegistry} - the same mechanism ftbxaerocompat uses for
 * its claim-color tinting.
 * <p>
 * {@code WorldMapSession.init()} does {@code new HighlighterRegistry()},
 * lets other mods register into it, calls {@code registry.end()} (which
 * locks the list via {@code Collections.unmodifiableList}), and only
 * AFTERWARDS constructs {@code MapProcessor} with that registry - so
 * {@code MapProcessor.getHighlighterRegistry()} is USELESS for registering
 * anything (it doesn't exist yet, and by the time it does the list is
 * already locked). Redirecting the {@code new HighlighterRegistry()} call
 * itself is the only point that's both early enough to register before
 * {@code end()} runs, and guaranteed not to collide with ftbxaerocompat's
 * own {@code @Redirect} on the separate {@code end()} call in the same method.
 */
@Mixin(value = WorldMapSession.class, remap = false)
public class WorldMapSessionHighlighterMixin {
    @Redirect(
            method = "init",
            at = @At(value = "NEW", target = "Lxaero/map/highlight/HighlighterRegistry;"),
            remap = false
    )
    private HighlighterRegistry lcFtbHook$createAndRegister() {
        HighlighterRegistry registry = new HighlighterRegistry();
        try {
            Class<?> cls = Class.forName("dev.malik.lcftbhook.client.xaero.BuyableChunkHighlighter");
            Object highlighter = cls.getConstructor().newInstance();
            registry.register((AbstractHighlighter) highlighter);
            org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").info("Registered buyable-chunk highlighter into Xaero's HighlighterRegistry");
        } catch (Throwable e) {
            // Throwable, not just ReflectiveOperationException - a
            // LinkageError/AbstractMethodError from BuyableChunkHighlighter
            // failing to link against a changed ChunkHighlighter API (e.g. an
            // abstract method it overrides being renamed/re-signed) is thrown
            // directly, not wrapped, and would otherwise abort Xaero's own
            // session init for every highlighter, not just ours.
            org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").error(
                    "Failed to register buyable-chunk highlighter (likely an incompatible Xaero World Map version)", e);
        }
        return registry;
    }
}
