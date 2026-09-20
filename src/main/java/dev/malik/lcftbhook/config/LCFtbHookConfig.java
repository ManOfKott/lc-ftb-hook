package dev.malik.lcftbhook.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class LCFtbHookConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    private LCFtbHookConfig() {
    }

    public static final class Server {
        public final ModConfigSpec.LongValue claimPrice;
        public final ModConfigSpec.EnumValue<ClaimPricingMode> claimPricingMode;
        public final ModConfigSpec.LongValue claimBasePrice;
        public final ModConfigSpec.LongValue claimIncrementPrice;
        public final ModConfigSpec.IntValue claimIncrementSize;
        public final ModConfigSpec.DoubleValue claimPriceGrowthPercent;
        public final ModConfigSpec.IntValue freeChunks;
        public final ModConfigSpec.DoubleValue unclaimRefundRatio;
        public final ModConfigSpec.LongValue forceLoadUpkeepPrice;
        public final ModConfigSpec.IntValue upkeepPeriodMinutes;
        public final ModConfigSpec.EnumValue<ForceLoadUpkeepMode> forceLoadUpkeepMode;
        public final ModConfigSpec.EnumValue<ProtectionUpkeepMode> protectionUpkeepMode;
        public final ModConfigSpec.LongValue mobGriefProtectionPrice;
        public final ModConfigSpec.LongValue explosionProtectionPrice;
        public final ModConfigSpec.LongValue pvpDisablePrice;
        public final ModConfigSpec.LongValue blockInteractProtectionPrice;
        public final ModConfigSpec.LongValue blockEditProtectionPrice;
        public final ModConfigSpec.LongValue entityInteractProtectionPrice;
        public final ModConfigSpec.DoubleValue warCostMultiplier;
        public final ModConfigSpec.DoubleValue warOutgoingCostMultiplier;
        public final ModConfigSpec.BooleanValue warEnabled;
        public final ModConfigSpec.ConfigValue<List<? extends String>> protectionDismantleOrder;
        public final ModConfigSpec.BooleanValue debugTestTeamCommands;

        Server(ModConfigSpec.Builder builder) {
            builder.comment("LC FTB Hook server configuration").push("general");

            claimPrice = builder
                    .comment(
                            "Only used when claimPricingMode = CONSTANT. Cost in copper units (main coin chain) to claim one chunk,",
                            "the same for every claim regardless of how many the team already has. Default: 10000 copper = 1 Diamond coin."
                    )
                    .defineInRange("claimPrice", 10_000L, 0L, Long.MAX_VALUE);

            claimPricingMode = builder
                    .comment(
                            "How the price of a team's NEXT chunk claim scales with how many chunks they already have.",
                            "The team's currently claimed chunk count (excluding any free-allowance chunks - see freeChunks",
                            "below) determines which \"increment\" (price tier) the next claim falls into; unclaiming lowers",
                            "the count and therefore the tier again, so the price a team pays always reflects their CURRENT",
                            "chunk count, not a running total. Every increment always starts counting from 0.",
                            "  CONSTANT    - flat price for every claim: price = claimPrice.",
                            "  LINEAR      - price grows by a fixed amount every claimIncrementSize claims:",
                            "                increment = floor(chunksAlreadyClaimed / claimIncrementSize)",
                            "                price = claimBasePrice + increment * claimIncrementPrice",
                            "  EXPONENTIAL - price grows by a fixed PERCENTAGE every claimIncrementSize claims:",
                            "                increment = floor(chunksAlreadyClaimed / claimIncrementSize)",
                            "                price = claimBasePrice * (1 + claimPriceGrowthPercent) ^ increment",
                            "Both LINEAR and EXPONENTIAL can produce prices with fractional copper - those are always",
                            "rounded DOWN to the nearest whole copper before being charged."
                    )
                    .defineEnum("claimPricingMode", ClaimPricingMode.EXPONENTIAL);

            claimBasePrice = builder
                    .comment(
                            "Used by claimPricingMode = LINEAR and EXPONENTIAL only: the price of a claim at increment 0",
                            "(a team's first claimIncrementSize chunks). Ignored under CONSTANT (see claimPrice instead)."
                    )
                    .defineInRange("claimBasePrice", 10_000L, 0L, Long.MAX_VALUE);

            claimIncrementSize = builder
                    .comment(
                            "Used by claimPricingMode = LINEAR and EXPONENTIAL only: how many chunks make up one price tier",
                            "(\"increment\"). Ignored under CONSTANT. Default: 20 (matches the default EXPONENTIAL 5% step)."
                    )
                    .defineInRange("claimIncrementSize", 20, 1, Integer.MAX_VALUE);

            claimIncrementPrice = builder
                    .comment(
                            "Used by claimPricingMode = LINEAR only: how much copper the price goes up by per increment",
                            "(every claimIncrementSize claims). Ignored by CONSTANT and EXPONENTIAL."
                    )
                    .defineInRange("claimIncrementPrice", 1_000L, 0L, Long.MAX_VALUE);

            claimPriceGrowthPercent = builder
                    .comment(
                            "Used by claimPricingMode = EXPONENTIAL only: the fractional price increase per increment",
                            "(every claimIncrementSize claims), e.g. 0.05 = +5% per increment, compounding. Ignored by",
                            "CONSTANT and LINEAR. Default: 0.05 (5% every 20 claims)."
                    )
                    .defineInRange("claimPriceGrowthPercent", 0.05D, 0.0D, 1.0D);

            freeChunks = builder
                    .comment("The first N claimed chunks per team or player are free to claim and exempt from protection upkeep")
                    .defineInRange("freeChunks", 0, 0, Integer.MAX_VALUE);

            unclaimRefundRatio = builder
                    .comment("Fraction of the claim price refunded when unclaiming a chunk (0 = none, 1 = full refund, 0.8 = 80%)")
                    .defineInRange("unclaimRefundRatio", 0.8D, 0.0D, 1.0D);

            forceLoadUpkeepPrice = builder
                    .comment("Upkeep cost in copper units per force-loaded chunk per upkeep period (force-loading itself is free). Default: 1 Netherite coin (100000 copper).")
                    .defineInRange("forceLoadUpkeepPrice", 100_000L, 0L, Long.MAX_VALUE);

            upkeepPeriodMinutes = builder
                    .comment("How often upkeep is charged, in real-time minutes")
                    .defineInRange("upkeepPeriodMinutes", 60, 1, 10080);

            forceLoadUpkeepMode = builder
                    .comment(
                            "When force-load upkeep is actually charged for a period:",
                            "ALWAYS = charged every period regardless of who is online.",
                            "DEFAULT = charged only when at least one of the team's own members is online."
                    )
                    .defineEnum("forceLoadUpkeepMode", ForceLoadUpkeepMode.DEFAULT);

            protectionUpkeepMode = builder
                    .comment(
                            "When protection upkeep is actually charged (and can therefore be dismantled for non-payment) for a period:",
                            "HEAVY = charged even when nobody is online at all (not recommended).",
                            "DEFAULT = charged when at least one player is online anywhere on the server.",
                            "LIGHT = charged only when at least one of the team's own members is online."
                    )
                    .defineEnum("protectionUpkeepMode", ProtectionUpkeepMode.DEFAULT);

            builder.pop();
            builder.comment("Per-protection base prices added to upkeep calculation (b in c = b * n)").push("protectionPrices");

            mobGriefProtectionPrice = builder
                    .comment("Price when mob griefing protection is enabled (Allow Mob Griefing = false). Default: 80 copper.")
                    .defineInRange("mobGriefProtectionPrice", 80L, 0L, Long.MAX_VALUE);

            explosionProtectionPrice = builder
                    .comment("Price when explosion protection is enabled (Allow Explosion Damage = false). Default: 70 copper (second cheapest).")
                    .defineInRange("explosionProtectionPrice", 70L, 0L, Long.MAX_VALUE);

            pvpDisablePrice = builder
                    .comment("Price when PvP is disabled (Allow PvP Combat = false). Default: 50 copper (cheapest protection).")
                    .defineInRange("pvpDisablePrice", 50L, 0L, Long.MAX_VALUE);

            blockInteractProtectionPrice = builder
                    .comment("Price when block interact mode is not public. Default: 100 copper.")
                    .defineInRange("blockInteractProtectionPrice", 100L, 0L, Long.MAX_VALUE);

            blockEditProtectionPrice = builder
                    .comment("Price when block edit mode is not public. Default: 100 copper.")
                    .defineInRange("blockEditProtectionPrice", 100L, 0L, Long.MAX_VALUE);

            entityInteractProtectionPrice = builder
                    .comment("Price when entity interact mode is not public. Default: 100 copper.")
                    .defineInRange("entityInteractProtectionPrice", 100L, 0L, Long.MAX_VALUE);

            builder.pop();
            builder.comment("War declarations between claim teams (teams or solo players with claimed chunks)").push("war");

            warEnabled = builder
                    .comment("Enable the war system. When false, war costs are ignored, war actions are blocked, and the war button is hidden on clients.")
                    .define("warEnabled", true);

            warOutgoingCostMultiplier = builder
                    .comment("Flat multiplier x for outgoing war cost. Declaring war on a team costs x * their base upkeep per period, regardless of how many wars you have declared.")
                    .defineInRange("warOutgoingCostMultiplier", 2.0D, 0.0D, 100.0D);

            warCostMultiplier = builder
                    .comment("Incoming war exponent l. With base upkeep b and k incoming wars, the incoming surcharge is b * sum(l^n for n=0..k-1). First incoming war uses l^0 = 1.")
                    .defineInRange("warCostMultiplier", 1.2D, 1.0D, 100.0D);

            builder.pop();
            builder.comment("Order in which a region's protections are disabled when upkeep cannot be paid (first = dropped first); applied per-region in region-list order (top of the list dismantled first). Use the protection property ids without namespace.").push("protectionDismantle");

            protectionDismantleOrder = builder
                    .comment("Per-region protection dismantle order")
                    .defineList(
                            "protectionDismantleOrder",
                            List.of(
                                    "entity_interact_mode",
                                    "block_edit_mode",
                                    "block_interact_mode",
                                    "allow_mob_griefing",
                                    "allow_explosions",
                                    "allow_pvp"
                            ),
                            obj -> obj instanceof String
                    );

            builder.pop();
            builder.comment("Debug-only features for development and testing").push("debug");

            debugTestTeamCommands = builder
                    .comment("Allow /lc_ftb_hook seed_test_teams, clear_test_teams, and count_test_teams. Keep disabled on production servers.")
                    .define("debugTestTeamCommands", false);

            builder.pop();
        }
    }
}
