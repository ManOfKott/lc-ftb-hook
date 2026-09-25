package dev.malik.lcftbhook.client.gui;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.NordButton;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.client.ClientPublicRegions;
import dev.malik.lcftbhook.client.ClientRegionMembership;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.SetChunkLabelPayload;
import dev.malik.lcftbhook.network.SetChunkOverridePayload;
import dev.malik.lcftbhook.service.ProtectionResolution;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

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
    private ComingSoonRow comingSoonRow;

    public ChunkOverrideScreen(BaseScreen returnTo, String chunkKey) {
        this.returnTo = returnTo;
        this.chunkKey = chunkKey;
    }

    @Override
    public boolean onInit() {
        // Wider than before (was 220) - a row showing both the stored
        // (crossed-out) preference and the actually-effective value side by
        // side (see PropertyRow#drawBackground) needs more room than a
        // single value ever did, or the two collide with the label on a row
        // where they diverge (e.g. "Mob griefing protection").
        setWidth(Math.min(getScreen().getGuiScaledWidth() - 20, 280));
        setHeight(30 + LABEL_ROW_HEIGHT + (ProtectionProperty.DEFAULT_ORDER.size() + 1) * (ROW_HEIGHT + ROW_GAP));
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

        comingSoonRow = new ComingSoonRow(this);
        add(comingSoonRow);
    }

    /**
     * A privately-owned chunk can sit in ANOTHER team's territory entirely
     * (you bought it off their marketplace) - {@code ClientRegions} only
     * ever holds the viewer's own team's regions (team-private, since it
     * also carries chunk counts/prices), so it silently returned null for
     * every such chunk and made canOverride() permanently false, blocking
     * every property on a cross-team-owned chunk from ever being editable.
     * {@link ClientPublicRegions} (name + protection values, no
     * counts/prices, broadcast for every team) resolves correctly regardless
     * of which team owns the land - same pattern as
     * {@code ProtectionInfoLines}/{@code BuyableChunkChecker}.
     */
    @Nullable
    private Region region() {
        UUID regionId = ClientRegionMembership.rawRegionOf(chunkKey);
        if (regionId != null) {
            Region region = ClientPublicRegions.get(regionId);
            if (region != null) {
                return region;
            }
        }
        UUID ownerTeamId = resolveOwningTeamId();
        return ownerTeamId != null ? ClientPublicRegions.getDefaultFor(ownerTeamId) : null;
    }

    @Nullable
    private UUID resolveOwningTeamId() {
        Optional<MapDimension> dimOpt = MapDimension.getCurrent();
        if (dimOpt.isEmpty()) {
            return null;
        }
        MapDimension dim = dimOpt.get();
        ResourceLocation chunkDimension = ChunkPosKey.dimension(chunkKey);
        if (!dim.dimension.location().equals(chunkDimension)) {
            return null;
        }
        int x = ChunkPosKey.x(chunkKey);
        int z = ChunkPosKey.z(chunkKey);
        MapRegion mapRegion = dim.getRegion(XZ.regionFromChunk(x, z));
        MapChunk chunk = mapRegion != null ? mapRegion.getChunkForAbsoluteChunkPos(XZ.of(x, z)) : null;
        Team team = chunk != null ? chunk.getTeam().orElse(null) : null;
        return team != null ? team.getTeamId() : null;
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

        comingSoonRow.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2, ROW_HEIGHT);
        y += ROW_HEIGHT + ROW_GAP;
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
                    dev.malik.lcftbhook.client.ProtectionDescriptionTooltip.append(list, property, false);
                    String stored = storedValue();
                    String effective = effectiveValue();
                    if (stored != null && !stored.equals(effective)) {
                        // Never blocked - just informational: your stored
                        // preference is still saved, it just isn't what's
                        // actually enforced right now because the region
                        // itself can't currently deliver it (e.g. dismantled
                        // for non-payment). It resumes automatically the
                        // moment the region's protection is restored -
                        // clicking still cycles your STORED preference, not
                        // the effective one.
                        list.add(Component.translatable(
                                "gui.lc_ftb_hook.marketplace_override_not_in_effect",
                                formatValue(effective, property)
                        ).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
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
                        if (property.isAtMinimum(effectiveValue())) {
                            // Still editable (see PlayerAccessListScreen/
                            // MarketplaceService#setAccessList) - this is
                            // informational, not a block: the list just sits
                            // dormant, unconsulted by
                            // ChunkTeamDataRegionPrivacyMixin, while nothing
                            // above PUBLIC is actually in effect for this
                            // chunk right now.
                            list.add(Component.translatable("message.lc_ftb_hook.access_list.not_in_effect").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                        }
                    }
                };
                add(accessListButton);
            }
        }

        /** The private owner's stored preference, or {@code null} if none is set - regardless of whether it's currently achievable. */
        @Nullable
        private String storedValue() {
            return ProtectionResolution.storedOverrideValue(ClientChunkOwnership.get(chunkKey), property);
        }

        /** What's actually enforced right now (see {@link ProtectionResolution#effectiveValue}). */
        private String effectiveValue() {
            Region region = region();
            ChunkOwnership ownership = ClientChunkOwnership.get(chunkKey);
            return region != null
                    ? ProtectionResolution.effectiveValue(region, ownership, property)
                    : property.minimumSerialized();
        }

        @Override
        public void alignWidgets() {
            int rightWidth = accessListButton != null ? 20 : 0;
            clickCatcher.setPosAndSize(0, 0, Math.max(0, width - rightWidth), height);
            if (accessListButton != null) {
                accessListButton.setPosAndSize(width - 18, (height - 16) / 2, 16, 16);
            }
        }

        /**
         * Never blocked - a private owner can always change their stored
         * preference, whether or not it's currently achievable against the
         * region's own value (see {@link ProtectionResolution#effectiveValue}).
         * Cycles from the STORED preference (falling back to the region's
         * own value if none is stored yet), not from the effective one - so
         * repeatedly clicking while dormant still steps through your own
         * intended values instead of getting stuck cycling from whatever the
         * region is currently forcing.
         */
        private void cycle() {
            Region region = region();
            if (region == null) {
                return;
            }
            String stored = storedValue();
            String current = stored != null ? stored : region.propertyValue(property);
            String next = nextValue(current);
            PacketDistributor.sendToServer(new SetChunkOverridePayload(chunkKey, property.id(), next));
        }

        /**
         * Freely cycles every value (not just "loosen further") - Allies/Team/Private
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
            String stored = storedValue();
            String effective = effectiveValue();

            Color4I background = isMouseOver() ? NordColors.POLAR_NIGHT_1 : NordColors.POLAR_NIGHT_2;
            background.withAlpha(180).draw(graphics, x + 1, y, w - 2, h);

            Component label = Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + property.id());
            theme.drawString(graphics, label, x + 6, y + 3, NordColors.SNOW_STORM_0, 0);

            // Match RegionSettingsScreen's PropertyRow: green = protected
            // (non-minimum), red = unprotected (minimum) - never gray, that
            // was signaling "matches the region baseline" instead of the
            // property's actual protected/unprotected state, inconsistent
            // with every other protection screen in the mod. Also use
            // formatValue's translated "Protected"/"Unprotected" labels
            // instead of the raw "true"/"false" string - a bare boolean read
            // backwards against a label like "Mob griefing protection", since
            // the underlying value is really "is mob griefing ALLOWED", not
            // "is protection active".
            boolean protectedValue = !property.isAtMinimum(effective);
            Component effectiveText = formatValue(effective, property).copy().withStyle(protectedValue ? ChatFormatting.GREEN : ChatFormatting.RED);

            // The stored preference always stays visible somewhere even
            // while dormant (never silently hidden or lost) - shown gray and
            // struck-through next to the value that's actually enforced,
            // rather than replacing it, so it's always obvious which one is
            // real right now (per the region possibly having dropped below
            // it, e.g. dismantled for non-payment - see ProtectionResolution).
            Component valueText;
            if (stored != null && !stored.equals(effective)) {
                Component storedText = formatValue(stored, property).copy().withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
                valueText = Component.empty().append(storedText).append(Component.literal(" ")).append(effectiveText);
            } else {
                valueText = effectiveText;
            }
            int textWidth = theme.getStringWidth(valueText);
            // Leave room for accessListButton (same 20px reserved by
            // alignWidgets' rightWidth) - the text used to be right-aligned
            // against the row's full width regardless of whether that button
            // was present, so it ran straight into/under the player-head icon.
            int rightInset = accessListButton != null ? 20 : 6;
            theme.drawString(graphics, valueText, x + w - rightInset - textWidth, y + 3, NordColors.SNOW_STORM_0, 0);
        }
    }

    private static Component formatValue(String serialized, ProtectionProperty property) {
        return dev.malik.lcftbhook.client.ProtectionValueFormat.formatValue(serialized, property);
    }

    /** Same spoiler placeholder as {@code RegionSettingsScreen.ComingSoonRow} - see its javadoc. */
    private final class ComingSoonRow extends Panel {
        ComingSoonRow(Panel panel) {
            super(panel);
        }

        @Override
        public void addWidgets() {
        }

        @Override
        public void alignWidgets() {
        }

        @Override
        public void addMouseOverText(dev.ftb.mods.ftblibrary.util.TooltipList list) {
            list.add(Component.translatable("gui.lc_ftb_hook.protection_coming_soon_tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_2.withAlpha(180).draw(graphics, x + 1, y, w - 2, h);

            Component label = Component.translatable("gui.lc_ftb_hook.regions.sable_sublevels").withStyle(ChatFormatting.GRAY);
            theme.drawString(graphics, label, x + 6, y + 3, NordColors.POLAR_NIGHT_4, 0);

            Component value = Component.translatable("gui.lc_ftb_hook.protection_coming_soon").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            int textWidth = theme.getStringWidth(value);
            theme.drawString(graphics, value, x + w - 6 - textWidth, y + 3, NordColors.POLAR_NIGHT_4, 0);
        }
    }
}
