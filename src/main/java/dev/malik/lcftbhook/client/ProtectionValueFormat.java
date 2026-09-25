package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import net.minecraft.network.chat.Component;

/**
 * Shared "what does this property's stored value actually mean" formatting -
 * the raw tier name (PUBLIC/ALLIES/TEAM/PRIVATE) for privacy-mode properties,
 * translated "Protected"/"Unprotected" for plain booleans. Every screen/widget
 * that shows a property's value (Region Settings, the private chunk override
 * screen, the map hover info panel) uses this same helper so they all read
 * identically regardless of who's looking - there's no per-viewer variant of
 * this text, only of the optional "+You" access-list annotation some callers
 * add on top (see ProtectionInfoLines).
 */
public final class ProtectionValueFormat {
    private ProtectionValueFormat() {
    }

    public static Component formatValue(String serialized, ProtectionProperty property) {
        if (property.isPrivacyMode()) {
            return Component.literal(PrivacyLevel.valueOf(serialized).name());
        }
        boolean allowed = "true".equals(serialized);
        return Component.translatable(allowed ? "gui.lc_ftb_hook.protection_unprotected" : "gui.lc_ftb_hook.protection_protected");
    }
}
