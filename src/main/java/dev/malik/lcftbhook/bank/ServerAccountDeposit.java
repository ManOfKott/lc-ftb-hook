package dev.malik.lcftbhook.bank;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;

/**
 * Deposits an already-withdrawn "sink" payment (upkeep, chunk-claim price,
 * ...) into {@link ServerAccountHelper}'s account instead of letting the
 * money vanish, and logs it there the same way {@code UpkeepSettlementService}
 * already logs the withdrawal on the payer's own side.
 * <p>
 * Guards against LC's own overflow bug: decompiling {@code CoinValue.addValue}
 * showed it's plain {@code long + long} with no overflow check at all (unlike
 * {@code multiplyValue}, which does clamp to {@code Long.MAX_VALUE}) - a
 * deposit that pushed the balance past {@code Long.MAX_VALUE} copper would
 * silently wrap the account balance NEGATIVE. In practice that ceiling is
 * astronomically far away for any real server's upkeep/claim income, but
 * since it's a real landmine in LC itself, not just a theoretical one, this
 * checks for it before every deposit and simply skips the deposit (burning
 * the money, exactly like every sink did before this account existed) rather
 * than risk it.
 */
public final class ServerAccountDeposit {
    private ServerAccountDeposit() {
    }

    public static void deposit(MoneyValue amount, String sourceLabel) {
        if (amount.isEmpty()) {
            return;
        }
        IBankAccount account = ServerAccountHelper.get();
        if (account == null) {
            return;
        }

        long currentCopper = MoneyUtil.mainChainValue(account).getCoreValue();
        long incomingCopper = amount.getCoreValue();
        long newTotal;
        try {
            newTotal = Math.addExact(currentCopper, incomingCopper);
        } catch (ArithmeticException overflow) {
            LCFtbHook.LOGGER.warn(
                    "Server account balance would overflow past Long.MAX_VALUE copper (currently {} + {} incoming) - "
                            + "burning this payment instead of risking a negative balance.",
                    currentCopper, incomingCopper
            );
            return;
        }
        if (newTotal < 0) {
            // Belt and suspenders: Math.addExact already catches the normal
            // overflow case, but guard the resulting sign too in case some
            // future chain/denomination change lets a negative slip through
            // some other path.
            LCFtbHook.LOGGER.warn("Server account deposit produced a negative total ({}) - burning this payment instead.", newTotal);
            return;
        }

        account.depositMoney(amount);
        BankTransactionLog.logDeposit(account, sourceLabel, amount);
    }
}
