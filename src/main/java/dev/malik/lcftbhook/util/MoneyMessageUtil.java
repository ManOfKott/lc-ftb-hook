package dev.malik.lcftbhook.util;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;

public final class MoneyMessageUtil {
    private MoneyMessageUtil() {
    }

    public static Component formatBalance(IBankAccount account) {
        if (account.getMoneyStorage().isEmpty()) {
            return Component.translatable("message.lc_ftb_hook.balance_empty");
        }
        return account.getMoneyStorage().getAllValueText();
    }

    public static Component formatValue(MoneyValue value) {
        if (value.isEmpty()) {
            return Component.translatable("message.lc_ftb_hook.balance_empty");
        }
        return value.getText();
    }
}
