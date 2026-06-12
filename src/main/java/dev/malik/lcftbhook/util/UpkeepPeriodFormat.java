package dev.malik.lcftbhook.util;

import net.minecraft.network.chat.Component;

public final class UpkeepPeriodFormat {
    private UpkeepPeriodFormat() {
    }

    public static Component format(int minutes) {
        if (minutes % 1440 == 0) {
            int days = minutes / 1440;
            return days == 1
                    ? Component.translatable("message.lc_ftb_hook.upkeep_period.one_day")
                    : Component.translatable("message.lc_ftb_hook.upkeep_period.days", days);
        }
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours == 1
                    ? Component.translatable("message.lc_ftb_hook.upkeep_period.one_hour")
                    : Component.translatable("message.lc_ftb_hook.upkeep_period.hours", hours);
        }
        return minutes == 1
                ? Component.translatable("message.lc_ftb_hook.upkeep_period.one_minute")
                : Component.translatable("message.lc_ftb_hook.upkeep_period.minutes", minutes);
    }
}
