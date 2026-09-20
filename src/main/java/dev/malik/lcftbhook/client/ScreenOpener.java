package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Opens screens via reflection instead of a direct constructor reference.
 * Needed for any screen reached from a right-click-menu action stored on a
 * long-lived object (an {@code xaero.map.gui.dropdown.rightclick.RightClickOption}
 * or an FTB Library {@code ContextMenuItem}) - the action lambda is invoked
 * much later, when the player actually clicks the menu entry, which is
 * exactly the kind of "first reference to this class, at an unpredictable
 * later moment" pattern that intermittently throws {@code ClassNotFoundException}
 * (same root cause documented on {@code PartyTeamRankSyncMixin} - confirmed
 * here that the race isn't limited to Mixin-merged code, since none of the
 * callers of these methods are mixins; {@code RegionPickerScreen} crashed
 * this way from a plain lambda stored in an {@code LcRightClickOption}).
 * <p>
 * The single-arg {@code Class.forName(name)} used here originally still
 * threw {@code ClassNotFoundException} 100% of the time from Xaero's
 * right-click callback, even in a freshly-launched client - ruling out the
 * "first reference at an unpredictable moment" race as the whole story,
 * since that predicts intermittent failures, not a consistent one.
 * {@code Class.forName(name)} resolves against "the defining class loader
 * of the CURRENT class" via caller-sensitive machinery that can get
 * confused when the call happens through a lambda/synthetic frame (exactly
 * how every caller here reaches this class). {@link #loadClass} pins the
 * loader explicitly to {@code ScreenOpener}'s own (known-good, since this
 * class and the screens it opens are compiled into the same jar) with a
 * thread-context-classloader fallback for extra safety.
 */
public final class ScreenOpener {
    private ScreenOpener() {
    }

    public static void openRegionPicker(@Nullable BaseScreen returnTo, Consumer<UUID> onPicked) {
        try {
            Class<?> cls = loadClass("dev.malik.lcftbhook.client.gui.RegionPickerScreen");
            Constructor<?> ctor = cls.getConstructor(BaseScreen.class, Consumer.class);
            Object screen = ctor.newInstance(returnTo, onPicked);
            cls.getMethod("openGui").invoke(screen);
        } catch (ReflectiveOperationException e) {
            log("RegionPickerScreen", e);
        }
    }

    public static void openSalePrice(@Nullable Runnable onBack, List<String> chunkKeys, boolean asCountry) {
        try {
            Class<?> cls = loadClass("dev.malik.lcftbhook.client.gui.ChunkSalePriceScreen");
            Constructor<?> ctor = cls.getConstructor(Runnable.class, List.class, boolean.class);
            Object screen = ctor.newInstance(onBack, chunkKeys, asCountry);
            Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen) screen);
        } catch (ReflectiveOperationException e) {
            log("ChunkSalePriceScreen", e);
        }
    }

    public static void openOverride(@Nullable BaseScreen returnTo, String chunkKey) {
        try {
            Class<?> cls = loadClass("dev.malik.lcftbhook.client.gui.ChunkOverrideScreen");
            Constructor<?> ctor = cls.getConstructor(BaseScreen.class, String.class);
            Object screen = ctor.newInstance(returnTo, chunkKey);
            cls.getMethod("openGui").invoke(screen);
        } catch (ReflectiveOperationException e) {
            log("ChunkOverrideScreen", e);
        }
    }

    public static void openBuyConfirm(@Nullable BaseScreen returnTo, List<String> chunkKeys, boolean asCountry) {
        try {
            Class<?> cls = loadClass("dev.malik.lcftbhook.client.gui.ChunkBuyConfirmScreen");
            Constructor<?> ctor = cls.getConstructor(BaseScreen.class, List.class, boolean.class);
            Object screen = ctor.newInstance(returnTo, chunkKeys, asCountry);
            cls.getMethod("openGui").invoke(screen);
        } catch (ReflectiveOperationException e) {
            log("ChunkBuyConfirmScreen", e);
        }
    }

    public static void openExpropriatePrice(@Nullable Runnable onBack, List<String> chunkKeys) {
        try {
            Class<?> cls = loadClass("dev.malik.lcftbhook.client.gui.ExpropriatePriceScreen");
            Constructor<?> ctor = cls.getConstructor(Runnable.class, List.class);
            Object screen = ctor.newInstance(onBack, chunkKeys);
            Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen) screen);
        } catch (ReflectiveOperationException e) {
            log("ExpropriatePriceScreen", e);
        }
    }

    public static void openRegionSettings(@Nullable BaseScreen returnTo, UUID regionId) {
        try {
            Class<?> cls = loadClass("dev.malik.lcftbhook.client.gui.RegionSettingsScreen");
            Constructor<?> ctor = cls.getConstructor(BaseScreen.class, UUID.class);
            Object screen = ctor.newInstance(returnTo, regionId);
            cls.getMethod("openGui").invoke(screen);
        } catch (ReflectiveOperationException e) {
            log("RegionSettingsScreen", e);
        }
    }

    public static void openTextPrompt(@Nullable BaseScreen returnTo, Component title, String ghostText, Consumer<String> onConfirm) {
        try {
            Class<?> cls = loadClass("dev.malik.lcftbhook.client.gui.TextPromptScreen");
            Constructor<?> ctor = cls.getConstructor(BaseScreen.class, Component.class, String.class, Consumer.class);
            Object screen = ctor.newInstance(returnTo, title, ghostText, onConfirm);
            cls.getMethod("openGui").invoke(screen);
        } catch (ReflectiveOperationException e) {
            log("TextPromptScreen", e);
        }
    }

    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, true, ScreenOpener.class.getClassLoader());
        } catch (ClassNotFoundException ownLoaderFailure) {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader == null) {
                throw ownLoaderFailure;
            }
            try {
                Class<?> found = Class.forName(name, true, contextLoader);
                org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").warn(
                        "{} not found via ScreenOpener's own classloader but found via the thread context classloader instead", name
                );
                return found;
            } catch (ClassNotFoundException contextLoaderFailure) {
                ownLoaderFailure.addSuppressed(contextLoaderFailure);
                throw ownLoaderFailure;
            }
        }
    }

    private static void log(String screenName, ReflectiveOperationException e) {
        org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").error("Failed to open {}", screenName, e);
    }
}
