package dev.malik.lcftbhook.config;

/** Controls when protection upkeep is actually charged (and thus can be dismantled for non-payment) for a period. */
public enum ProtectionUpkeepMode {
    /** Charge even when nobody is online at all (not recommended). */
    HEAVY,
    /** Charge when at least one player is online anywhere on the server. */
    DEFAULT,
    /** Charge only when at least one of the team's own members is online. */
    LIGHT
}
