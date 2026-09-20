package dev.malik.lcftbhook.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.NordButton;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.CreateRegionPayload;
import dev.malik.lcftbhook.network.DeleteRegionPayload;
import dev.malik.lcftbhook.network.ReorderRegionsPayload;
import dev.malik.lcftbhook.network.RequestRegionsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lists a team's Regions in dismantle-priority order (top = dismantled
 * first). Rows can be dragged by their grip to reorder; clicking a row opens
 * its {@link RegionSettingsScreen}.
 */
public class RegionListScreen extends BaseScreen {
    private static final Icon DELETE_ICON = ItemIcon.getItemIcon(Items.BARRIER);
    private static final int HEADER_HEIGHT = 22;
    private static final int HEADER_BUTTON_SIZE = 16;
    private static final int INFO_LINE_HEIGHT = 14;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_GAP = 2;
    private static final int ROW_STEP = ROW_HEIGHT + ROW_GAP;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int CONTENT_PAD = 8;
    private static final int FOOTER_HEIGHT = 24;

    private final MyTeamScreen parent;
    private SimpleButton backButton;
    private SimpleButton infoButton;
    private dev.ftb.mods.ftblibrary.ui.Button upkeepHoverArea;
    private dev.ftb.mods.ftblibrary.ui.Button forceLoadHoverArea;
    private RegionListPanel listPanel;
    private PanelScrollBar scrollBar;
    private TextBox createNameBox;
    private NordButton createButton;

    public RegionListScreen(MyTeamScreen parent) {
        this.parent = parent;
    }

    @Override
    public boolean onInit() {
        setWidth(getScreen().getGuiScaledWidth() * 3 / 5);
        setHeight(getScreen().getGuiScaledHeight() * 3 / 5);
        PacketDistributor.sendToServer(new RequestRegionsPayload());
        // Also refreshes minutesUntilNextUpkeep (shown in the upkeep tooltips
        // below) - without this, a player who opened this screen without
        // recently visiting the FTB Chunks claim screen would see a stale
        // countdown from whenever they last synced.
        PacketDistributor.sendToServer(new dev.malik.lcftbhook.network.RequestClaimPricesPayload());
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> parent.openGui());
        add(backButton);

        infoButton = new SimpleButton(this, Component.empty(), Icons.INFO, (button, mouseButton) -> {}) {
            @Override
            public void addMouseOverText(dev.ftb.mods.ftblibrary.util.TooltipList list) {
                list.add(Component.translatable("gui.lc_ftb_hook.regions.info_title").withStyle(ChatFormatting.BOLD));
                list.add(Component.translatable("gui.lc_ftb_hook.regions.info_line1"));
                list.add(Component.translatable("gui.lc_ftb_hook.regions.info_line2"));
                list.add(Component.translatable("gui.lc_ftb_hook.regions.info_line3"));
            }
        };
        add(infoButton);

        upkeepHoverArea = new dev.ftb.mods.ftblibrary.ui.Button(this, Component.empty(), Color4I.empty()) {
            @Override
            public void onClicked(MouseButton button) {
            }

            @Override
            public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            }

