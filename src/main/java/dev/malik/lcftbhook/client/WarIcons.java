package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;

public final class WarIcons {
    // Matches FTB Teams' own officer-rank icon (dev.ftb.mods.ftbteams.api.TeamRank),
    // confirmed via decompile - a plain sword item icon read as "just a weapon"
    // rather than reading as a military/defense emblem.
    public static final Icon ICON = Icons.SHIELD;

    private WarIcons() {
    }
}
