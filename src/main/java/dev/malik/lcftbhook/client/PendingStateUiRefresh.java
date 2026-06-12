package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.mixin.client.EditConfigScreenAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PendingStateUiRefresh {
    private PendingStateUiRefresh() {
    }

    public static void refreshOpenScreens() {
        EditConfigScreen configScreen = ClientUtils.getCurrentGuiAs(EditConfigScreen.class);
        if (configScreen != null) {
            configScreen.refreshWidgets();
        }
    }

    /**
     * Like {@link #syncOpenScreenValues(Team)}, but resolves the player's own
     * team first. Used when only the pending state changed (our own packet),
     * which carries no team reference.
     */
    public static void syncSelfTeamOpenScreen() {
        if (!FTBTeamsAPI.api().isClientManagerLoaded()) {
            return;
        }
        Team selfTeam = FTBTeamsAPI.api().getClientManager().selfTeam();
        if (selfTeam != null) {
            syncOpenScreenValues(selfTeam);
        }
    }

    /**
     * Pushes the team's current (server-synced) property values into an open
     * properties screen. Without this, the screen keeps showing the value
     * snapshots taken when it was opened, so server-side changes (pending
     * changes applied at upkeep, reverts, resets) only become visible after
     * closing and reopening the menu.
     *
     * <p>Properties with a queued (pending) change are pre-filled with the
     * queued value instead. This keeps the player's queued choice visible and
     * editable, and it lets the server distinguish "untouched" (submits the
     * queued value) from "switched back to the current value" (cancels the
     * queued change) on the next accept.
     */
    public static void syncOpenScreenValues(Team team) {
        EditConfigScreen screen = ClientUtils.getCurrentGuiAs(EditConfigScreen.class);
        if (screen == null) {
            return;
        }

        ConfigGroup root = ((EditConfigScreenAccessor) (Object) screen).lcFtbHook$getGroup();
        if (root == null) {
            return;
        }

        Map<String, ConfigValue<?>> configs = new HashMap<>();
        collectConfigs(root, configs);

        boolean[] changed = {false};
        team.getProperties().forEach((property, propertyValue) -> {
            // The properties screen groups entries by property namespace and
            // uses the property path as config id (e.g. ftbchunks.allow_pvp).
            String key = property.getId().getNamespace() + "." + property.getId().getPath();
            ConfigValue<?> config = configs.get(key);
            if (config == null) {
                return;
            }

            // Some property types expose a different value type than their
            // screen config works on (e.g. set-backed properties vs a
            // ListConfig). Skip those per entry instead of letting one
            // mismatch abort the sync for all remaining entries.
            try {
                Object newValue = ClientPendingState.getDisplayValue(property, propertyValue.getValue());
                if (Objects.equals(config.getValue(), newValue)) {
                    return;
                }

                setConfigValue(config, newValue);
                changed[0] = true;
            } catch (RuntimeException ignored) {
                // Type mismatch between property value and config; leave the
                // entry as it is.
            }
        });

        if (changed[0]) {
            screen.refreshWidgets();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setConfigValue(ConfigValue config, Object value) {
        config.setValue(config.copy(value));
    }

    private static void collectConfigs(ConfigGroup group, Map<String, ConfigValue<?>> out) {
        for (ConfigValue<?> value : group.getValues()) {
            out.put(group.getId() + "." + value.id, value);
        }
        for (ConfigGroup subgroup : group.getSubgroups()) {
            collectConfigs(subgroup, out);
        }
    }
}
