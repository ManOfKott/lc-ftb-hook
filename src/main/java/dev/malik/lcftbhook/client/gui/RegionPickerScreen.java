package dev.malik.lcftbhook.client.gui;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.NordButton;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.data.Region;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

/** Small "pick a region" list, opened from "Change region..." in the chunk context menus. */
public class RegionPickerScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_GAP = 2;
    private static final int ROW_STEP = ROW_HEIGHT + ROW_GAP;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int CONTENT_PAD = 8;

    @Nullable
    private final BaseScreen returnTo;
    private final Consumer<UUID> onPicked;

    private SimpleButton backButton;
    private ListPanel listPanel;
    private PanelScrollBar scrollBar;

    public RegionPickerScreen(@Nullable BaseScreen returnTo, Consumer<UUID> onPicked) {
        this.returnTo = returnTo;
        this.onPicked = onPicked;
    }

    @Override
    public boolean onInit() {
        setWidth(Math.min(getScreen().getGuiScaledWidth() - 20, 200));
        setHeight(Math.min(getScreen().getGuiScaledHeight() - 20, 180));
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> back());
        add(backButton);

        listPanel = new ListPanel(this);
        add(listPanel);
        scrollBar = new PanelScrollBar(this, listPanel);
        add(scrollBar);
    }

    @Override
    public void alignWidgets() {
        backButton.setPosAndSize(5, 5, 16, 16);
        int listWidth = Math.max(0, width - CONTENT_PAD * 2 - SCROLLBAR_WIDTH - 2);
        int listHeight = Math.max(0, height - HEADER_HEIGHT - CONTENT_PAD * 2);
        listPanel.setPosAndSize(CONTENT_PAD, HEADER_HEIGHT + CONTENT_PAD, listWidth, listHeight);
        listPanel.alignWidgets();
        scrollBar.setPosAndSize(CONTENT_PAD + listWidth + 2, HEADER_HEIGHT + CONTENT_PAD, SCROLLBAR_WIDTH, listHeight);
    }

    private void back() {
        if (returnTo != null) {
            returnTo.openGui();
        } else {
            closeGui(true);
        }
    }

    @Override
    public void drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(graphics, theme, x, y, w, h);
        theme.drawString(graphics, Component.translatable("gui.lc_ftb_hook.regions.pick_title"), x + w / 2, y + 7, NordColors.SNOW_STORM_0, Theme.CENTERED);
    }

    private final class ListPanel extends Panel {
        ListPanel(BaseScreen screen) {
            super(screen);
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
        }

        @Override
        public void addWidgets() {
            for (UUID regionId : ClientRegions.regionOrder()) {
                Region region = ClientRegions.get(regionId);
                if (region == null) {
                    continue;
                }
                add(new RegionOption(this, region));
            }
        }

        @Override
        public void alignWidgets() {
            int y = 0;
            for (var widget : widgets) {
                widget.setPosAndSize(0, y, width, ROW_HEIGHT);
                y += ROW_STEP;
            }
        }

        @Override
        public int getContentHeight() {
            return widgets.size() * ROW_STEP;
        }
    }

    private final class RegionOption extends NordButton {
        private final Region region;

        RegionOption(Panel panel, Region region) {
            super(panel, Component.literal(region.name()), ItemIcon.getItemIcon(Items.GRASS_BLOCK));
            this.region = region;
        }

        @Override
        public void onClicked(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
            onPicked.accept(region.id());
            back();
        }
    }
}
