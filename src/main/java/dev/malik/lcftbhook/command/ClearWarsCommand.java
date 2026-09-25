package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.WarService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.UUID;

public final class ClearWarsCommand {
    private ClearWarsCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal(LCFtbHook.MOD_ID)
                        .then(Commands.literal("clear_wars")
                                .requires(src -> src.hasPermission(2))
                                .executes(ClearWarsCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        List<UUID> affected = WarService.clearAllWars(server);
        int count = affected.size();

        LCFtbHook.LOGGER.info("clear_wars: cleared war state for {} team(s)", count);
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared all wars for " + count + " team(s)."),
                true
        );
        return count;
    }
}
