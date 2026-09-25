package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import dev.malik.lcftbhook.service.UpkeepPriorityService;
import dev.malik.lcftbhook.util.MoneyMessageUtil;
import dev.malik.lcftbhook.util.MoneyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class UpkeepPriorityCommand {
    private UpkeepPriorityCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LCFtbHook.MOD_ID)
                .then(Commands.literal("upkeep_priority")
                        .executes(UpkeepPriorityCommand::showPriority)));
    }

    private static int showPriority(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return 0;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return 0;
        }

        if (team.isPartyTeam() && !BankAccountHelper.canPurchaseForTeam(player.server, team, player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.upkeep_priority.denied"),
                    false
            );
            return 0;
        }

        var entries = UpkeepPriorityService.buildOrder(source.getServer(), team);
        if (entries.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.upkeep_priority.empty"),
                    false
            );
            return 1;
        }

        Component period = ProtectionPriceDisplay.upkeepPeriodLabel();
        MutableComponent message = Component.translatable("message.lc_ftb_hook.upkeep_priority.header")
                .withStyle(ChatFormatting.YELLOW);
        message.append("\n");
        message.append(Component.translatable("message.lc_ftb_hook.upkeep_priority.legend")
                .withStyle(ChatFormatting.GRAY));
        message.append("\n");

        for (UpkeepPriorityService.PriorityEntry entry : entries) {
            // Composed piece-by-piece instead of one flat translatable
            // template string (the old "%1$s %2$s [%3$s] — %4$s / %5$s"
            // format) - that collapsed the priority number, kind tag, cost
            // and period into the message's single default (uncolored)
            // style, reading as an undifferentiated wall of white text
            // (image-12). Each piece keeps its own color here instead,
            // matching the money=GOLD/label=YELLOW/structural=DARK_GRAY
            // convention used throughout UpkeepMessageBuilder.
            String kindKey = switch (entry.kind()) {
                case OUTGOING_WAR -> "message.lc_ftb_hook.upkeep_priority.kind.war";
                case FORCE_LOAD -> "message.lc_ftb_hook.upkeep_priority.kind.force_load";
                case PROTECTION -> "message.lc_ftb_hook.upkeep_priority.kind.protection";
            };
            ChatFormatting kindColor = switch (entry.kind()) {
                case OUTGOING_WAR -> ChatFormatting.LIGHT_PURPLE;
                case FORCE_LOAD -> ChatFormatting.RED;
                case PROTECTION -> ChatFormatting.AQUA;
            };
            message.append(Component.literal("#" + entry.priority() + " ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(entry.label());
            message.append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.translatable(kindKey).withStyle(kindColor));
            message.append(Component.literal("] — ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(entry.costCopper())).copy().withStyle(ChatFormatting.GOLD));
            message.append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(period.copy().withStyle(ChatFormatting.GRAY));
            message.append("\n");
        }

        player.displayClientMessage(message, false);
        return 1;
    }
}
