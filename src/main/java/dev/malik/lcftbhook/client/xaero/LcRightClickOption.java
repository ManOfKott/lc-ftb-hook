package dev.malik.lcftbhook.client.xaero;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.function.Consumer;

/**
 * A plain, standalone (non-anonymous) {@link RightClickOption} implementation.
 * This must NOT be an anonymous class declared inside {@code GuiMapMarketplaceMixin}
 * itself - Sponge Mixin has to physically relocate an anonymous class into the
 * mixin's target class ({@code GuiMap}) under a synthetic {@code $Anonymous$<hash>}
 * name, and that relocation intermittently produces an unresolvable class
 * ({@code NoClassDefFoundError}) later when {@code GuiMap} is used from an
 * unrelated code path (observed via {@code ControlsHandler.keyDown}). Being a
 * normal top-level class here means Mixin never needs to relocate it at all -
 * see {@code GuiMapMarketplaceMixin} for why it's still instantiated via
 * reflection from the mixin side.
 */
public final class LcRightClickOption extends RightClickOption {
    private final Component title;
    private final Consumer<Screen> action;

    public LcRightClickOption(String name, int index, IRightClickableElement target, Component title, Consumer<Screen> action) {
        super(name, index, target);
        this.title = title;
        this.action = action;
    }

    @Override
    public Component getDisplayName() {
        return title;
    }

    @Override
    public void onAction(Screen screen) {
        action.accept(screen);
    }
}
