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
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.client.ClientRegionMembership;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.SetChunkLabelPayload;
import dev.malik.lcftbhook.network.SetChunkOverridePayload;
import dev.malik.lcftbhook.service.ProtectionResolution;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Single-chunk protection settings for a private owner (no batch - one
 * chunk at a time). Each property can only be loosened below the owning
 * region's baseline, never tightened; clicking cycles down to unprotected
 * then wraps back to the region's own baseline value.
 */
public class ChunkOverrideScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;
    private static final int CONTENT_PAD = 8;
    private static final int LABEL_ROW_HEIGHT = 22;

    private final BaseScreen returnTo;
    private final String chunkKey;
    private SimpleButton backButton;
    private TextBox labelBox;
    private NordButton labelButton;
    private PropertyRow[] rows;

    public ChunkOverrideScreen(BaseScreen returnTo, String chunkKey) {
        this.returnTo = returnTo;
        this.chunkKey = chunkKey;
    }

    @Override
    public boolean onInit() {
        setWidth(Math.min(getScreen().getGuiScaledWidth() - 20, 220));
        setHeight(30 + LABEL_ROW_HEIGHT + ProtectionProperty.DEFAULT_ORDER.size() * (ROW_HEIGHT + ROW_GAP));
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> back());
        add(backButton);

        labelBox = new TextBox(this);
        String currentLabel = ClientChunkOwnership.get(chunkKey).label();
        labelBox.setText(currentLabel != null ? currentLabel : "");
        labelBox.ghostText = Component.translatable("gui.lc_ftb_hook.marketplace.label_ghost").getString();
        labelBox.charLimit = 24;
        add(labelBox);

        labelButton = new NordButton(this, Component.translatable("gui.lc_ftb_hook.marketplace.label_set"), Icons.ACCEPT) {
            @Override
            public void onClicked(MouseButton button) {
                PacketDistributor.sendToServer(new SetChunkLabelPayload(chunkKey, labelBox.getText().trim()));
            }
        };
        add(labelButton);

        rows = new PropertyRow[ProtectionProperty.DEFAULT_ORDER.size()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new PropertyRow(this, ProtectionProperty.DEFAULT_ORDER.get(i));
            add(rows[i]);
        }
    }

    @Nullable
    private Region region() {
        Region defaultRegion = ClientRegions.getDefault();
        var regionId = ClientRegionMembership.regionOf(chunkKey, defaultRegion != null ? defaultRegion.id() : null);
        return regionId != null ? ClientRegions.get(regionId) : null;
    }

    @Override
    public void alignWidgets() {
        backButton.setPosAndSize(5, 5, 16, 16);
        int y = HEADER_HEIGHT + 4;

        int labelButtonWidth = 50;
        labelBox.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2 - labelButtonWidth - 4, 18);
        labelButton.setPosAndSize(width - CONTENT_PAD - labelButtonWidth, y, labelButtonWidth, 18);
        y += LABEL_ROW_HEIGHT;

        for (PropertyRow row : rows) {
            row.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2, ROW_HEIGHT);
            // setPosAndSize doesn't cascade into a child Panel's own
            // alignWidgets() - without this, clickCatcher/accessListButton
            // stayed at their stale default positions (see the identical
            // bug/fix in RegionListScreen).
            row.alignWidgets();
            y += ROW_HEIGHT + ROW_GAP;
        }
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
        theme.drawString(graphics, Component.translatable("gui.lc_ftb_hook.marketplace.settings_title"),
                x + w / 2, y + 7, NordColors.SNOW_STORM_0, Theme.CENTERED);
    }

    private final class PropertyRow extends Panel {
        private final ProtectionProperty property;
        private dev.ftb.mods.ftblibrary.ui.Button clickCatcher;
        @Nullable
        private SimpleButton accessListButton;

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
                        cycle();
                    }
                }

                @Override
                public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                }

                @Override
                public void addMouseOverText(dev.ftb.mods.ftblibrary.util.TooltipList list) {
                    if (!canOverride()) {
                        list.add(Component.translatable("message.lc_ftb_hook.marketplace_override_locked").withStyle(ChatFormatting.RED));
                    }
                }
            };
            add(clickCatcher);

            if (property.isPrivacyMode()) {
                accessListButton = new SimpleButton(
                        this,
                        Component.empty(),
                        dev.ftb.mods.ftblibrary.icon.ItemIcon.getItemIcon(net.minecraft.world.item.Items.PLAYER_HEAD),
                        (button, mouseButton) -> new PlayerAccessListScreen(ChunkOverrideScreen.this, chunkKey, property).openGui()
                ) {
                    @Override
                    public void addMouseOverText(dev.ftb.mods.ftblibrary.util.TooltipList list) {
                        list.add(Component.translatable("gui.lc_ftb_hook.access_list.button"));
                        if (!canOverride()) {
                            // Still editable (see PlayerAccessListScreen/
                            // MarketplaceService#setAccessList - no longer
                            // gated on the region supporting protection) -
                            // this is informational, not a block: the list
                            // just sits dormant, unconsulted by
                            // ChunkTeamDataRegionPrivacyMixin, until the
                            // region's baseline is raised enough to make an
                            // override possible again.
                            list.add(Component.translatable("message.lc_ftb_hook.access_list.not_in_effect").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                        }
                    }
                };
                add(accessListButton);
            }
        }

        private boolean canOverride() {
            Region region = region();
            return region != null && !property.isAtMinimum(region.propertyValue(property));
        }

        @Override
        public void alignWidgets() {
            int rightWidth = accessListButton != null ? 20 : 0;
            clickCatcher.setPosAndSize(0, 0, Math.max(0, width - rightWidth), height);
            if (accessListButton != null) {
                accessListButton.setPosAndSize(width - 18, (height - 16) / 2, 16, 16);
            }
        }

        private void cycle() {
            Region region = region();
            if (region == null || !canOverride()) {
                // The region itself isn't protected here - any override
                // would be stricter than the region baseline, which isn't
                // allowed, so no override is possible at all (see the
                // clickCatcher's tooltip explaining this to the player).
                return;
            }
            ChunkOwnership ownership = ClientChunkOwnership.get(chunkKey);
            String current = ProtectionResolution.effectiveValue(region, ownership, property);
            String next = nextValue(current);
            PacketDistributor.sendToServer(new SetChunkOverridePayload(chunkKey, property.id(), next));
        }

        /**
         * Freely cycles every value (not just "loosen further") once the
         * region baseline allows an override at all - Allies/Team/Private
         * share the same strictness tier (see ProtectionResolution.strictness),
         * so switching between them is never a tightening, and Public is
         * always reachable as the loosest option. Matches
         * RegionSettingsScreen.PropertyRow's own cycling for consistency.
         */
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
            String regionValue = region != null ? region.propertyValue(property) : property.minimumSerialized();
            ChunkOwnership ownership = ClientChunkOwnership.get(chunkKey);
            String effective = region != null ? ProtectionResolution.effectiveValue(region, ownership, property) : regionValue;

            Color4I background = isMouseOver() ? NordColors.POLAR_NIGHT_1 : NordColors.POLAR_NIGHT_2;
            background.withAlpha(180).draw(graphics, x + 1, y, w - 2, h);

            Component label = Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + property.id());
            theme.drawString(graphics, label, x + 6, y + 3, NordColors.SNOW_STORM_0, 0);

            boolean overridden = !effective.equals(regionValue);
            Component valueText = Component.literal(effective).withStyle(overridden ? ChatFormatting.GREEN : ChatFormatting.GRAY);
            int textWidth = theme.getStringWidth(valueText);
            // Leave room for accessListButton (same 20px reserved by
            // alignWidgets' rightWidth) - the text used to be right-aligned
            // against the row's full width regardless of whether that button
            // was present, so it ran straight into/under the player-head icon.
            int rightInset = accessListButton != null ? 20 : 6;
            theme.drawString(graphics, valueText, x + w - rightInset - textWidth, y + 3, NordColors.SNOW_STORM_0, 0);
        }
    }
}
