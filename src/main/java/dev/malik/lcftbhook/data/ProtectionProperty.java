package dev.malik.lcftbhook.data;

import dev.malik.lcftbhook.config.LCFtbHookConfig;

import java.util.List;
import java.util.function.Supplier;

/**
 * Mod-owned replacement for the 6 protection settings previously stored as
 * FTB {@code TeamProperty} values. Regions store their own values for these
 * (see {@link Region}), so this class owns serialization/pricing instead of
 * FTB Teams' property system, which only supports one value per team.
 */
public enum ProtectionProperty {
    ALLOW_MOB_GRIEFING("allow_mob_griefing", false, "true", () -> LCFtbHookConfig.SERVER.mobGriefProtectionPrice.get()),
    ALLOW_EXPLOSIONS("allow_explosions", false, "true", () -> LCFtbHookConfig.SERVER.explosionProtectionPrice.get()),
    ALLOW_PVP("allow_pvp", false, "true", () -> LCFtbHookConfig.SERVER.pvpDisablePrice.get()),
    BLOCK_INTERACT_MODE("block_interact_mode", true, PrivacyLevel.PUBLIC.name(), () -> LCFtbHookConfig.SERVER.blockInteractProtectionPrice.get()),
    BLOCK_EDIT_MODE("block_edit_mode", true, PrivacyLevel.PUBLIC.name(), () -> LCFtbHookConfig.SERVER.blockEditProtectionPrice.get()),
    ENTITY_INTERACT_MODE("entity_interact_mode", true, PrivacyLevel.PUBLIC.name(), () -> LCFtbHookConfig.SERVER.entityInteractProtectionPrice.get());

    /**
     * All 6 config prices above are denominated per this many billable
     * chunks, not per single chunk - a config value of {@code V} means
     * {@code V} copper is owed once {@code PRICE_UNIT_CHUNKS} chunks have
     * that property active, rounded DOWN (see {@code ProtectionPricing}'s
     * actual `* chunks / PRICE_UNIT_CHUNKS` formula). This lets a price be
     * effectively "0.something copper per chunk" (e.g. configPrice=1 means
     * nothing is charged below 10 chunks, then 1 copper at 10-19 chunks,
     * 2 copper at 20-29, etc.) without needing fractional money anywhere.
     */
    public static final long PRICE_UNIT_CHUNKS = 10L;

    /** Default per-region dismantle/restore order (top dismantled first, i.e. first here). */
    public static final List<ProtectionProperty> DEFAULT_ORDER = List.of(
            ENTITY_INTERACT_MODE, BLOCK_EDIT_MODE, BLOCK_INTERACT_MODE,
            ALLOW_MOB_GRIEFING, ALLOW_EXPLOSIONS, ALLOW_PVP
    );

    private final String id;
    private final boolean privacyMode;
    private final String minimumSerialized;
    private final Supplier<Long> configPrice;

    ProtectionProperty(String id, boolean privacyMode, String minimumSerialized, Supplier<Long> configPrice) {
        this.id = id;
        this.privacyMode = privacyMode;
        this.minimumSerialized = minimumSerialized;
        this.configPrice = configPrice;
    }

    public String id() {
        return id;
    }

    public boolean isPrivacyMode() {
        return privacyMode;
    }

    /** "true" for booleans (unprotected/allowed), {@code PrivacyLevel.PUBLIC} name for privacy modes. */
    public String minimumSerialized() {
        return minimumSerialized;
    }

    public boolean isAtMinimum(String serialized) {
        return minimumSerialized.equals(serialized);
    }

    public long configPrice() {
        return configPrice.get();
    }

    public static ProtectionProperty byId(String id) {
        for (ProtectionProperty property : values()) {
            if (property.id.equals(id)) {
                return property;
            }
        }
        throw new IllegalArgumentException("Unknown protection property id: " + id);
    }
}
