package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.UpkeepService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Debug/testing command: makes every tracked team's upkeep settlement run on the very next server tick. */
public final class ForceUpkeepCommand {
    private ForceUpkeepCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LCFtbHook.MOD_ID)
                .then(Commands.literal("force_upkeep")
                        .requires(src -> src.hasPermission(2))
                        .executes(ForceUpkeepCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        UpkeepService.forceNextUpkeep();
        context.getSource().sendSuccess(
                () -> Component.literal("Upkeep settlement for all tracked teams will run on the next server tick."),
                true
        );
        return 1;
    }
}
