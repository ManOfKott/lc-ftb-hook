package dev.malik.lcftbhook.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.malik.lcftbhook.client.ClientLandChunks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Land chunks are tinted with the same team color as build chunks but at a
 * lower opacity, so they are visually distinguishable on the map and in the
 * claim manager.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.map.RenderMapImageTask", remap = false)
public abstract class RenderMapImageTaskLandTintMixin {
    private static final int LAND_CLAIM_ALPHA = 40;

    @WrapOperation(
            method = "runMapTask",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Ldev/ftb/mods/ftbteams/api/Team;getProperty(Ldev/ftb/mods/ftbteams/api/property/TeamProperty;)Ljava/lang/Object;"
                    )
            ),
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/icon/Color4I;withAlpha(I)Ldev/ftb/mods/ftblibrary/icon/Color4I;",
                    ordinal = 0
            ),
            remap = false
    )
    private Color4I lcFtbHook$dimLandClaim(Color4I color, int alpha, Operation<Color4I> original, @Local MapChunk chunk) {
        if (chunk != null && isLandChunk(chunk)) {
            return original.call(color, LAND_CLAIM_ALPHA);
        }
        return original.call(color, alpha);
    }

    private static boolean isLandChunk(MapChunk chunk) {
        if (Minecraft.getInstance().level == null || chunk.getTeam().isEmpty()) {
            return false;
        }
        var pos = chunk.getActualPos();
        return ClientLandChunks.isLand(
                Minecraft.getInstance().level.dimension().location(), pos.x(), pos.z());
    }
}
