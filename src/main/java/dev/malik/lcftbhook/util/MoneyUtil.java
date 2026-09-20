package dev.malik.lcftbhook.util;

import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValuePair;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MoneyUtil {
    private MoneyUtil() {
    }

    public static MoneyValue fromCopper(long amount) {
        if (amount <= 0) {
            return MoneyValue.empty();
        }
        return CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount);
    }

    /**
     * Whether any part of this value was entered using an Emerald-based
     * denomination - covers BOTH of Lightman's Currency's independent
     * chains that touch emeralds: the "main" chain's own
     * {@code lightmanscurrency:coin_emerald} (a custom coin/pile/block
     * item, not vanilla) AND the separate "emeralds" chain, which is priced
     * directly in real vanilla {@code minecraft:emerald}/
     * {@code minecraft:emerald_block} (confirmed via the generated
     * MasterCoinList.json - a distinct top-level chain, not a denomination
     * of "main"). Both chains' denomination ladders are global config, not
     * something a single {@code MoneyValueWidget} instance can restrict, so
     * this is checked at confirm time instead - see
     * ChunkSalePriceScreen/ExpropriatePriceScreen.
     */
    public static boolean usesEmeraldCoinDenomination(MoneyValue value) {
        if (!(value instanceof CoinValue coinValue)) {
            return false;
        }
        for (CoinValuePair pair : coinValue.getEntries()) {
            if (pair.amount == 0) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(pair.coin);
            if (id.getNamespace().equals("lightmanscurrency") && id.getPath().contains("emerald")) {
                return true;
            }
            if (id.getNamespace().equals("minecraft") && (id.getPath().equals("emerald") || id.getPath().equals("emerald_block"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code fromCopper(0).getText()} renders as empty text (an empty
     * {@link MoneyValue} has nothing to format) - anywhere a price/upkeep
     * total is displayed standalone (not as a "+X" suffix that can just
     * vanish when there's nothing to add), a genuine zero still needs to
     * show *something*, so this falls back to the same "Free" label
     * {@code ClientClaimPrices} already uses for a free claim.
     */
    public static Component textOrFree(long amount) {
        if (amount <= 0) {
            return Component.translatable("gui.lc_ftb_hook.price_free");
        }
        return fromCopper(amount).getText();
    }
}
