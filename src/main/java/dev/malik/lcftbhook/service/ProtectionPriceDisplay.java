package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.malik.lcftbhook.client.ClientClaimPrices;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.util.MoneyUtil;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;

public final class ProtectionPriceDisplay {
    private ProtectionPriceDisplay() {
    }

    @Nullable
    public static Long pricePerChunkForConfigId(String configId) {
        String key = normalizePropertyKey(configId);
        if (key == null) {
            return null;
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            Long synced = ClientClaimPrices.protectionPrice(key);
            if (synced != null) {
                return synced;
            }
            return ClientClaimPrices.defaultProtectionPrice(key);
        }

        return switch (key) {
            case "allow_mob_griefing" -> LCFtbHookConfig.SERVER.mobGriefProtectionPrice.get();
            case "allow_explosions" -> LCFtbHookConfig.SERVER.explosionProtectionPrice.get();
            case "allow_pvp" -> LCFtbHookConfig.SERVER.pvpDisablePrice.get();
            case "block_interact_mode" -> LCFtbHookConfig.SERVER.blockInteractProtectionPrice.get();
            case "block_edit_mode" -> LCFtbHookConfig.SERVER.blockEditProtectionPrice.get();
            case "entity_interact_mode" -> LCFtbHookConfig.SERVER.entityInteractProtectionPrice.get();
            default -> null;
        };
    }

    @Nullable
    public static Long pricePerChunkForProperty(TeamProperty<?> property) {
        return pricePerChunkForConfigId(ProtectionPricing.propertyKey(property));
    }

    public static boolean isProtectionConfigId(String configId) {
        return pricePerChunkForConfigId(configId) != null;
    }

    public static boolean isActiveBillableSetting(String configId, Object value) {
        String key = normalizePropertyKey(configId);
        if (key == null) {
            return false;
        }

        return switch (key) {
            case "allow_mob_griefing", "allow_explosions", "allow_pvp" ->
                    value instanceof Boolean enabled && !enabled;
            case "block_interact_mode", "block_edit_mode", "entity_interact_mode" ->
                    value instanceof PrivacyMode mode && mode != PrivacyMode.PUBLIC;
            default -> false;
        };
    }

    public static Component formatPricePerChunk(long copper) {
        return MoneyUtil.fromCopper(copper).getText();
    }

    public static Component upkeepPeriodLabel() {
        int minutes = FMLEnvironment.dist == Dist.CLIENT ? ClientClaimPrices.upkeepPeriodMinutes() : -1;
        if (minutes <= 0) {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                return formatUpkeepPeriodLabel(60);
            }
            minutes = LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get();
        }
        return formatUpkeepPeriodLabel(minutes);
    }

    public static Component formatUpkeepPeriodLabel(int minutes) {
        if (minutes <= 1) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.one_minute");
        }
        if (minutes < 60) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.minutes", minutes);
        }
        if (minutes == 60) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.one_hour");
        }
        if (minutes % 60 == 0) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.hours", minutes / 60);
        }
        if (minutes == 1440) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.one_day");
        }
        if (minutes % 1440 == 0) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.days", minutes / 1440);
        }
        return Component.translatable("message.lc_ftb_hook.upkeep_period.minutes", minutes);
    }

    @Nullable
    public static String normalizePropertyKey(String configId) {
        if (configId == null || configId.isBlank()) {
            return null;
        }

        String key = configId;
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        int slash = key.lastIndexOf('/');
        if (slash >= 0) {
            key = key.substring(slash + 1);
        }
        // Config paths from FTB Library group configs are dot-separated
        // (e.g. "ftbteamsconfig.ftbchunks.allow_pvp").
        int dot = key.lastIndexOf('.');
        if (dot >= 0) {
            key = key.substring(dot + 1);
        }
        return key;
    }
}
