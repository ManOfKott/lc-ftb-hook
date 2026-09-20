package dev.malik.lcftbhook.config;

/** Controls when force-load upkeep is actually charged for a period. */
public enum ForceLoadUpkeepMode {
    /** Charge every period regardless of whether anyone is online. */
    ALWAYS,
    /** Charge only when at least one of the team's own members is online. */
    DEFAULT
}
