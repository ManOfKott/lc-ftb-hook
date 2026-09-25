package dev.malik.lcftbhook.mixin.client.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;

/**
 * Registers {@code BuyableChunkHighlighter} as the LAST entry in Xaero's
 * {@link HighlighterRegistry}, so it always draws on top of every other
 * registered highlighter - decompiling
 * {@code DimensionHighlighterHandler.applyChunkHighlightColors} confirmed it
 * iterates {@code registry.getHighlighters()} in plain registration order,
 * alpha-blending each highlighter's color onto an accumulating buffer, so
 * whichever highlighter registered LAST paints over everything before it.
 * <p>
 * This used to register via a {@code @Redirect} on {@code new
 * HighlighterRegistry()} inside {@code WorldMapSession.init()} - which made
 * us the FIRST entry, not the last. That was the real cause behind the
 * marketplace hatch still looking team-tinted despite being fully opaque
 * (255 alpha) where we draw it: ftbxaerocompat's own {@code ClaimsHighlighter}
 * (the team-color tint) registers via its own {@code @Redirect} on the
 * {@code registry.end()} call in that same method - always LAST, right
 * before the list locks - so its semi-transparent tint was landing on top of
 * our opaque stripes afterward, not the other way around.
 * <p>
 * Injecting at the {@code HEAD} of {@link HighlighterRegistry#end()} itself
 * sidesteps that ordering fight instead of trying to win it: {@code end()}
 * only ever runs once, synchronously, after every mod's registration has
 * already happened (regardless of whether that mod hooked {@code init()}
 * directly or - like ftbxaerocompat - redirected the {@code end()} call
 * itself, since a redirect still has to actually invoke the real {@code
 * end()} to preserve behavior, which re-enters right here). Registering here
 * is therefore guaranteed to land as the final entry no matter what other
 * mods do, without needing to know or race their specific mechanism, and
 * without colliding with ftbxaerocompat's own redirect (a different class/
 * method entirely).
 */
@Mixin(value = HighlighterRegistry.class, remap = false)
public class HighlighterRegistryEndMixin {
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void lcFtbHook$registerLast(CallbackInfo ci) {
        try {
            HighlighterRegistry self = (HighlighterRegistry) (Object) this;
            Class<?> cls = Class.forName("dev.malik.lcftbhook.client.xaero.BuyableChunkHighlighter");
            Object highlighter = cls.getConstructor().newInstance();
            self.register((AbstractHighlighter) highlighter);
            org.slf4j.LoggerFactory.getLogger("lc_ftb_hook")
                    .info("Registered buyable-chunk highlighter into Xaero's HighlighterRegistry (last, draws on top)");
        } catch (Throwable e) {
            // Throwable, not just ReflectiveOperationException - see the old
            // WorldMapSessionHighlighterMixin's javadoc for why: a failure to
            // link BuyableChunkHighlighter against a changed Xaero API must
            // never abort end() for every other highlighter too.
            org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").error(
                    "Failed to register buyable-chunk highlighter (likely an incompatible Xaero World Map version)", e);
        }
    }
}
