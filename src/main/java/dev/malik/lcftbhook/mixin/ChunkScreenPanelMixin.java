package dev.malik.lcftbhook.mixin;

import dev.malik.lcftbhook.client.ClientClaimPrices;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel", remap = false)
public class ChunkScreenPanelMixin {
    @Unique
    private static final ThreadLocal<Boolean> lcFtbHook$skipProblemCount = ThreadLocal.withInitial(() -> false);

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbchunks/client/gui/ChunkScreenPanel$ChunkUpdateInfo;summary()Lnet/minecraft/network/chat/Component;"
            )
    )
    private Component lcFtbHook$hideChunkModifiedSummary(
            dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel.ChunkUpdateInfo updateInfo
    ) {
        return Component.empty();
    }

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"
            )
    )
    private MutableComponent lcFtbHook$formatClaimProblem(String key) {
        if (ClientClaimPrices.isLcClaimResult(key)) {
            lcFtbHook$skipProblemCount.set(true);
            return ClientClaimPrices.claimProblemLine(key);
        }
        lcFtbHook$skipProblemCount.set(false);
        return Component.translatable(key);
    }

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/MutableComponent;append(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"
            )
    )
    private MutableComponent lcFtbHook$hideProblemChunkCount(MutableComponent component, String suffix) {
        if (Boolean.TRUE.equals(lcFtbHook$skipProblemCount.get())) {
            lcFtbHook$skipProblemCount.set(false);
            return component;
        }
        return component.append(suffix);
    }
}
