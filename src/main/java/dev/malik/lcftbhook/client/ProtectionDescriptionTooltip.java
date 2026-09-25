package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Shared "what does this property actually do, and what does it cost"
 * tooltip body - property name, a plain-language description of its effect,
 * the Public/Allies/Team/Private tier legend for the 3 privacy-mode
 * properties, and (only where it's actually true - see the {@code
 * showPrice} parameter) the price. Used by every screen that lets a
 * protection property be edited (Region Settings, the private chunk
 * override screen) so hovering any of them explains the setting instead of
 * just showing its price per chunk.
 */
public final class ProtectionDescriptionTooltip {
    private ProtectionDescriptionTooltip() {
    }

    /** Region Settings - loosening/tightening the region's own baseline is what upkeep actually bills for. */
    public static void append(TooltipList list, ProtectionProperty property) {
        append(list, property, true);
    }

    /**
     * @param showPrice Whether changing this property here has its own
     *                  price. False for the private chunk override screen -
     *                  a private owner loosening their own chunk below the
     *                  region's baseline is free, upkeep only ever bills the
     *                  region's own stored value regardless of any override,
     *                  so showing a price there implied the override itself
     *                  cost something, which it never has.
     */
    public static void append(TooltipList list, ProtectionProperty property, boolean showPrice) {
        list.add(Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + property.id())
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        list.add(Component.translatable("gui.lc_ftb_hook.protection_description." + property.id())
                .withStyle(ChatFormatting.GRAY));
        if (property.isPrivacyMode()) {
            list.add(Component.translatable("gui.lc_ftb_hook.protection_privacy_tier_legend")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        if (!showPrice) {
            list.add(Component.translatable("gui.lc_ftb_hook.protection_override_no_extra_cost")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }

        Long price = ClientClaimPrices.protectionPrice(property.id());
        Component priceValue = price != null && price > 0
                ? ProtectionPriceDisplay.formatPricePerChunk(price).copy()
                        .append(Component.translatable("gui.lc_ftb_hook.protection_price_per_chunk_suffix"))
                        .append(Component.literal(" / "))
                        .append(ProtectionPriceDisplay.upkeepPeriodLabel())
                : Component.translatable("gui.lc_ftb_hook.price_free");
        list.add(priceValue.copy().withStyle(ChatFormatting.GOLD));
    }
}
