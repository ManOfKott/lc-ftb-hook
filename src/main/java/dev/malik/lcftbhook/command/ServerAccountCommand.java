package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.bank.ServerAccountHelper;
import dev.malik.lcftbhook.util.MoneyMessageUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.common.data.types.BankDataCache;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Op-facing access to {@link ServerAccountHelper}'s pooled bank account (see
 * SPEC.md's "server money account" feature). Reports the balance, and also
 * points the invoking op's currently-selected LC bank account at it - so
 * opening any existing ATM right after running this shows/manages this
 * account instead of the op's own personal one. Real interactive access
 * (deposit/withdraw coins) then goes entirely through LC's own ATM UI, not a
 * custom screen - {@code PlayerBankReferenceOpAccessMixin} grants any op
 * direct access to this specific account, no Admin Mode toggle needed.
 */
public final class ServerAccountCommand {
    private ServerAccountCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LCFtbHook.MOD_ID)
                .then(Commands.literal("server_account")
                        .requires(src -> src.hasPermission(2))
                        .executes(ServerAccountCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        IBankAccount account = ServerAccountHelper.get();
        if (account == null) {
            source.sendFailure(Component.literal("Server account is unavailable right now."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Server account balance: ")
                .withStyle(ChatFormatting.GRAY)
                .append(MoneyMessageUtil.formatBalance(account).copy().withStyle(ChatFormatting.GOLD)), false);

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            BankDataCache data = BankDataCache.TYPE.get(false);
            if (data != null) {
                data.setSelectedAccount(player, ServerAccountHelper.reference());
                source.sendSuccess(() -> Component.literal(
                        "Selected as your active account - open any ATM to view/manage it."
                ).withStyle(ChatFormatting.GRAY), false);
            }
        }

        return 1;
    }
}
