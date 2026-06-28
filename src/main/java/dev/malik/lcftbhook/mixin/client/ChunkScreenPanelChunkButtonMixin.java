package dev.malik.lcftbhook.mixin.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.ImageIcon;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.malik.lcftbhook.client.ClientLandChunks;
import dev.malik.lcftbhook.client.ClientPendingState;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.client.ChunkScreenPanelAltToggleAccess;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel$ChunkButton", remap = false)
public class ChunkScreenPanelChunkButtonMixin {
    private static final ImageIcon CHECKERED = new ImageIcon(
            ResourceLocation.fromNamespaceAndPath("ftbchunks", "textures/checkered.png")
    );

    @Shadow(remap = false)
    private dev.ftb.mods.ftblibrary.math.XZ chunkPos;

    @Shadow(remap = false)
    private dev.ftb.mods.ftbchunks.client.map.MapChunk chunk;

    @Final
    @Shadow(remap = false)
    private dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel this$0;

    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$altClickToggleChunkType(MouseButton mouseButton, CallbackInfo ci) {
        if (!Screen.hasAltDown() || !mouseButton.isLeft() || chunk == null || chunkPos == null) {
            return;
        }
        ci.cancel();
        if (chunk.getClaimedDate().isEmpty()) {
            return;
        }
        ((ChunkScreenPanelAltToggleAccess) this$0).lcFtbHook$selectForAltToggle(chunkPos);
    }

    @Inject(method = "addMouseOverText", at = @At("RETURN"), remap = false)
    private void lcFtbHook$addPendingForceLoadTooltip(
            dev.ftb.mods.ftblibrary.util.TooltipList list,
            CallbackInfo ci
    ) {
        if (chunk == null || chunkPos == null) {
            return;
        }

        ResourceKey<Level> dimension = ((ChunkScreenPanelAccessor) this$0).lcFtbHook$getChunkScreen().getDimension().dimension;
        int chunkX = chunkPos.x();
        int chunkZ = chunkPos.z();

        if (chunk.getClaimedDate().isPresent()) {
            boolean land = ClientLandChunks.isLand(dimension, chunkX, chunkZ);
            list.add(Component.translatable(land
                    ? "gui.lc_ftb_hook.chunk_type_land"
                    : "gui.lc_ftb_hook.chunk_type_build").withStyle(ChatFormatting.AQUA));
            list.add(Component.translatable("gui.lc_ftb_hook.chunk_type_hint").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (ClientPendingState.isPendingForceLoad(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_forceload",
                    ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
            return;
        }

        if (ClientPendingState.isPendingForceUnload(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_forceunload",
                    ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
            return;
        }

        if (ClientPendingState.isPendingLandChunk(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_land",
                    ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
            return;
        }

        if (ClientPendingState.isPendingBuildChunk(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_build",
                    ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
        }
    }

    @Inject(method = "drawBackground", at = @At("RETURN"), remap = false)
    private void lcFtbHook$drawPendingChunkPattern(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int w,
            int h,
            CallbackInfo ci
    ) {
        ResourceKey<Level> dimension = ((ChunkScreenPanelAccessor) this$0).lcFtbHook$getChunkScreen().getDimension().dimension;
        int chunkX = chunkPos.x();
        int chunkZ = chunkPos.z();

        if (ClientPendingState.isPendingForceLoad(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(0xFFB74D).withAlpha(170));
            return;
        }

        if (ClientPendingState.isPendingForceUnload(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(0xEF5350).withAlpha(170));
            return;
        }

        if (ClientPendingState.isPendingLandChunk(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(0x81C784).withAlpha(170));
            return;
        }

        if (ClientPendingState.isPendingBuildChunk(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(0x64B5F6).withAlpha(170));
        }
    }

    private static void drawPattern(GuiGraphics graphics, int x, int y, int w, int h, Color4I color) {
        CHECKERED.withColor(color).draw(graphics, x, y, w, h);
    }
}
