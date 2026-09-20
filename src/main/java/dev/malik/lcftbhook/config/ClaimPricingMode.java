package dev.malik.lcftbhook.config;

/**
 * How much a team's next chunk claim costs, as a function of how many
 * chunks they already have. See {@code claimPricing} section comments in
 * {@link LCFtbHookConfig} for the exact formulas.
 */
public enum ClaimPricingMode {
    /** Every claim costs the same flat {@code claimPrice}, regardless of how many chunks the team already has. */
    CONSTANT,
    /** Price grows by a fixed amount every {@code claimIncrementSize} claims. */
    LINEAR,
    /** Price grows by a fixed percentage every {@code claimIncrementSize} claims. */
    EXPONENTIAL
}
