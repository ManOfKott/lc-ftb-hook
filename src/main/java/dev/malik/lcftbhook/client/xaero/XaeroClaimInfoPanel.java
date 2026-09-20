package dev.malik.lcftbhook.client.xaero;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.malik.lcftbhook.client.ProtectionInfoLines;
import dev.malik.lcftbhook.client.ProtectionInfoPanelRenderer;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Optional;

/**
 * Fixed-position info panel on the left side of the World Map, shown while
 * hovering a claimed chunk - replaces ftbxaerocompat's plain bottom-center
 * tooltip (suppressed in {@link ClaimsHighlighterTooltipMixin}) with owner
 * (+ skin head for private owners), region, for-sale status/price, and every
 * protection property.
 * <p>
 * Plain (non-mixin) class - see {@code XaeroMarketplaceMenu}'s javadoc for
 * why {@code GuiMapClaimInfoPanelMixin} only calls into this via reflection
 * instead of containing this logic itself.
 */
public final class XaeroClaimInfoPanel {
    static final int PANEL_X = 8;
    static final int PANEL_Y = 60;
    private static final int PANEL_WIDTH = 170;

    private XaeroClaimInfoPanel() {
    }

    public static void render(GuiGraphics graphics, int mouseBlockPosX, int mouseBlockPosZ) {
        // Always-visible money panel (claim price/balance/force-load
        // upkeep), with the hover panel stacked directly below it.
        var moneyContent = new ProtectionInfoLines.Content(dev.malik.lcftbhook.client.ClientClaimPrices.moneyLines(false), null);
        int moneyHeight = ProtectionInfoPanelRenderer.height(moneyContent);
        int hoverPanelY = PANEL_Y;
        if (moneyHeight > 0) {
            ProtectionInfoPanelRenderer.draw(graphics, moneyContent, PANEL_X, PANEL_Y, PANEL_WIDTH);
            hoverPanelY = PANEL_Y + moneyHeight + 4;
        }

        Optional<MapDimension> dimOpt = MapDimension.getCurrent();
        if (dimOpt.isEmpty()) {
            return;
        }
        MapDimension dim = dimOpt.get();
        int chunkX = mouseBlockPosX >> 4;
        int chunkZ = mouseBlockPosZ >> 4;
        var mapRegion = dim.getRegion(XZ.regionFromChunk(chunkX, chunkZ));
        MapChunk chunk = mapRegion != null ? mapRegion.getChunkForAbsoluteChunkPos(XZ.of(chunkX, chunkZ)) : null;

        ProtectionInfoLines.Content content = ProtectionInfoLines.build(chunk, dim.dimension, chunkX, chunkZ);
        ProtectionInfoPanelRenderer.draw(graphics, content, PANEL_X, hoverPanelY, PANEL_WIDTH);
    }
}
