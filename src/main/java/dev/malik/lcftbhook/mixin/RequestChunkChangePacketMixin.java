package dev.malik.lcftbhook.mixin;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.handler.BulkClaimHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RequestChunkChangePacket.class, remap = false)
public class RequestChunkChangePacketMixin {
    @Inject(
            method = "handle(Ldev/ftb/mods/ftbchunks/net/RequestChunkChangePacket;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;iterator()Ljava/util/Iterator;",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true,
            remap = false
    )
    private static void lcFtbHook$beginBulkOperation(
            RequestChunkChangePacket message,
            dev.architectury.networking.NetworkManager.PacketContext context,
            CallbackInfo ci
    ) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        CommandSourceStack source = player.createCommandSourceStack();
        ChunkTeamData chunkTeamData = BulkClaimHandler.resolveTeamData(message, player);
        if (chunkTeamData == null) {
            return;
        }

        if (BulkClaimHandler.rejectIfInsufficientFunds(message, player, source, chunkTeamData)) {
            ci.cancel();
            return;
        }

        ClaimBatchContext.beginExecution(message.action(), message.chunks().size(), player.getUUID());
    }

    @Inject(
            method = "handle(Ldev/ftb/mods/ftbchunks/net/RequestChunkChangePacket;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At("RETURN"),
            remap = false
    )
    private static void lcFtbHook$flushBulkMessages(
            RequestChunkChangePacket message,
            dev.architectury.networking.NetworkManager.PacketContext context,
            CallbackInfo ci
    ) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            ClaimBatchContext.flush(player);
        }
    }
}
