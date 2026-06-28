package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.service.TestTeamSeedService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SeedTestTeamsCommand {
    private SeedTestTeamsCommand() {
    }

    private static boolean debugCommandsEnabled() {
        return LCFtbHookConfig.SERVER.debugTestTeamCommands.get();
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LCFtbHook.MOD_ID)
                .then(Commands.literal("seed_test_teams")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> seed(context, TestTeamSeedService.DEFAULT_COUNT))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> seed(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("clear_test_teams")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> clear(context, 0))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> clear(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("count_test_teams")
                        .requires(source -> source.hasPermission(2))
                        .executes(SeedTestTeamsCommand::count)));
    }

    private static int count(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!debugCommandsEnabled()) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.test_teams.debug_disabled"));
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.count_test_teams.unavailable"));
            return 0;
        }

        TestTeamSeedService.CountResult result = TestTeamSeedService.count(source.getServer());
        source.sendSuccess(
                () -> Component.translatable(
                        "message.lc_ftb_hook.count_test_teams.done",
                        result.total(),
                        result.withClaims(),
                        TestTeamSeedService.DEFAULT_COUNT,
                        result.inDefaultRange()
                ),
                false
        );
        return result.total();
    }

    private static int seed(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!debugCommandsEnabled()) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.test_teams.debug_disabled"));
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.seed_test_teams.unavailable"));
            return 0;
        }

        TestTeamSeedService.SeedResult result = TestTeamSeedService.seed(source.getServer(), source, count);
        source.sendSuccess(
                () -> Component.translatable(
                        "message.lc_ftb_hook.seed_test_teams.done",
                        result.created(),
                        result.skipped(),
                        result.failed(),
                        result.incomingWars(),
                        result.outgoingWars(),
                        result.availableTargets()
                ),
                true
        );
        return result.created() > 0 ? result.created() : (result.skipped() > 0 ? 1 : 0);
    }

    private static int clear(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();
        if (!debugCommandsEnabled()) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.test_teams.debug_disabled"));
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.clear_test_teams.unavailable"));
            return 0;
        }

        TestTeamSeedService.ClearResult result = TestTeamSeedService.clear(source.getServer(), source, count);
        source.sendSuccess(
                () -> Component.translatable(
                        "message.lc_ftb_hook.clear_test_teams.done",
                        result.deleted(),
                        result.skipped(),
                        result.failed()
                ),
                true
        );
        return result.deleted() > 0 ? result.deleted() : (result.failed() == 0 && result.skipped() == 0 ? 0 : 1);
    }
}
