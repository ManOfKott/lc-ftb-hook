package dev.malik.lcftbhook.client.gui;

import io.github.lightman314.lightmanscurrency.api.misc.client.rendering.EasyGuiGraphics;
import io.github.lightman314.lightmanscurrency.api.money.input.MoneyValueWidget;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.client.gui.easy.EasyScreen;
import io.github.lightman314.lightmanscurrency.client.util.ScreenArea;
import dev.malik.lcftbhook.network.ExpropriateChunksPayload;
import dev.malik.lcftbhook.util.MoneyUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Standalone compensation-entry screen for "Expropriate...", mirroring
 * {@link ChunkSalePriceScreen}'s Lightman's Currency coin-input widget setup
 * (same redundant-by-design copy - see that class's javadoc for why this
 * can't just be a shared BaseScreen). Unlike a sale price, a compensation of
 * 0 is a valid (if unfriendly) choice, so no minimum is enforced here.
 */
public class ExpropriatePriceScreen extends EasyScreen {
    @Nullable
    private final Runnable onBack;
    private final List<String> chunkKeys;

    private MoneyValueWidget priceWidget;
    private ScreenArea screenArea;

    public ExpropriatePriceScreen(@Nullable Runnable onBack, List<String> chunkKeys) {
        super(Component.translatable("gui.lc_ftb_hook.marketplace.expropriate"));
        this.onBack = onBack;
        this.chunkKeys = chunkKeys;
    }

    @Override
    protected void preInit() {
        int titleWidth = this.getFont().width(title());
        this.resize(Math.max(200, titleWidth + 24), 116);
    }

    private Component title() {
        return Component.translatable("gui.lc_ftb_hook.marketplace.expropriate_title", chunkKeys.size());
    }

    @Override
    protected void initialize(ScreenArea screenArea) {
        this.screenArea = screenArea;
        this.priceWidget = this.addChild(
                MoneyValueWidget.builder()
                        .position(screenArea.pos.offset(12, 24))
                        .oldIfNotFirst(this.priceWidget == null, this.priceWidget)
                        .blockFreeInputs()
                        .build()
        );

        this.addChild(Button.builder(Component.translatable("gui.back"), button -> this.onClose())
                .bounds(screenArea.x + 12, screenArea.y + screenArea.height - 24, 80, 16)
                .build());

        this.addChild(Button.builder(Component.translatable("gui.lc_ftb_hook.marketplace.expropriate_confirm"), button -> this.confirm())
                .bounds(screenArea.x + screenArea.width - 92, screenArea.y + screenArea.height - 24, 80, 16)
                .build());
    }

    private void confirm() {
        MoneyValue value = this.priceWidget.getCurrentValue();
        // See ChunkSalePriceScreen.confirm() for why this is checked here
        // rather than restricted on the widget itself.
        if (MoneyUtil.usesEmeraldCoinDenomination(value)) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_no_emerald_coins"), false);
            }
            return;
        }
        long compensation = value.getCoreValue();
        PacketDistributor.sendToServer(new ExpropriateChunksPayload(chunkKeys, compensation));
        this.onClose();
    }

    @Override
    public void onClose() {
        if (onBack != null) {
            onBack.run();
        } else {
            super.onClose();
        }
    }

    private static final int HEADER_HEIGHT = 22;

    @Override
    protected void renderBG(EasyGuiGraphics gui) {
        // See ChunkSalePriceScreen.renderBG for why this is a fully manual
        // bordered box rather than Theme.DEFAULT.drawGui (same bug, same fix).
        gui.fill(0, 0, screenArea.width, screenArea.height, 0xFF4C566A);
        gui.fill(1, 1, screenArea.width - 2, screenArea.height - 2, 0xF02E3440);
        gui.fill(1, HEADER_HEIGHT + 1, screenArea.width - 2, 1, 0xFF4C566A);

        Component title = title();
        int titleWidth = this.getFont().width(title);
        gui.drawString(title, (screenArea.width - titleWidth) / 2, 7, 0xFFFFFF);
    }
}
