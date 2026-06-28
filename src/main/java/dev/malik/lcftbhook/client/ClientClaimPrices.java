package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.malik.lcftbhook.bank.BulkInsufficientFundsClaimResult;
import dev.malik.lcftbhook.bank.InsufficientFundsClaimResult;
import dev.malik.lcftbhook.util.MoneyUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public final class ClientClaimPrices {
    public static final Set<String> LC_CLAIM_RESULT_IDS = Set.of(
            InsufficientFundsClaimResult.RESULT_ID,
            BulkInsufficientFundsClaimResult.RESULT_ID,
            "message.lc_ftb_hook.claim_rank_denied"
    );

    private static final Color4I LABEL_COLOR = Color4I.rgb(0xA0A0A0);
    private static final Color4I SEPARATOR_COLOR = Color4I.rgb(0x606060);
    private static final Color4I VALUE_COLOR = Color4I.rgb(0xFFFFFF);
    private static final Color4I PERIOD_COLOR = Color4I.rgb(0x909090);

    private static long claimPrice = -1L;
    private static long forceLoadUpkeepPrice = -1L;
    private static int upkeepPeriodMinutes = -1;
    private static int freeChunks;
    private static int claimedChunks;
    private static long mobGriefProtectionPrice = -1L;
    private static long explosionProtectionPrice = -1L;
    private static long pvpDisablePrice = -1L;
    private static long blockInteractProtectionPrice = -1L;
    private static long blockEditProtectionPrice = -1L;
    private static long entityInteractProtectionPrice = -1L;
    private static int landChunkGroupSize = -1;
    private static boolean balanceSynced;
    private static boolean balanceEmpty = true;
    @Nullable
    private static String balanceText;
    private static int lastUpdateTotalChunks = 1;
    private static Map<String, Integer> lastProblems = Map.of();

    private ClientClaimPrices() {
    }

    public static void update(
            long claim,
            long forceLoadUpkeep,
            int upkeepPeriod,
            int free,
            int claimed,
            boolean syncedBalance,
            boolean emptyBalance,
            String balance,
            long mobGrief,
            long explosion,
            long pvpDisable,
            long blockInteract,
            long blockEdit,
            long entityInteract,
            int landGroupSize
    ) {
        claimPrice = claim;
        forceLoadUpkeepPrice = forceLoadUpkeep;
        upkeepPeriodMinutes = upkeepPeriod;
        freeChunks = free;
        claimedChunks = claimed;
        balanceSynced = syncedBalance;
        balanceEmpty = emptyBalance;
        balanceText = balance;
        mobGriefProtectionPrice = mobGrief;
        explosionProtectionPrice = explosion;
        pvpDisablePrice = pvpDisable;
        blockInteractProtectionPrice = blockInteract;
        blockEditProtectionPrice = blockEdit;
        entityInteractProtectionPrice = entityInteract;
        landChunkGroupSize = landGroupSize;
    }

    public static int landChunkGroupSize() {
        return landChunkGroupSize > 0 ? landChunkGroupSize : 5;
    }

    public static int upkeepPeriodMinutes() {
        return upkeepPeriodMinutes;
    }

    @Nullable
    public static Long protectionPrice(String propertyKey) {
        return switch (propertyKey) {
            case "allow_mob_griefing" -> mobGriefProtectionPrice >= 0L ? mobGriefProtectionPrice : null;
            case "allow_explosions" -> explosionProtectionPrice >= 0L ? explosionProtectionPrice : null;
            case "allow_pvp" -> pvpDisablePrice >= 0L ? pvpDisablePrice : null;
            case "block_interact_mode" -> blockInteractProtectionPrice >= 0L ? blockInteractProtectionPrice : null;
            case "block_edit_mode" -> blockEditProtectionPrice >= 0L ? blockEditProtectionPrice : null;
            case "entity_interact_mode" -> entityInteractProtectionPrice >= 0L ? entityInteractProtectionPrice : null;
            default -> null;
        };
    }

    @Nullable
    public static Long defaultProtectionPrice(String propertyKey) {
        return switch (propertyKey) {
            case "allow_mob_griefing" -> 10L;
            case "allow_explosions" -> 10L;
            case "allow_pvp" -> 5L;
            case "block_interact_mode" -> 15L;
            case "block_edit_mode" -> 15L;
            case "entity_interact_mode" -> 15L;
            default -> null;
        };
    }

    public static boolean protectionPricesSynced() {
        return mobGriefProtectionPrice >= 0L
                && explosionProtectionPrice >= 0L
                && pvpDisablePrice >= 0L
                && blockInteractProtectionPrice >= 0L
                && blockEditProtectionPrice >= 0L
                && entityInteractProtectionPrice >= 0L;
    }

    public static boolean isSynced() {
        return claimPrice >= 0L
                && forceLoadUpkeepPrice >= 0L
                && upkeepPeriodMinutes > 0
                && balanceSynced
                && protectionPricesSynced();
    }

    public static boolean isLcClaimResult(String resultId) {
        return LC_CLAIM_RESULT_IDS.contains(resultId);
    }

    public static void renderBottomPanel(GuiGraphics graphics, Theme theme, int x, int y) {
        int lineHeight = theme.getFontHeight() + 2;
        int cursor = x;

        cursor = drawSegment(theme, graphics, cursor, y, label("claim"), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, space(), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, formatEffectiveClaimPrice(), VALUE_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, separator(), SEPARATOR_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, label("balance"), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, space(), LABEL_COLOR);
        drawSegment(theme, graphics, cursor, y, formatBalance(), VALUE_COLOR);

        int upkeepY = y + lineHeight;
        cursor = x;
        cursor = drawSegment(theme, graphics, cursor, upkeepY, label("upkeep"), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, upkeepY, space(), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, upkeepY, formatPrice(forceLoadUpkeepPrice), VALUE_COLOR);
        cursor = drawSegment(theme, graphics, cursor, upkeepY, space(), PERIOD_COLOR);
        drawSegment(theme, graphics, cursor, upkeepY, formatUpkeepPeriod(upkeepPeriodMinutes), PERIOD_COLOR);
    }

    public static void noteChunkUpdate(int totalChunks, int changedChunks, Map<String, Integer> problems) {
        lastUpdateTotalChunks = totalChunks;
        lastProblems = problems;
    }

    public static MutableComponent claimProblemLine(String resultId) {
        if (BulkInsufficientFundsClaimResult.RESULT_ID.equals(resultId)) {
            int count = lastProblems.getOrDefault(resultId, lastUpdateTotalChunks);
            return insufficientFundsBulkMessage(count);
        }
        if (InsufficientFundsClaimResult.RESULT_ID.equals(resultId)) {
            int count = lastProblems.getOrDefault(resultId, 1);
            if (count > 1) {
                return Component.translatable(
                        "message.lc_ftb_hook.insufficient_funds_bulk_claim",
                        formatEffectiveClaimPrice(),
                        count,
                        formatBalance()
                );
            }
            return insufficientFundsMessage();
        }
        return Component.translatable(resultId);
    }

    public static MutableComponent insufficientFundsMessage() {
        return Component.translatable(
                InsufficientFundsClaimResult.RESULT_ID,
                formatEffectiveClaimPrice(),
                formatBalance()
        );
    }

    public static MutableComponent insufficientFundsBulkMessage(int chunkCount) {
        long unitPrice = nextClaimUnitPrice();
        return Component.translatable(
                BulkInsufficientFundsClaimResult.RESULT_ID,
                formatPrice(unitPrice * Math.max(chunkCount, 1)),
                chunkCount,
                formatBalance()
        );
    }

    private static long nextClaimUnitPrice() {
        return claimedChunks < freeChunks ? 0L : claimPrice;
    }

    private static Component formatEffectiveClaimPrice() {
        return formatPrice(nextClaimUnitPrice());
    }

    private static int drawSegment(
            Theme theme,
            GuiGraphics graphics,
            int x,
            int y,
            Component text,
            Color4I color
    ) {
        theme.drawString(graphics, text, x, y, color, 0);
        return x + theme.getStringWidth(text);
    }

    private static Component label(String key) {
        return Component.translatable("gui.lc_ftb_hook.label." + key);
    }

    private static Component separator() {
        return Component.translatable("gui.lc_ftb_hook.separator");
    }

    private static Component space() {
        return Component.literal(" ");
    }

    private static Component formatBalance() {
        if (balanceEmpty) {
            return Component.translatable("message.lc_ftb_hook.balance_empty");
        }
        return Component.literal(balanceText == null ? "" : balanceText);
    }

    private static Component formatPrice(long amount) {
        if (amount <= 0L) {
            return Component.translatable("gui.lc_ftb_hook.price_free");
        }
        return MoneyUtil.fromCopper(amount).getText();
    }

    private static Component formatUpkeepPeriod(int minutes) {
        if (minutes % 1440 == 0) {
            int days = minutes / 1440;
            return days == 1
                    ? Component.translatable("gui.lc_ftb_hook.period.per_day")
                    : Component.translatable("gui.lc_ftb_hook.period.per_days", days);
        }
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours == 1
                    ? Component.translatable("gui.lc_ftb_hook.period.per_hour")
                    : Component.translatable("gui.lc_ftb_hook.period.per_hours", hours);
        }
        return minutes == 1
                ? Component.translatable("gui.lc_ftb_hook.period.per_minute")
                : Component.translatable("gui.lc_ftb_hook.period.per_minutes", minutes);
    }
}