            @Override
            public void addMouseOverText(dev.ftb.mods.ftblibrary.util.TooltipList list) {
                buildUpkeepTooltip(list);
            }
        };
        add(upkeepHoverArea);

        forceLoadHoverArea = new dev.ftb.mods.ftblibrary.ui.Button(this, Component.empty(), Color4I.empty()) {
            @Override
            public void onClicked(MouseButton button) {
            }

            @Override
            public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            }

            @Override
            public void addMouseOverText(dev.ftb.mods.ftblibrary.util.TooltipList list) {
                buildForceLoadTooltip(list);
            }
        };
        add(forceLoadHoverArea);

        listPanel = new RegionListPanel(this);
        add(listPanel);
        scrollBar = new PanelScrollBar(this, listPanel);
        add(scrollBar);

        createNameBox = new TextBox(this);
        createNameBox.ghostText = Component.translatable("gui.lc_ftb_hook.regions.create_ghost").getString();
        createNameBox.charLimit = 32;
        add(createNameBox);

        createButton = new NordButton(this, Component.translatable("gui.lc_ftb_hook.regions.create"), Icons.ADD) {
            @Override
            public void onClicked(MouseButton button) {
                String name = createNameBox.getText().trim();
                if (name.isEmpty()) {
                    return;
                }
                PacketDistributor.sendToServer(new CreateRegionPayload(name));
                createNameBox.setText("");
            }
        };
        add(createButton);
    }

    @Override
    public void alignWidgets() {
        backButton.setPosAndSize(5, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
        infoButton.setPosAndSize(width - 5 - HEADER_BUTTON_SIZE, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);

        int infoY = HEADER_HEIGHT + 4;
        upkeepHoverArea.setPosAndSize(CONTENT_PAD, infoY, width - CONTENT_PAD * 2, INFO_LINE_HEIGHT);
        forceLoadHoverArea.setPosAndSize(CONTENT_PAD, infoY + INFO_LINE_HEIGHT, width - CONTENT_PAD * 2, INFO_LINE_HEIGHT);

        int listTop = infoY + INFO_LINE_HEIGHT * 2 + 4;
        int listBottom = height - FOOTER_HEIGHT - CONTENT_PAD;
        int listWidth = Math.max(0, width - CONTENT_PAD * 2 - SCROLLBAR_WIDTH - 2);

        listPanel.setPosAndSize(CONTENT_PAD, listTop, listWidth, Math.max(0, listBottom - listTop));
        listPanel.alignWidgets();
        scrollBar.setPosAndSize(CONTENT_PAD + listWidth + 2, listTop, SCROLLBAR_WIDTH, Math.max(0, listBottom - listTop));

        int footerY = height - FOOTER_HEIGHT + 2;
        int createWidth = 90;
        createButton.setPosAndSize(width - CONTENT_PAD - createWidth, footerY, createWidth, 18);
        createNameBox.setPosAndSize(CONTENT_PAD, footerY, width - CONTENT_PAD * 2 - createWidth - 6, 18);
    }

    public void refreshList() {
        if (listPanel != null) {
            listPanel.refreshWidgets();
            alignWidgets();
        }
    }

    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawBackground(graphics, theme, x, y, w, h);
        NordColors.POLAR_NIGHT_0.draw(graphics, x + 4, y + HEADER_HEIGHT + 2, w - 8, h - HEADER_HEIGHT - 6);
        NordColors.POLAR_NIGHT_2.draw(graphics, x + 4, y + HEADER_HEIGHT + 2, w - 8, 1);
    }

    @Override
    public void drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(graphics, theme, x, y, w, h);
        theme.drawString(
                graphics,
                Component.translatable("gui.lc_ftb_hook.regions.title"),
                x + w / 2,
                y + 7,
                NordColors.SNOW_STORM_0,
                Theme.CENTERED
        );

        dev.malik.lcftbhook.service.UpkeepSummaryService.UpkeepSummary summary = dev.malik.lcftbhook.client.ClientUpkeepSummary.get();
        int infoY = y + HEADER_HEIGHT + 4;

        long upkeepCurrent = summary.protectionCurrentCopper() + summary.warIncomingCurrentCopper() + summary.warOutgoingCurrentCopper();
        long upkeepPending = summary.protectionPendingCopper() + summary.warIncomingPendingCopper() + summary.warOutgoingPendingCopper();
        Component upkeepText = upkeepPending != upkeepCurrent
                ? Component.translatable(
                        "gui.lc_ftb_hook.regions.upkeep_pending",
                        dev.malik.lcftbhook.util.MoneyUtil.textOrFree(upkeepCurrent),
                        dev.malik.lcftbhook.util.MoneyUtil.textOrFree(upkeepPending))
                : Component.translatable("gui.lc_ftb_hook.regions.upkeep", dev.malik.lcftbhook.util.MoneyUtil.textOrFree(upkeepCurrent));
        theme.drawString(graphics, upkeepText.copy().withStyle(ChatFormatting.GOLD), x + CONTENT_PAD, infoY, NordColors.SNOW_STORM_1, 0);

        long forceLoadCurrent = summary.forceLoadCurrentCopper();
        long forceLoadPending = summary.forceLoadPendingCopper();
        Component forceLoadText = forceLoadPending != forceLoadCurrent
                ? Component.translatable(
                        "gui.lc_ftb_hook.regions.force_load_upkeep_pending",
                        dev.malik.lcftbhook.util.MoneyUtil.textOrFree(forceLoadCurrent),
                        dev.malik.lcftbhook.util.MoneyUtil.textOrFree(forceLoadPending))
                : Component.translatable("gui.lc_ftb_hook.regions.force_load_upkeep", dev.malik.lcftbhook.util.MoneyUtil.textOrFree(forceLoadCurrent));
        theme.drawString(graphics, forceLoadText.copy().withStyle(ChatFormatting.GRAY), x + CONTENT_PAD, infoY + INFO_LINE_HEIGHT, NordColors.SNOW_STORM_1, 0);
    }

    private void buildUpkeepTooltip(dev.ftb.mods.ftblibrary.util.TooltipList list) {
        dev.malik.lcftbhook.service.UpkeepSummaryService.UpkeepSummary summary = dev.malik.lcftbhook.client.ClientUpkeepSummary.get();
        list.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_title").withStyle(ChatFormatting.BOLD));

        addBreakdownLine(list, "gui.lc_ftb_hook.regions.upkeep_tooltip_protection", summary.protectionCurrentCopper(), summary.protectionPendingCopper());
        if (summary.warIncomingCount() > 0) {
            addBreakdownLine(list, "gui.lc_ftb_hook.regions.upkeep_tooltip_war_incoming", summary.warIncomingCurrentCopper(), summary.warIncomingPendingCopper(), summary.warIncomingCount());
        }
        if (summary.warOutgoingCount() > 0) {
            addBreakdownLine(list, "gui.lc_ftb_hook.regions.upkeep_tooltip_war_outgoing", summary.warOutgoingCurrentCopper(), summary.warOutgoingPendingCopper(), summary.warOutgoingCount());
        }

        long current = summary.protectionCurrentCopper() + summary.warIncomingCurrentCopper() + summary.warOutgoingCurrentCopper();
        long pending = summary.protectionPendingCopper() + summary.warIncomingPendingCopper() + summary.warOutgoingPendingCopper();
        list.add(totalLine(current, pending).withStyle(ChatFormatting.GOLD));
        list.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_period", dev.malik.lcftbhook.service.ProtectionPriceDisplay.upkeepPeriodLabel())
                .withStyle(ChatFormatting.DARK_GRAY));
        list.add(dev.malik.lcftbhook.service.ProtectionPriceDisplay.nextUpkeepLabel().copy().withStyle(ChatFormatting.DARK_GRAY));
        list.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_not_forceload").withStyle(ChatFormatting.DARK_GRAY));
    }

    private void buildForceLoadTooltip(dev.ftb.mods.ftblibrary.util.TooltipList list) {
        dev.malik.lcftbhook.service.UpkeepSummaryService.UpkeepSummary summary = dev.malik.lcftbhook.client.ClientUpkeepSummary.get();
        list.add(Component.translatable("gui.lc_ftb_hook.regions.force_load_tooltip_title").withStyle(ChatFormatting.BOLD));
        list.add(Component.translatable(
                "gui.lc_ftb_hook.regions.force_load_tooltip_line",
                summary.forceLoadCurrentCount()
        ).withStyle(ChatFormatting.GRAY));
        if (summary.forceLoadPendingCount() != summary.forceLoadCurrentCount()) {
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.regions.force_load_tooltip_pending_count",
                    summary.forceLoadCurrentCount(), summary.forceLoadPendingCount()
            ).withStyle(ChatFormatting.GOLD));
        }
        list.add(totalLine(summary.forceLoadCurrentCopper(), summary.forceLoadPendingCopper()).withStyle(ChatFormatting.GOLD));
        list.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_period", dev.malik.lcftbhook.service.ProtectionPriceDisplay.upkeepPeriodLabel())
                .withStyle(ChatFormatting.DARK_GRAY));
        list.add(dev.malik.lcftbhook.service.ProtectionPriceDisplay.nextUpkeepLabel().copy().withStyle(ChatFormatting.DARK_GRAY));
    }

    private void addBreakdownLine(dev.ftb.mods.ftblibrary.util.TooltipList list, String labelKey, long current, long pending) {
        list.add(Component.literal("- ").append(Component.translatable(labelKey)).append(": ")
                .append(totalLine(current, pending))
                .withStyle(current == pending ? ChatFormatting.GRAY : ChatFormatting.GOLD));
    }

    private void addBreakdownLine(dev.ftb.mods.ftblibrary.util.TooltipList list, String labelKey, long current, long pending, int count) {
        list.add(Component.literal("- ").append(Component.translatable(labelKey, count)).append(": ")
                .append(totalLine(current, pending))
                .withStyle(current == pending ? ChatFormatting.GRAY : ChatFormatting.GOLD));
    }

    private net.minecraft.network.chat.MutableComponent totalLine(long current, long pending) {
        return current == pending
                ? dev.malik.lcftbhook.util.MoneyUtil.textOrFree(current).copy()
                : Component.literal("").append(dev.malik.lcftbhook.util.MoneyUtil.textOrFree(current))
                        .append(" -> ").append(dev.malik.lcftbhook.util.MoneyUtil.textOrFree(pending));
    }

    private final class RegionListPanel extends Panel {
        private final Map<UUID, RegionRow> rowsByRegion = new LinkedHashMap<>();
        @Nullable
        private List<UUID> dragOrder;

        RegionListPanel(BaseScreen screen) {
            super(screen);
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
        }

        @Override
        public void addWidgets() {
            rowsByRegion.clear();
            for (UUID id : ClientRegions.regionOrder()) {
                Region region = ClientRegions.get(id);
                if (region == null) {
                    continue;
                }
                RegionRow row = new RegionRow(this, region);
                rowsByRegion.put(id, row);
                add(row);
            }
        }

        @Override
        public void alignWidgets() {
            int y = 0;
            for (UUID id : currentOrder()) {
                RegionRow row = rowsByRegion.get(id);
                if (row == null) {
                    continue;
                }
                row.setPosAndSize(0, y, width, ROW_HEIGHT);
                // setPosAndSize does NOT cascade into a child Panel's own
                // alignWidgets() - without this, openButton/dragHandle/
                // deleteButton stayed at their stale (effectively
                // uninitialized) positions forever, even though the row
                // itself rendered in the right place (its own bounds come
                // straight from setPosAndSize above; its CHILDREN's bounds
                // don't move until alignWidgets() explicitly repositions
                // them). That's why hover highlighting on the row always
                // looked correct while every click inside it missed.
                row.alignWidgets();
                y += ROW_STEP;
            }
        }

        List<UUID> currentOrder() {
            return dragOrder != null ? dragOrder : ClientRegions.regionOrder();
        }

        void beginDrag() {
            dragOrder = new ArrayList<>(ClientRegions.regionOrder());
        }

        void dragTo(UUID regionId, int targetIndex) {
            if (dragOrder == null) {
                return;
            }
            int index = dragOrder.indexOf(regionId);
            if (index < 0) {
                return;
            }
            int clamped = Math.max(0, Math.min(dragOrder.size() - 1, targetIndex));
            if (index == clamped) {
                return;
            }
            dragOrder.remove(index);
            dragOrder.add(clamped, regionId);
            alignWidgets();
        }

        void endDrag() {
            if (dragOrder != null) {
                PacketDistributor.sendToServer(new ReorderRegionsPayload(dragOrder));
                dragOrder = null;
                alignWidgets();
            }
        }

        @Override
        public int getContentHeight() {
            return rowsByRegion.size() * ROW_STEP;
        }
    }

    private final class RegionRow extends Panel {
        private final Region region;
        private SimpleButton deleteButton;
        private DragHandle dragHandle;
        private dev.ftb.mods.ftblibrary.ui.Button openButton;

        RegionRow(Panel panel, Region region) {
            super(panel);
            this.region = region;
        }

        @Override
        public void addWidgets() {
            openButton = new dev.ftb.mods.ftblibrary.ui.Button(this, Component.empty(), Color4I.empty()) {
                @Override
                public void onClicked(MouseButton button) {
                    if (button.isLeft()) {
                        new RegionSettingsScreen(RegionListScreen.this, region.id()).openGui();
                    }
                }

                @Override
                public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                }
            };
            add(openButton);

            dragHandle = new DragHandle(this);
            add(dragHandle);

            if (!region.isDefault()) {
                deleteButton = new SimpleButton(this, Component.empty(), DELETE_ICON, (button, mouseButton) ->
                        PacketDistributor.sendToServer(new DeleteRegionPayload(region.id()))) {
                    @Override
                    public void addMouseOverText(dev.ftb.mods.ftblibrary.util.TooltipList list) {
                        list.add(Component.translatable("gui.lc_ftb_hook.regions.delete"));
                    }
                };
                add(deleteButton);
            }
        }

        @Override
        public void alignWidgets() {
            int rightControlsWidth = 16 + (deleteButton != null ? 20 : 0);
            dragHandle.setPosAndSize(2, 0, 14, height);
            openButton.setPosAndSize(18, 0, Math.max(0, width - 18 - rightControlsWidth), height);
            if (deleteButton != null) {
                deleteButton.setPosAndSize(width - 20, (height - 16) / 2, 16, 16);
            }
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            Color4I background = isMouseOver() ? NordColors.POLAR_NIGHT_1 : NordColors.POLAR_NIGHT_2;
            background.withAlpha(180).draw(graphics, x + 1, y, w - 2, h);

            int textX = x + 18;
            Component name = Component.literal(region.name())
                    .withStyle(region.isDefault() ? ChatFormatting.GRAY : ChatFormatting.WHITE);
            theme.drawString(graphics, name, textX, y + 4, NordColors.SNOW_STORM_0, 0);

            Component count = Component.translatable(
                    "gui.lc_ftb_hook.regions.chunk_count",
                    ClientRegions.chunkCount(region.id())
            ).withStyle(ChatFormatting.GRAY);
            theme.drawString(graphics, count, textX, y + 13, NordColors.POLAR_NIGHT_4, 0);
        }

        /**
         * A dedicated child widget, occupying only its own small strip of
         * the row (never overlapping {@code openButton}/{@code deleteButton}),
         * so its drag handling can't intercept clicks meant for them - unlike
         * the original implementation, which hand-checked mouse coordinates
         * directly in the ROW's own {@code mousePressed} and ended up
         * swallowing every click on the row, not just ones on the grip.
         */
        private final class DragHandle extends dev.ftb.mods.ftblibrary.ui.Widget {
            private boolean dragging;
            private int dragStartMouseY;
            private int dragStartIndex;

            DragHandle(Panel panel) {
                super(panel);
            }

            @Override
            public boolean mousePressed(MouseButton button) {
                if (!button.isLeft() || !isMouseOver()) {
                    return false;
                }
                dragging = true;
                dragStartMouseY = getMouseY();
                dragStartIndex = listPanel.currentOrder().indexOf(region.id());
                listPanel.beginDrag();
                return true;
            }

            @Override
            public boolean mouseDragged(int button, double dragX, double dragY) {
                if (!dragging) {
                    return false;
                }
                int deltaSteps = Math.round((getMouseY() - dragStartMouseY) / (float) ROW_STEP);
                listPanel.dragTo(region.id(), dragStartIndex + deltaSteps);
                return true;
            }

            @Override
            public void mouseReleased(MouseButton button) {
                if (dragging) {
                    dragging = false;
                    listPanel.endDrag();
                }
            }

            @Override
            public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                Component grip = Component.literal("≡")
                        .withStyle(isMouseOver() || dragging ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
                theme.drawString(graphics, grip, x + 2, y + (h - 8) / 2, 0);
            }
        }
    }
}
