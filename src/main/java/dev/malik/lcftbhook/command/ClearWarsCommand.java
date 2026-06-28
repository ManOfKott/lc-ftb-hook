package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.service.WarStateSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
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
        FtbHookSavedData savedData = FtbHookSavedData.get(server);

        List<UUID> affected = new ArrayList<>();
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            UUID teamId = entry.ftbTeamId();
            boolean changed = false;

            if (!entry.warTargets().isEmpty()) {
                savedData.clearWarReferences(teamId);
                changed = true;
            }

            TeamPendingState pending = savedData.getPendingState(teamId);
            if (!pending.pendingWarDeclares().isEmpty() || !pending.pendingWarEnds().isEmpty()) {
                TeamPendingState cleared = pending.copy().withoutWarReferences(teamId);
                for (UUID targetId : new java.util.HashSet<>(pending.pendingWarDeclares())) {
                    cleared = cleared.withoutPendingWarDeclare(targetId);
                }
                for (UUID targetId : new java.util.HashSet<>(pending.pendingWarEnds())) {
                    cleared = cleared.withoutPendingWarEnd(targetId);
                }
                savedData.setPendingState(teamId, cleared);
                changed = true;
            }

            if (changed) {
                affected.add(teamId);
            }
        }

        int count = affected.size();
        for (UUID teamId : affected) {
            WarStateSync.syncToTeam(server, teamId);
        }

        LCFtbHook.LOGGER.info("clear_wars: cleared war state for {} team(s)", count);
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared all wars for " + count + " team(s)."),
                true
        );
        return count;
    }
}
