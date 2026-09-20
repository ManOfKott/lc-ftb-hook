package dev.malik.lcftbhook.client.gui;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.NordButton;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Small reusable single-line text prompt (e.g. naming a new region). */
public class TextPromptScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = 22;

    private final BaseScreen returnTo;
    private final Component title;
    private final String ghostText;
    private final Consumer<String> onConfirm;

    private SimpleButton backButton;
    private TextBox textBox;
    private NordButton confirmButton;

    public TextPromptScreen(BaseScreen returnTo, Component title, String ghostText, Consumer<String> onConfirm) {
        this.returnTo = returnTo;
        this.title = title;
        this.ghostText = ghostText;
        this.onConfirm = onConfirm;
    }

    @Override
    public boolean onInit() {
        setWidth(Math.min(getScreen().getGuiScaledWidth() - 20, 220));
        setHeight(70);
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> back());
        add(backButton);

        textBox = new TextBox(this);
        textBox.ghostText = ghostText;
        textBox.charLimit = 32;
        add(textBox);

        confirmButton = new NordButton(this, Component.translatable("gui.lc_ftb_hook.confirm"), Icons.ACCEPT) {
            @Override
            public void onClicked(MouseButton button) {
                String value = textBox.getText().trim();
                if (!value.isEmpty()) {
                    onConfirm.accept(value);
                    back();
                }
            }
        };
        add(confirmButton);
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
        textBox.setPosAndSize(8, HEADER_HEIGHT + 8, width - 16, 18);
        // Give the button its own full-width row instead of a narrow fixed
        // width sharing space with something else - a fixed width (tried
        // 70px, then 90px) kept clipping/overflowing "Confirm" at some GUI
        // scales no matter how generous, since NordButton doesn't reliably
        // constrain its own label to a narrow box. Full width removes the
        // guesswork entirely.
        confirmButton.setPosAndSize(8, HEADER_HEIGHT + 32, width - 16, 18);
    }

    @Override
    public void drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(graphics, theme, x, y, w, h);
        theme.drawString(graphics, title, x + w / 2, y + 7, NordColors.SNOW_STORM_0, Theme.CENTERED);
    }
}
