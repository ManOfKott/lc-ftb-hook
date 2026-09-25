package dev.malik.lcftbhook.mixin.client;

import dev.ftb.mods.ftblibrary.config.ConfigValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Protection settings no longer live in this screen (moved to the Region
 * screens, see plan A6) - all that remains here is locking claim_visibility
 * to PUBLIC, which is a native FTB Chunks/Teams property unrelated to Regions.
 * <p>
 * The config-id normalizing/matching logic is inlined here (instead of
 * calling out to {@code ProtectionPriceDisplay}/{@code ClaimVisibilityService})
 * because this mixin only applies lazily, the first time the player opens
 * this specific FTB screen - Sponge Mixin's late-transform reference
 * resolution failed to find those mod classes at that point
 * ({@code ClassNotFoundException}) even though they're loaded and used
 * elsewhere at that point in a real session, crashing the game. Referencing
 * only JDK/vanilla/FTB Library types here avoids that entirely.
 */
@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen$ConfigEntryButton", remap = false)
public class EditConfigScreenConfigEntryButtonMixin {
    private static final String CLAIM_VISIBILITY_KEY = "claim_visibility";

    @Shadow(remap = false)
    private ConfigValue<?> configValue;

    // FTB Library 2101.1.35 no longer calls ConfigValue.getCanEdit() from this
    // constructor (it moved the check into draw()/onClicked() instead), so the old
    // INVOKE-shift injection point stopped existing and crashed at class load with
    // "Scanned 0 target(s)". configValue is already assigned by the time the
    // constructor returns, so locking it at TAIL is equivalent to the old behavior.
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void lcFtbHook$lockClaimVisibility(CallbackInfo ci) {
        if (CLAIM_VISIBILITY_KEY.equals(lcFtbHook$normalizePropertyKey(configValue.getPath())) && lcFtbHook$isLocked()) {
            configValue.setCanEdit(false);
        }
    }

    // Reflection, not a direct reference to ClientClaimVisibilityState - see
    // the class javadoc on why this mixin can't reference other lc_ftb_hook
    // classes directly. Defaults to true (locked, the original hardcoded
    // behavior) if anything about that lookup fails.
    private static boolean lcFtbHook$isLocked() {
        try {
            Class<?> cls = Class.forName("dev.malik.lcftbhook.client.ClientClaimVisibilityState");
            return (boolean) cls.getMethod("isLocked").invoke(null);
        } catch (Throwable e) {
            return true;
        }
    }

    private static String lcFtbHook$normalizePropertyKey(String configId) {
        if (configId == null || configId.isBlank()) {
            return null;
        }
        String key = configId;
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        int slash = key.lastIndexOf('/');
        if (slash >= 0) {
            key = key.substring(slash + 1);
        }
        int dot = key.lastIndexOf('.');
        if (dot >= 0) {
            key = key.substring(dot + 1);
        }
        return key.isBlank() ? null : key;
    }
}
