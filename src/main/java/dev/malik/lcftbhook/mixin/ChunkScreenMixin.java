package dev.malik.lcftbhook.mixin;

import dev.malik.lcftbhook.network.RequestClaimPricesPayload;
import dev.malik.lcftbhook.network.RequestLandChunksPayload;
import dev.malik.lcftbhook.network.RequestPendingStatePayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen")
public class ChunkScreenMixin {
    @Inject(method = "onInit", at = @At("RETURN"))
    private void lcFtbHook$requestClaimPrices(CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            PacketDistributor.sendToServer(new RequestClaimPricesPayload());
            PacketDistributor.sendToServer(new RequestPendingStatePayload());
            PacketDistributor.sendToServer(new RequestLandChunksPayload());
        }
    }
}
