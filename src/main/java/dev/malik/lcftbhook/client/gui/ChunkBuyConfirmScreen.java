package dev.malik.lcftbhook.client.gui;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.NordButton;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.network.BuyChunksPayload;
import dev.malik.lcftbhook.util.MoneyUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Price/cancel/confirm dialog for buying one or more listed chunks ("Buy" or "Buy as Country"). */
public class ChunkBuyConfirmScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = 22;

    private final BaseScreen returnTo;
    private final List<String> chunkKeys;
    private final boolean asCountry;

    private SimpleButton backButton;
    private NordButton confirmButton;

    public ChunkBuyConfirmScreen(BaseScreen returnTo, List<String> chunkKeys, boolean asCountry) {
        this.returnTo = returnTo;
        this.chunkKeys = chunkKeys;
        this.asCountry = asCountry;
    }

    @Override
    public boolean onInit() {
        setWidth(Math.min(getScreen().getGuiScaledWidth() - 20, 220));
        setHeight(80);
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> back());
        add(backButton);

        confirmButton = new NordButton(this, Component.translatable("gui.lc_ftb_hook.marketplace.buy"), Icons.ACCEPT) {
            @Override
            public void onClicked(MouseButton button) {
                PacketDistributor.sendToServer(new BuyChunksPayload(chunkKeys, asCountry));
                back();
            }
        };
        add(confirmButton);
    }

    private long totalCopper() {
        long total = 0L;
        for (String key : chunkKeys) {
            Long price = ClientChunkOwnership.get(key).listingPricePerChunk();
            if (price != null) {
                total += price;
            }
        }
        return total;
    }

    private void back() {
        if (returnTo != null) {
            returnTo.openGui();
        } else {
            closeGui(true);
        }
    }

    @Override
    public void alignWidgets() {
        backButton.setPosAndSize(5, 5, 16, 16);
        confirmButton.setPosAndSize(width - 100, height - 26, 92, 18);
    }

    @Override
    public void drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(graphics, theme, x, y, w, h);
        theme.drawString(
                graphics,
                Component.translatable("gui.lc_ftb_hook.marketplace.buy_title", chunkKeys.size()),
                x + w / 2, y + 7, NordColors.SNOW_STORM_0, Theme.CENTERED
        );
        Component total = Component.translatable(
                "gui.lc_ftb_hook.marketplace.buy_total",
                MoneyUtil.fromCopper(totalCopper()).getText()
        );
        theme.drawString(graphics, total, x + 8, y + HEADER_HEIGHT + 10, NordColors.SNOW_STORM_1, 0);
        if (asCountry) {
            theme.drawString(
                    graphics,
                    Component.translatable("gui.lc_ftb_hook.marketplace.buying_as_country"),
                    x + 8, y + HEADER_HEIGHT + 22, NordColors.YELLOW, 0
            );
        }
    }
}
