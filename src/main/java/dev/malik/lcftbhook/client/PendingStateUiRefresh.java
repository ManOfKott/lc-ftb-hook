package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;

public final class PendingStateUiRefresh {
    private PendingStateUiRefresh() {
    }

    /**
     * Refreshes an open FTB config screen's widgets from its current backing
     * values (e.g. claim_visibility). Protection settings no longer live in
     * this screen - they're edited entirely through the Region screens, which
     * refresh themselves from {@link ClientPendingState}/region sync packets
     * directly.
     */
    public static void refreshOpenScreens() {
        EditConfigScreen configScreen = ClientUtils.getCurrentGuiAs(EditConfigScreen.class);
        if (configScreen != null) {
            configScreen.refreshWidgets();
        }
    }
}
