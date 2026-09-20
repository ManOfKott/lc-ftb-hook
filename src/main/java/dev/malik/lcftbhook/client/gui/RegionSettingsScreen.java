package dev.malik.lcftbhook.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.NordButton;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.malik.lcftbhook.client.ClientClaimPrices;
import dev.malik.lcftbhook.client.ClientPendingState;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.RenameRegionPayload;
import dev.malik.lcftbhook.network.RequestRegionsPayload;
import dev.malik.lcftbhook.network.SetRegionAllowPrivateSellingPayload;
import dev.malik.lcftbhook.network.SetRegionPropertyPayload;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import dev.malik.lcftbhook.util.MoneyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Per-region protection settings: chunk count shown before the 6 protection
 * rows (explicit requirement), each editable by clicking to cycle its value,
 * with live price-per-chunk and pending-change highlighting.
 */
public class RegionSettingsScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = 22;
    private static final int HEADER_BUTTON_SIZE = 16;
    private static final int INFO_LINE_HEIGHT = 14;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;
    private static final int CONTENT_PAD = 8;

    @Nullable
    private final BaseScreen parent;
    private final UUID regionId;

    private SimpleButton backButton;
    private SimpleButton deleteButton;
    private TextBox renameBox;
    private NordButton renameButton;
    private dev.ftb.mods.ftblibrary.ui.Button upkeepHoverArea;
    private AllowSellingRow allowSellingRow;
    private BuyerAccessRow buyerAccessRow;
    private PropertyRow[] rows;

    public RegionSettingsScreen(@Nullable BaseScreen parent, UUID regionId) {
        this.parent = parent;
        this.regionId = regionId;
    }

    @Override
    public boolean onInit() {
        setWidth(getScreen().getGuiScaledWidth() * 2 / 5);
        setHeight(getScreen().getGuiScaledHeight() * 3 / 5);
        PacketDistributor.sendToServer(new RequestRegionsPayload());
        // Also refreshes minutesUntilNextUpkeep (shown in the upkeep
        // tooltip below) - see the identical comment in RegionListScreen.
        PacketDistributor.sendToServer(new dev.malik.lcftbhook.network.RequestClaimPricesPayload());
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> back());
        add(backButton);

        Region region = region();
        if (region != null && !region.isDefault()) {
            renameBox = new TextBox(this);
            renameBox.setText(region.name());
            renameBox.charLimit = 32;
            add(renameBox);

            renameButton = new NordButton(this, Component.translatable("gui.lc_ftb_hook.regions.rename"), Icons.ACCEPT) {
                @Override
                public void onClicked(MouseButton button) {
                    String name = renameBox.getText().trim();
                    if (!name.isEmpty()) {
                        PacketDistributor.sendToServer(new RenameRegionPayload(regionId, name));
                    }
                }
            };
            add(renameButton);

            deleteButton = new SimpleButton(this, Component.empty(), Icons.BIN, (button, mouseButton) -> {
                PacketDistributor.sendToServer(new dev.malik.lcftbhook.network.DeleteRegionPayload(regionId));
                back();
            }) {
                @Override
                public void addMouseOverText(TooltipList list) {
                    list.add(Component.translatable("gui.lc_ftb_hook.regions.delete"));
                }
            };
            add(deleteButton);
        }

        upkeepHoverArea = new dev.ftb.mods.ftblibrary.ui.Button(this, Component.empty(), Color4I.empty()) {
            @Override
            public void onClicked(MouseButton button) {
            }

            @Override
            public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            }

            @Override
            public void addMouseOverText(TooltipList list) {
                buildUpkeepTooltip(list);
            }
        };
        add(upkeepHoverArea);

        allowSellingRow = new AllowSellingRow(this);
        add(allowSellingRow);

        buyerAccessRow = new BuyerAccessRow(this);
        add(buyerAccessRow);

        rows = new PropertyRow[ProtectionProperty.DEFAULT_ORDER.size()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new PropertyRow(this, ProtectionProperty.DEFAULT_ORDER.get(i));
            add(rows[i]);
        }
    }

    @Nullable
    private Region region() {
        return ClientRegions.get(regionId);
    }

    /**
     * {@code parent} is null when this screen is opened from Xaero's World
     * Map right-click menu (see {@code XaeroMarketplaceMenu}, which has no
     * FTB {@code BaseScreen} to return to) - crashed with an NPE here before,
     * since this used to call {@code parent.openGui()} unconditionally.
     */
    private void back() {
        if (parent != null) {
            parent.openGui();
        } else {
            closeGui(true);
        }
    }

    @Override
    public void alignWidgets() {
        backButton.setPosAndSize(5, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
        if (deleteButton != null) {
            deleteButton.setPosAndSize(width - 5 - HEADER_BUTTON_SIZE, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
        }

        int y = HEADER_HEIGHT + 6;
        if (renameBox != null) {
            int renameWidth = 90;
            renameBox.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2 - renameWidth - 6, 18);
            renameButton.setPosAndSize(width - CONTENT_PAD - renameWidth, y, renameWidth, 18);
            y += 22;
        }

        // Two info lines now (chunk count + upkeep), both drawn in
        // drawForeground rather than as widgets - upkeepHoverArea sits over
        // just the second line so hovering it shows the price breakdown.
        upkeepHoverArea.setPosAndSize(CONTENT_PAD, y + INFO_LINE_HEIGHT, width - CONTENT_PAD * 2, INFO_LINE_HEIGHT);
        y += INFO_LINE_HEIGHT * 2 + 4;

        allowSellingRow.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2, ROW_HEIGHT);
        // setPosAndSize doesn't cascade into a child Panel's own
        // alignWidgets() - without this, every row's click-catcher stayed at
        // its stale default position (see the identical bug/fix in
        // RegionListScreen/ChunkOverrideScreen).
        allowSellingRow.alignWidgets();
        y += ROW_HEIGHT + ROW_GAP;

        buyerAccessRow.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2, ROW_HEIGHT);
        buyerAccessRow.alignWidgets();
        y += ROW_HEIGHT + ROW_GAP + 2;

        for (PropertyRow row : rows) {
            row.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2, ROW_HEIGHT);
            row.alignWidgets();
            y += ROW_HEIGHT + ROW_GAP;
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
        Region region = region();
        String title = region != null ? region.name() : "?";
        theme.drawString(graphics, Component.literal(title), x + w / 2, y + 7, NordColors.SNOW_STORM_0, Theme.CENTERED);

        int infoY = y + HEADER_HEIGHT + 6 + (renameBox != null ? 22 : 0);
        Component chunkCount = Component.translatable("gui.lc_ftb_hook.regions.chunk_count", ClientRegions.chunkCount(regionId));
        theme.drawString(graphics, chunkCount, x + CONTENT_PAD, infoY, NordColors.SNOW_STORM_1, 0);

        long current = ClientRegions.currentUpkeepCopper(regionId);
        long pendingAmount = ClientRegions.pendingUpkeepCopper(regionId);
        Component upkeepText = pendingAmount != current
                ? Component.translatable(
                        "gui.lc_ftb_hook.regions.upkeep_pending",
                        MoneyUtil.textOrFree(current),
                        MoneyUtil.textOrFree(pendingAmount))
                : Component.translatable("gui.lc_ftb_hook.regions.upkeep", MoneyUtil.textOrFree(current));
        theme.drawString(graphics, upkeepText.copy().withStyle(ChatFormatting.GOLD), x + CONTENT_PAD, infoY + INFO_LINE_HEIGHT, NordColors.SNOW_STORM_1, 0);
    }

    /**
     * Detailed "how did we get this number" breakdown for the upkeep line:
     * one line per currently-active (or about-to-be-active) protection
     * property, the billable chunk multiplier, and the resulting total(s) -
     * including how a pending change shifts each of those, when relevant.
     */
    private void buildUpkeepTooltip(TooltipList list) {
        Region region = region();
        if (region == null) {
            return;
        }
        list.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_title").withStyle(ChatFormatting.BOLD));

        boolean anyActiveOrPending = false;
        for (ProtectionProperty property : ProtectionProperty.DEFAULT_ORDER) {
            String liveValue = region.propertyValue(property);
            boolean pending = ClientPendingState.hasPendingRegionProperty(regionId, property);
            String pendingValue = pending ? ClientPendingState.getPendingRegionProperty(regionId, property) : null;
            boolean hasPending = pendingValue != null && !pendingValue.equals(liveValue);
            boolean liveActive = !property.isAtMinimum(liveValue);
            boolean pendingActive = hasPending && !property.isAtMinimum(pendingValue);
            if (!liveActive && !pendingActive) {
                continue;
            }
            anyActiveOrPending = true;
            Long price = ClientClaimPrices.protectionPrice(property.id());
            Component priceText = price != null && price > 0
                    ? MoneyUtil.fromCopper(price).getText()
                    : Component.translatable("gui.lc_ftb_hook.price_free");
            Component propertyLabel = Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + property.id());
            if (hasPending) {
                list.add(Component.literal("- ").append(propertyLabel).append(": ")
                        .append(formatValue(liveValue, property)).append(" → ").append(formatValue(pendingValue, property))
                        .append(Component.literal(" (").append(priceText).append("/chunk)"))
                        .withStyle(ChatFormatting.GOLD));
            } else {
                list.add(Component.literal("- ").append(propertyLabel).append(": ")
                        .append(formatValue(liveValue, property))
                        .append(Component.literal(" (").append(priceText).append("/chunk)"))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (!anyActiveOrPending) {
            list.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_none").withStyle(ChatFormatting.GRAY));
        }

        int billableChunks = ClientRegions.billableChunkCount(regionId);
        int totalChunks = ClientRegions.chunkCount(regionId);
        Component chunkMultiplier = totalChunks != billableChunks
                ? Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_chunks_free", billableChunks, totalChunks)
                : Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_chunks", billableChunks);
        list.add(chunkMultiplier.copy().withStyle(ChatFormatting.GRAY));

        long current = ClientRegions.currentUpkeepCopper(regionId);
        long pendingTotal = ClientRegions.pendingUpkeepCopper(regionId);
        if (pendingTotal != current) {
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.regions.upkeep_tooltip_total_pending",
                    MoneyUtil.textOrFree(current),
                    MoneyUtil.textOrFree(pendingTotal)
            ).withStyle(ChatFormatting.GOLD));
        } else {
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.regions.upkeep_tooltip_total",
                    MoneyUtil.textOrFree(current)
            ).withStyle(ChatFormatting.GOLD));
        }
        list.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_period", ProtectionPriceDisplay.upkeepPeriodLabel())
                .withStyle(ChatFormatting.DARK_GRAY));
        list.add(ProtectionPriceDisplay.nextUpkeepLabel().copy().withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Whether players who privately own a chunk in this Region may resell it
     * ("Sell...") on either map - toggled here, not billed/priced like the
     * protection rows below. New Regions default to true (see
     * {@link Region#create}); disabling it here also hides the right-click
     * "Sell..." option client-side and is re-checked server-side in
     * {@code MarketplaceService#listForSale}.
     */
    private final class AllowSellingRow extends Panel {
        private dev.ftb.mods.ftblibrary.ui.Button clickCatcher;

        AllowSellingRow(Panel panel) {
            super(panel);
        }

        @Override
        public void addWidgets() {
            clickCatcher = new dev.ftb.mods.ftblibrary.ui.Button(this, Component.empty(), Color4I.empty()) {
                @Override
                public void onClicked(MouseButton button) {
                    if (!button.isLeft()) {
                        return;
                    }
                    Region region = region();
                    if (region != null) {
                        PacketDistributor.sendToServer(new SetRegionAllowPrivateSellingPayload(regionId, !region.allowPrivateSelling()));
                    }
                }

                @Override
                public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                }
            };
            add(clickCatcher);
        }

        @Override
        public void alignWidgets() {
            clickCatcher.setPosAndSize(0, 0, width, height);
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            Region region = region();
            boolean allowed = region == null || region.allowPrivateSelling();

            Color4I background = isMouseOver() ? NordColors.POLAR_NIGHT_1 : NordColors.POLAR_NIGHT_2;
            background.withAlpha(180).draw(graphics, x + 1, y, w - 2, h);

            Component label = Component.translatable("gui.lc_ftb_hook.regions.allow_private_selling");
            theme.drawString(graphics, label, x + 6, y + 3, NordColors.SNOW_STORM_0, 0);

            Component valueText = Component.translatable(allowed ? "gui.lc_ftb_hook.regions.selling_enabled" : "gui.lc_ftb_hook.regions.selling_disabled");
            Color4I valueColor = allowed ? NordColors.GREEN : NordColors.RED;
            int textWidth = theme.getStringWidth(valueText);
            theme.drawString(graphics, valueText, x + w - 6 - textWidth, y + 3, valueColor, 0);
        }
    }

    /**
     * Who may buy a marketplace listing within this Region - three tiers,
     * from most to least open: everyone ("All"), the owning team's allies or
     * better ("Allies"), or the owning team's own members only ("Team").
     * Per-region (not team-wide) so different parts of a team's territory
     * can open up to different buyers - see {@code BuyableChunkChecker}
     * (client-side gating) and {@code MarketplaceService#buy} (server-side
     * enforcement), both of which resolve this from the chunk's own Region.
     */
    private final class BuyerAccessRow extends Panel {
        private dev.ftb.mods.ftblibrary.ui.Button clickCatcher;

        BuyerAccessRow(Panel panel) {
            super(panel);
        }

        @Override
        public void addWidgets() {
            clickCatcher = new dev.ftb.mods.ftblibrary.ui.Button(this, Component.empty(), Color4I.empty()) {
                @Override
                public void onClicked(MouseButton button) {
                    if (!button.isLeft()) {
                        return;
                    }
                    Region region = region();
                    if (region != null) {
                        PacketDistributor.sendToServer(new dev.malik.lcftbhook.network.SetRegionBuyerAccessPayload(regionId, nextAccess(region.buyerAccess())));
                    }
                }

                @Override
                public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                }
            };
            add(clickCatcher);
        }

        private String nextAccess(String current) {
            return switch (current) {
                case dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALL -> dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALLIES;
                case dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALLIES -> dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_TEAM;
                default -> dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALL;
            };
        }

        @Override
        public void alignWidgets() {
            clickCatcher.setPosAndSize(0, 0, width, height);
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            Region region = region();
            String access = region != null ? region.buyerAccess() : dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALL;

            Color4I background = isMouseOver() ? NordColors.POLAR_NIGHT_1 : NordColors.POLAR_NIGHT_2;
            background.withAlpha(180).draw(graphics, x + 1, y, w - 2, h);

            Component label = Component.translatable("gui.lc_ftb_hook.regions.buyer_access");
            theme.drawString(graphics, label, x + 6, y + 3, NordColors.SNOW_STORM_0, 0);

            Component valueText = Component.translatable(switch (access) {
                case dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALLIES -> "gui.lc_ftb_hook.marketplace.access_allies";
                case dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_TEAM -> "gui.lc_ftb_hook.marketplace.access_team";
                default -> "gui.lc_ftb_hook.marketplace.access_all";
            });
            int textWidth = theme.getStringWidth(valueText);
            theme.drawString(graphics, valueText, x + w - 6 - textWidth, y + 3, NordColors.SNOW_STORM_1, 0);
        }
    }

    private final class PropertyRow extends Panel {
        private final ProtectionProperty property;
        private dev.ftb.mods.ftblibrary.ui.Button clickCatcher;

        PropertyRow(Panel panel, ProtectionProperty property) {
            super(panel);
            this.property = property;
        }

        @Override
        public void addWidgets() {
            clickCatcher = new dev.ftb.mods.ftblibrary.ui.Button(this, Component.empty(), Color4I.empty()) {
                @Override
                public void onClicked(MouseButton button) {
                    if (button.isLeft()) {
                        cycleValue();
                    }
                }

                @Override
                public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                }

                @Override
                public void addMouseOverText(TooltipList list) {
                    Long price = ClientClaimPrices.protectionPrice(property.id());
                    if (price != null && price > 0) {
                        list.add(Component.translatable(
                                "gui.lc_ftb_hook.protection_price_per_chunk",
                                ProtectionPriceDisplay.formatPricePerChunk(price),
                                ProtectionPriceDisplay.upkeepPeriodLabel()
                        ).withStyle(ChatFormatting.GRAY));
                    }
                }
            };
            add(clickCatcher);
        }

        @Override
        public void alignWidgets() {
            clickCatcher.setPosAndSize(0, 0, width, height);
        }

        private void cycleValue() {
            Region region = region();
            if (region == null) {
                return;
            }
            // Cycle from the PENDING value when one is queued, not the live
            // one - otherwise every click while something's pending kept
            // computing "next" from the unchanged live value and just
            // re-sent the same already-pending target forever, making it
            // impossible to either continue switching (e.g. Allies -> Private)
            // or cycle back around to the live value to discard the pending
            // change. This matches what the row actually displays as the
            // target (see PropertyRow.drawBackground's primaryValue).
            String liveValue = region.propertyValue(property);
            boolean pending = ClientPendingState.hasPendingRegionProperty(regionId, property);
            String pendingValue = pending ? ClientPendingState.getPendingRegionProperty(regionId, property) : null;
            String current = pendingValue != null ? pendingValue : liveValue;
            String next = nextValue(current);
            PacketDistributor.sendToServer(new SetRegionPropertyPayload(regionId, property.id(), next));
        }

        private String nextValue(String current) {
            if (property.isPrivacyMode()) {
                PrivacyLevel mode = PrivacyLevel.valueOf(current);
                PrivacyLevel[] values = PrivacyLevel.values();
                return values[(mode.ordinal() + 1) % values.length].name();
            }
            return "true".equals(current) ? "false" : "true";
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            Region region = region();
            String liveValue = region != null ? region.propertyValue(property) : property.minimumSerialized();
            boolean pending = ClientPendingState.hasPendingRegionProperty(regionId, property);
            String pendingValue = pending ? ClientPendingState.getPendingRegionProperty(regionId, property) : null;
            boolean hasPending = pendingValue != null && !pendingValue.equals(liveValue);

            Color4I background = isMouseOver() ? NordColors.POLAR_NIGHT_1 : NordColors.POLAR_NIGHT_2;
            background.withAlpha(180).draw(graphics, x + 1, y, w - 2, h);

            Component label = Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + property.id());
            theme.drawString(graphics, label, x + 6, y + 3, NordColors.SNOW_STORM_0, 0);

            // Primary value is what will actually end up in effect - the
            // queued/target value while a change is pending, otherwise the
            // live one. "Protected" (non-minimum) is green, "unprotected"
            // (minimum, e.g. Public) is red.
            String primaryValue = hasPending ? pendingValue : liveValue;
            boolean protectedPrimary = !property.isAtMinimum(primaryValue);
            Color4I valueColor = protectedPrimary ? NordColors.GREEN : NordColors.RED;

            int rightX = x + w - 6;
            Component priceSuffix = Component.empty();
            Long price = ClientClaimPrices.protectionPrice(property.id());
            if (price != null && price > 0 && protectedPrimary) {
                priceSuffix = Component.literal(" (+" + MoneyUtil.fromCopper(price).getText().getString() + ")")
                        .withStyle(ChatFormatting.GOLD);
            }

            Component full = formatValue(primaryValue, property).copy().append(priceSuffix);
            int textWidth = theme.getStringWidth(full);
            theme.drawString(graphics, full, rightX - textWidth, y + 3, valueColor, 0);

            if (hasPending) {
                Component pendingLabel = Component.translatable("gui.lc_ftb_hook.pending").withStyle(ChatFormatting.GOLD);
                int pendingWidth = theme.getStringWidth(pendingLabel);
                theme.drawString(graphics, pendingLabel, rightX - pendingWidth, y + 12, NordColors.SNOW_STORM_1, 0);

                Component currentlyText = Component.translatable(
                        "gui.lc_ftb_hook.protection_currently_active",
                        formatValue(liveValue, property)
                ).withStyle(ChatFormatting.GRAY);
                theme.drawString(graphics, currentlyText, x + 6, y + 12, NordColors.SNOW_STORM_1, 0);
            }
        }

    }

    private static Component formatValue(String serialized, ProtectionProperty property) {
        if (property.isPrivacyMode()) {
            return Component.literal(PrivacyLevel.valueOf(serialized).name());
        }
        boolean allowed = "true".equals(serialized);
        return Component.translatable(allowed ? "gui.lc_ftb_hook.protection_unprotected" : "gui.lc_ftb_hook.protection_protected");
    }
}
