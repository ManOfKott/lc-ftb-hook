package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class EditConfigScreenUiHelper {
    public static final int TITLE_Y = 2;
    public static final int TITLE_HEIGHT = 14;
    public static final int NOTE_LINE1_Y = 18;
    public static final int NOTE_LINE2_Y = 28;
    public static final int NOTE_LINE_HEIGHT = 9;
    /** Extra height added to the default 20px FTB top panel. */
    public static final int TOP_PANEL_EXTRA_HEIGHT = NOTE_LINE2_Y + NOTE_LINE_HEIGHT - 20;

    private EditConfigScreenUiHelper() {
    }

    public static boolean isFtbChunksPropertiesTitle(Component title) {
        String text = title.getString().toLowerCase();
        return text.contains("ftb chunks")
                || text.contains("chunk")
                || text.contains("team properties");
    }

    public static void drawProtectionPricesNote(GuiGraphics graphics, Theme theme, int panelX, int panelY, int panelWidth) {
        int noteX = panelX + 6;
        int maxWidth = panelWidth - 12;

        Component line1 = Component.translatable("gui.lc_ftb_hook.protection_prices_note_line1")
                .withStyle(ChatFormatting.GRAY);
        Component line2 = Component.translatable(
                "gui.lc_ftb_hook.protection_prices_note_line2",
                ProtectionPriceDisplay.upkeepPeriodLabel(),
                ProtectionPriceDisplay.landChunkGroupSize()
        ).withStyle(ChatFormatting.GRAY);

        drawFittedString(graphics, theme, line1, noteX, panelY + NOTE_LINE1_Y, maxWidth);
        drawFittedString(graphics, theme, line2, noteX, panelY + NOTE_LINE2_Y, maxWidth);
    }

    private static void drawFittedString(
            GuiGraphics graphics,
            Theme theme,
            Component text,
            int x,
            int y,
            int maxWidth
    ) {
        Component draw = theme.getStringWidth(text) <= maxWidth
                ? text
                : Component.literal(theme.trimStringToWidth(text.getString(), maxWidth - 6) + "...")
                .withStyle(text.getStyle());
        theme.drawString(graphics, draw, x, y, Color4I.rgb(0xAAAAAA), 0);
    }
}
