package dev.malik.lcftbhook.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.NordButton;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.PlayerAccessList;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.network.SetChunkAccessListPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-property whitelist/blacklist editor for a privately-owned chunk (see
 * {@link PlayerAccessList}) - opened from {@link ChunkOverrideScreen}'s
 * per-row "..." button, only while that property's effective value isn't
 * Public. Every change (mode toggle, add, remove) sends the full updated
 * list immediately, same "act on every micro-action" pattern the rest of
 * this mod's small dialogs use (rename, delete, etc.) rather than a
 * separate save step.
 */
public class PlayerAccessListScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_GAP = 2;
    private static final int CONTENT_PAD = 8;
    private static final int CONTROLS_HEIGHT = 22;

    private final BaseScreen returnTo;
    private final String chunkKey;
    private final ProtectionProperty property;
    private final Set<UUID> players = new LinkedHashSet<>();
    private boolean whitelist;

    private SimpleButton backButton;
    private NordButton modeButton;
    private TextBox nameBox;
    private NordButton addButton;
    private PlayerRow[] rows;

    public PlayerAccessListScreen(BaseScreen returnTo, String chunkKey, ProtectionProperty property) {
        this.returnTo = returnTo;
        this.chunkKey = chunkKey;
        this.property = property;
        PlayerAccessList current = ClientChunkOwnership.get(chunkKey).accessLists().getOrDefault(property.id(), PlayerAccessList.EMPTY);
        this.whitelist = current.whitelist();
        this.players.addAll(current.players());
    }

    @Override
    public boolean onInit() {
        setWidth(Math.min(getScreen().getGuiScaledWidth() - 20, 200));
        setHeight(HEADER_HEIGHT + 4 + CONTROLS_HEIGHT * 2 + 4 + Math.max(1, players.size()) * (ROW_HEIGHT + ROW_GAP) + CONTENT_PAD);
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> back());
        add(backButton);

        modeButton = new NordButton(this, modeLabel(), Icons.SETTINGS) {
            @Override
            public void onClicked(MouseButton button) {
                whitelist = !whitelist;
                push();
                rebuild();
            }
        };
        add(modeButton);

        nameBox = new TextBox(this);
        nameBox.ghostText = Component.translatable("gui.lc_ftb_hook.access_list.name_ghost").getString();
        nameBox.charLimit = 32;
        add(nameBox);

        addButton = new NordButton(this, Component.translatable("gui.lc_ftb_hook.access_list.add"), Icons.ADD) {
            @Override
            public void onClicked(MouseButton button) {
                addTypedPlayer();
            }
        };
        add(addButton);

        rows = new PlayerRow[players.size()];
        int i = 0;
        for (UUID playerId : players) {
            rows[i] = new PlayerRow(this, playerId);
            add(rows[i]);
            i++;
        }
    }

    @Override
    public void alignWidgets() {
        backButton.setPosAndSize(5, 5, 16, 16);
        int y = HEADER_HEIGHT + 4;
        modeButton.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2, CONTROLS_HEIGHT - 2);
        y += CONTROLS_HEIGHT;

        int addWidth = 50;
        nameBox.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2 - addWidth - 4, CONTROLS_HEIGHT - 4);
        addButton.setPosAndSize(width - CONTENT_PAD - addWidth, y, addWidth, CONTROLS_HEIGHT - 4);
        y += CONTROLS_HEIGHT + 4;

        for (PlayerRow row : rows) {
            row.setPosAndSize(CONTENT_PAD, y, width - CONTENT_PAD * 2, ROW_HEIGHT);
            // setPosAndSize doesn't cascade into a child Panel's own
            // alignWidgets() - without this, removeButton stayed at its
            // stale default position (see the identical bug/fix in
            // RegionListScreen/ChunkOverrideScreen/RegionSettingsScreen).
            row.alignWidgets();
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    @Override
    public void drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(graphics, theme, x, y, w, h);
        theme.drawString(
                graphics,
                Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + property.id()),
                x + w / 2,
                y + 7,
                NordColors.SNOW_STORM_0,
                Theme.CENTERED
        );
        if (rows.length == 0) {
            theme.drawString(
                    graphics,
                    Component.translatable("gui.lc_ftb_hook.access_list.empty").withStyle(ChatFormatting.GRAY),
                    x + w / 2,
                    y + HEADER_HEIGHT + 4 + CONTROLS_HEIGHT * 2 + 8,
                    NordColors.POLAR_NIGHT_4,
                    Theme.CENTERED
            );
        }
    }

    private Component modeLabel() {
        return Component.translatable(whitelist ? "gui.lc_ftb_hook.access_list.mode_whitelist" : "gui.lc_ftb_hook.access_list.mode_blacklist");
    }

    private void addTypedPlayer() {
        String name = nameBox.getText().trim();
        if (name.isEmpty()) {
            return;
        }
        var connection = Minecraft.getInstance().getConnection();
        var info = connection != null ? connection.getPlayerInfo(name) : null;
        if (info == null) {
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("message.lc_ftb_hook.access_list.player_not_found", name));
            return;
        }
        if (players.add(info.getProfile().getId())) {
            push();
        }
        nameBox.setText("");
        rebuild();
    }

    private void removePlayer(UUID playerId) {
        if (players.remove(playerId)) {
            push();
            rebuild();
        }
    }

    private void push() {
        PacketDistributor.sendToServer(new SetChunkAccessListPayload(chunkKey, property.id(), whitelist, Set.copyOf(players)));
    }

    private void rebuild() {
        setHeight(HEADER_HEIGHT + 4 + CONTROLS_HEIGHT * 2 + 4 + Math.max(1, players.size()) * (ROW_HEIGHT + ROW_GAP) + CONTENT_PAD);
        refreshWidgets();
    }

    private void back() {
        if (returnTo != null) {
            returnTo.openGui();
        } else {
            closeGui(true);
        }
    }

    private final class PlayerRow extends Panel {
        private final UUID playerId;
        private SimpleButton removeButton;

        PlayerRow(Panel panel, UUID playerId) {
            super(panel);
            this.playerId = playerId;
        }

        @Override
        public void addWidgets() {
            removeButton = new SimpleButton(this, Component.empty(), Icons.CANCEL, (button, mouseButton) -> removePlayer(playerId));
            add(removeButton);
        }

        @Override
        public void alignWidgets() {
            removeButton.setPosAndSize(width - 16, (height - 14) / 2, 14, 14);
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_2.withAlpha(180).draw(graphics, x + 1, y, w - 2, h);
            theme.drawString(graphics, Component.literal(resolveName(playerId)), x + 4, y + 5, NordColors.SNOW_STORM_0, 0);
        }
    }

    private static String resolveName(UUID playerId) {
        var connection = Minecraft.getInstance().getConnection();
        var info = connection != null ? connection.getPlayerInfo(playerId) : null;
        return info != null ? info.getProfile().getName() : playerId.toString();
    }
}
