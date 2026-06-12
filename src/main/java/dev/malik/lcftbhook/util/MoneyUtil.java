package dev.malik.lcftbhook.util;

import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;

public final class MoneyUtil {
    private MoneyUtil() {
    }

    public static MoneyValue fromCopper(long amount) {
        if (amount <= 0) {
            return MoneyValue.empty();
        }
        return CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount);
    }

    public static boolean canAfford(MoneyValue balance, MoneyValue cost) {
        return !cost.isEmpty() && balance.containsValue(cost);
    }
}
