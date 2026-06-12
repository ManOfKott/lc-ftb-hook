package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.util.MoneyMessageUtil;
import dev.malik.lcftbhook.util.MoneyUtil;
import dev.malik.lcftbhook.util.UpkeepPeriodFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class UpkeepMessageBuilder {
    private static final String DETAILS_COMMAND = "/" + LCFtbHook.MOD_ID + " upkeep_details";

    private UpkeepMessageBuilder() {
    }

    public static Component buildSummary(UpkeepBreakdown breakdown) {
        Component amount = MoneyMessageUtil.formatValue(breakdown.totalCost());
        Component period = UpkeepPeriodFormat.format(breakdown.periodMinutes());

        MutableComponent message = Component.translatable("message.lc_ftb_hook.upkeep_paid", amount, period)
                .withStyle(ChatFormatting.WHITE);
        message.append(Component.literal(" "));
        message.append(buildSeeMoreButton());
        return message;
    }

    public static Component buildDetails(UpkeepBreakdown breakdown) {
        MutableComponent message = Component.empty();

        message.append(Component.translatable("message.lc_ftb_hook.upkeep_detail.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        message.append("\n");

        appendLine(message, "message.lc_ftb_hook.upkeep_detail.period",
                styled(UpkeepPeriodFormat.format(breakdown.periodMinutes()), ChatFormatting.AQUA));
        appendLine(message, "message.lc_ftb_hook.upkeep_detail.total",
                MoneyMessageUtil.formatValue(breakdown.totalCost()).copy().withStyle(ChatFormatting.GREEN));
        message.append("\n");

        if (breakdown.chunkCount() > 0) {
            Component chunkLine = breakdown.billableChunkCount() < breakdown.chunkCount()
                    ? Component.translatable(
                            "message.lc_ftb_hook.upkeep_detail.chunks_with_free",
                            breakdown.chunkCount(),
                            breakdown.billableChunkCount()
                    )
                    : Component.literal(String.valueOf(breakdown.chunkCount()));
            appendLine(message, "message.lc_ftb_hook.upkeep_detail.chunks",
                    chunkLine.copy().withStyle(ChatFormatting.GREEN));
        }

        if (breakdown.forceLoadCount() > 0) {
            appendLine(message, "message.lc_ftb_hook.upkeep_detail.forceloads",
                    Component.literal(String.valueOf(breakdown.forceLoadCount())).withStyle(ChatFormatting.GREEN));
        }

        message.append("\n");
        message.append(Component.translatable("message.lc_ftb_hook.upkeep_detail.protection_heading")
                .withStyle(ChatFormatting.YELLOW));
        message.append("\n");

        if (breakdown.protectionLines().isEmpty()) {
            message.append(Component.translatable("message.lc_ftb_hook.upkeep_detail.no_protections")
                    .withStyle(ChatFormatting.GRAY));
            message.append("\n");
        } else {
            for (UpkeepBreakdown.ProtectionLine line : breakdown.protectionLines()) {
                Component label = line.extraArg() == null
                        ? Component.translatable(line.labelKey())
                        : Component.translatable(line.labelKey(), line.extraArg());
                message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
                message.append(styled(label, ChatFormatting.YELLOW));
                message.append(Component.literal(" +").withStyle(ChatFormatting.GRAY));
                message.append(MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(line.pricePerChunk()))
                        .copy().withStyle(ChatFormatting.GOLD));
                message.append("\n");
            }
        }

        if (breakdown.billableChunkCount() > 0 && breakdown.protectionCopper() > 0) {
            message.append("\n");
            message.append(formatFormula(
                    "message.lc_ftb_hook.upkeep_detail.protection_formula",
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.basePricePerChunk())),
                    breakdown.billableChunkCount(),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.protectionCopper()))
            ));
            message.append("\n");
        }

        if (breakdown.forceLoadCount() > 0 && breakdown.forceLoadCopper() > 0) {
            message.append(formatFormula(
                    "message.lc_ftb_hook.upkeep_detail.forceload_formula",
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.forceLoadUnitPrice())),
                    breakdown.forceLoadCount(),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.forceLoadCopper()))
            ));
            message.append("\n");
        }

        if (breakdown.protectionCopper() > 0 && breakdown.forceLoadCopper() > 0) {
            message.append("\n");
            message.append(Component.translatable(
                    "message.lc_ftb_hook.upkeep_detail.total_formula",
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.protectionCopper())),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.forceLoadCopper())),
                    MoneyMessageUtil.formatValue(breakdown.totalCost())
            ).withStyle(ChatFormatting.GRAY));
        }

        return message;
    }

    private static Component buildSeeMoreButton() {
        return Component.translatable("message.lc_ftb_hook.upkeep_see_more")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, DETAILS_COMMAND))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.lc_ftb_hook.upkeep_see_more_hover")
                                        .withStyle(ChatFormatting.GRAY)
                        )));
    }

    private static void appendLine(MutableComponent message, String labelKey, Component value) {
        message.append(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY));
        message.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY));
        message.append(value);
        message.append("\n");
    }

    private static Component formatFormula(String key, Component unitPrice, int count, Component subtotal) {
        return Component.translatable(key, unitPrice, count, subtotal)
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component styled(Component component, ChatFormatting... formats) {
        return component.copy().withStyle(formats);
    }
}
