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
        public final ModConfigSpec.IntValue freeChunks;
        public final ModConfigSpec.IntValue landChunkGroupSize;
        public final ModConfigSpec.DoubleValue unclaimRefundRatio;
        public final ModConfigSpec.LongValue forceLoadUpkeepPrice;
        public final ModConfigSpec.IntValue upkeepPeriodMinutes;
        public final ModConfigSpec.LongValue mobGriefProtectionPrice;
        public final ModConfigSpec.LongValue explosionProtectionPrice;
        public final ModConfigSpec.LongValue pvpDisablePrice;
        public final ModConfigSpec.LongValue blockInteractProtectionPrice;
        public final ModConfigSpec.LongValue blockEditProtectionPrice;
        public final ModConfigSpec.LongValue entityInteractProtectionPrice;
        public final ModConfigSpec.DoubleValue warCostMultiplier;
        public final ModConfigSpec.BooleanValue warEnabled;
        public final ModConfigSpec.ConfigValue<List<? extends String>> protectionDismantleOrderBuild;
        public final ModConfigSpec.ConfigValue<List<? extends String>> protectionDismantleOrderLand;
        public final ModConfigSpec.BooleanValue debugTestTeamCommands;

        Server(ModConfigSpec.Builder builder) {
            builder.comment("LC FTB Hook server configuration").push("general");

            claimPrice = builder
                    .comment("Cost in copper units (main coin chain) to claim one chunk. Default: 10000 copper = 1 Diamond coin.")
                    .defineInRange("claimPrice", 10_000L, 0L, Long.MAX_VALUE);

            freeChunks = builder
                    .comment("The first N claimed chunks per team or player are free to claim and exempt from protection upkeep")
                    .defineInRange("freeChunks", 0, 0, Integer.MAX_VALUE);

            landChunkGroupSize = builder
                    .comment("Land chunks (state territory) pay the protection price once per group of this many chunks; the billable land chunk count is rounded up to the next full group (minimum 1 group when any land chunk is billable). Build chunks always pay per chunk. A value of 1 makes land cost the same as build.")
                    .defineInRange("landChunkGroupSize", 5, 1, Integer.MAX_VALUE);

            unclaimRefundRatio = builder
                    .comment("Fraction of the claim price refunded when unclaiming a chunk (0 = none, 1 = full refund, 0.8 = 80%)")
                    .defineInRange("unclaimRefundRatio", 0.8D, 0.0D, 1.0D);

            forceLoadUpkeepPrice = builder
                    .comment("Upkeep cost in copper units per force-loaded chunk per upkeep period (force-loading itself is free). Default: 1 Netherite coin (100000 copper).")
                    .defineInRange("forceLoadUpkeepPrice", 100_000L, 0L, Long.MAX_VALUE);

            upkeepPeriodMinutes = builder
                    .comment("How often upkeep is charged, in real-time minutes")
                    .defineInRange("upkeepPeriodMinutes", 60, 1, 10080);

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

            warCostMultiplier = builder
                    .comment("War upkeep exponent l. With base upkeep b and k incoming wars, incoming terms are b*l^n for n=0..k-1 (first war uses multiplier 1). Each outgoing war at 0-based index n costs target base upkeep * l^n.")
                    .defineInRange("warCostMultiplier", 1.2D, 1.0D, 100.0D);

            builder.pop();
            builder.comment("Order in which protections are disabled when upkeep cannot be paid (first = dropped first). Use FTB property id paths without namespace.").push("protectionDismantle");

            protectionDismantleOrderLand = builder
                    .comment("Land-chunk protections dismantled first when upkeep fails")
                    .defineList(
                            "protectionDismantleOrderLand",
                            List.of(
                                    "land_block_edit_mode",
                                    "land_block_interact_mode"
                            ),
                            obj -> obj instanceof String
                    );

            protectionDismantleOrderBuild = builder
                    .comment("Build-chunk protections dismantled after all land protections are off")
                    .defineList(
                            "protectionDismantleOrderBuild",
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
