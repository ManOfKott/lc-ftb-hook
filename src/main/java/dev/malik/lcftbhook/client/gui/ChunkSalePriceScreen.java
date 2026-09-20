package dev.malik.lcftbhook.client.gui;

import io.github.lightman314.lightmanscurrency.api.misc.client.rendering.EasyGuiGraphics;
import io.github.lightman314.lightmanscurrency.api.money.input.MoneyValueWidget;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.client.gui.easy.EasyScreen;
import io.github.lightman314.lightmanscurrency.client.util.ScreenArea;
import dev.malik.lcftbhook.network.ListForSalePayload;
import dev.malik.lcftbhook.util.MoneyUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Standalone price-entry screen for listing one or more chunks for sale
 * ("Sell" for a private resale, "Sell as Country" for state-owned chunks).
 * Hosts Lightman's Currency's own {@link MoneyValueWidget} - the same
 * ATM-style coin-input widget the ATM's Transfer tab uses (see
 * {@code io.github.lightman314.lightmanscurrency.client.gui.screen.inventory.atm.TransferTab}
 * in the LC jar, which this screen's construction/wiring deliberately
 * mirrors) - instead of a plain text field. FTB Library's Panel system can't
 * host it (it's built on LC's own "Easy Widget" framework, which needs its
 * children paired to a hosting {@code EasyScreen}), so this extends LC's own
 * public {@code EasyScreen} base (a plain vanilla {@code Screen}, not tied to
 * any container menu) instead of {@code BaseScreen}.
 */
public class ChunkSalePriceScreen extends EasyScreen {
    @Nullable
    private final Runnable onBack;
    private final List<String> chunkKeys;
    private final boolean asCountry;

    private MoneyValueWidget priceWidget;
    private ScreenArea screenArea;

    public ChunkSalePriceScreen(@Nullable Runnable onBack, List<String> chunkKeys, boolean asCountry) {
        super(Component.translatable(asCountry ? "gui.lc_ftb_hook.marketplace.sell_as_country" : "gui.lc_ftb_hook.marketplace.sell"));
        this.onBack = onBack;
        this.chunkKeys = chunkKeys;
        this.asCountry = asCountry;
    }

    @Override
    protected void preInit() {
        // The title includes the chunk count, so its width isn't fixed - a
        // flat 200px was too narrow for longer titles/selections and the
        // text spilled past the box. Size the box to fit whatever the title
        // actually measures out to, same "don't guess a fixed width" lesson
        // as TextPromptScreen's Confirm button.
        int titleWidth = this.getFont().width(title());
        this.resize(Math.max(200, titleWidth + 24), 116);
    }

    private Component title() {
        return Component.translatable("gui.lc_ftb_hook.marketplace.price_title", chunkKeys.size());
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

        this.addChild(Button.builder(Component.translatable("gui.lc_ftb_hook.marketplace.sell_confirm"), button -> this.confirm())
                .bounds(screenArea.x + screenArea.width - 92, screenArea.y + screenArea.height - 24, 80, 16)
                .build());
    }

    private void confirm() {
        MoneyValue value = this.priceWidget.getCurrentValue();
        long price = value.getCoreValue();
        if (price <= 0) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_invalid_price"), false);
            }
            return;
        }
        // Emerald Coin is Lightman's Currency's own custom coin item, not the
        // vanilla Emerald - not something we want players pricing chunks
        // with. The chain's denominations are global config, not something a
        // single widget can restrict, so this is checked here instead.
        if (MoneyUtil.usesEmeraldCoinDenomination(value)) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_no_emerald_coins"), false);
            }
            return;
        }
        PacketDistributor.sendToServer(new ListForSalePayload(chunkKeys, price, asCountry));
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
        // EasyScreen draws no dialog container of its own (renderBG is
        // abstract, entirely up to the subclass) - without this, the widgets
        // sat directly on the blurred world background, clashing visually
        // with every other FTB-styled dialog in this mod.
        //
        // Tried reusing Theme.DEFAULT.drawGui(...) (the same themed frame
        // BaseScreen's own drawBackground() draws) here, but its texture
        // rendered detached from this screen's own coordinate space (the
        // title/content ended up floating above an unrelated box) - unlike
        // gui.fill()/gui.drawString(), which are confirmed correctly
        // 0,0-relative to this screen's own corner (EasyScreen.render()
        // pushOffset()'s the EasyGuiGraphics to it), Icon.draw() apparently
        // doesn't respect that same offset when called through the raw
        // GuiGraphics from gui.getGui(). A fully manual bordered box - one
        // outer fill in the border color, one 1px-inset fill in the content
        // color - sidesteps that entirely and stays reliable.
        gui.fill(0, 0, screenArea.width, screenArea.height, 0xFF4C566A);
        gui.fill(1, 1, screenArea.width - 2, screenArea.height - 2, 0xF02E3440);
        gui.fill(1, HEADER_HEIGHT + 1, screenArea.width - 2, 1, 0xFF4C566A);

        Component title = title();
        int titleWidth = this.getFont().width(title);
        gui.drawString(title, (screenArea.width - titleWidth) / 2, 7, 0xFFFFFF);
    }
}
