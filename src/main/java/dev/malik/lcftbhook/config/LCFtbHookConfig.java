package dev.malik.lcftbhook.config;

import net.neoforged.neoforge.common.ModConfigSpec;

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
        public final ModConfigSpec.DoubleValue unclaimRefundRatio;
        public final ModConfigSpec.LongValue forceLoadUpkeepPrice;
        public final ModConfigSpec.IntValue upkeepPeriodMinutes;
        public final ModConfigSpec.LongValue mobGriefProtectionPrice;
        public final ModConfigSpec.LongValue explosionProtectionPrice;
        public final ModConfigSpec.LongValue pvpDisablePrice;
        public final ModConfigSpec.LongValue blockInteractProtectionPrice;
        public final ModConfigSpec.LongValue blockEditProtectionPrice;
        public final ModConfigSpec.LongValue entityInteractProtectionPrice;

        Server(ModConfigSpec.Builder builder) {
            builder.comment("LC FTB Hook server configuration").push("general");

            claimPrice = builder
                    .comment("Cost in copper units (main coin chain) to claim one chunk")
                    .defineInRange("claimPrice", 100L, 0L, Long.MAX_VALUE);

            freeChunks = builder
                    .comment("The first N claimed chunks per team or player are free to claim and exempt from protection upkeep")
                    .defineInRange("freeChunks", 0, 0, Integer.MAX_VALUE);

            unclaimRefundRatio = builder
                    .comment("Fraction of the claim price refunded when unclaiming a chunk (0 = none, 1 = full refund, 0.6 = 60%)")
                    .defineInRange("unclaimRefundRatio", 0.6D, 0.0D, 1.0D);

            forceLoadUpkeepPrice = builder
                    .comment("Upkeep cost in copper units per force-loaded chunk per upkeep period (force-loading itself is free)")
                    .defineInRange("forceLoadUpkeepPrice", 25L, 0L, Long.MAX_VALUE);

            upkeepPeriodMinutes = builder
                    .comment("How often upkeep is charged, in real-time minutes")
                    .defineInRange("upkeepPeriodMinutes", 60, 1, 10080);

            builder.pop();
            builder.comment("Per-protection base prices added to upkeep calculation (b in c = b * n)").push("protectionPrices");

            mobGriefProtectionPrice = builder
                    .comment("Price when mob griefing protection is enabled (Allow Mob Griefing = false)")
                    .defineInRange("mobGriefProtectionPrice", 10L, 0L, Long.MAX_VALUE);

            explosionProtectionPrice = builder
                    .comment("Price when explosion protection is enabled (Allow Explosion Damage = false)")
                    .defineInRange("explosionProtectionPrice", 10L, 0L, Long.MAX_VALUE);

            pvpDisablePrice = builder
                    .comment("Price when PvP is disabled (Allow PvP Combat = false)")
                    .defineInRange("pvpDisablePrice", 5L, 0L, Long.MAX_VALUE);

            blockInteractProtectionPrice = builder
                    .comment("Price when block interact mode is not public")
                    .defineInRange("blockInteractProtectionPrice", 15L, 0L, Long.MAX_VALUE);

            blockEditProtectionPrice = builder
                    .comment("Price when block edit mode is not public")
                    .defineInRange("blockEditProtectionPrice", 15L, 0L, Long.MAX_VALUE);

            entityInteractProtectionPrice = builder
                    .comment("Price when entity interact mode is not public")
                    .defineInRange("entityInteractProtectionPrice", 15L, 0L, Long.MAX_VALUE);

            builder.pop();
        }
    }
}
