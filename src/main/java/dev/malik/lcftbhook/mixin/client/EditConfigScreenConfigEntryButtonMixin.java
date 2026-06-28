package dev.malik.lcftbhook.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.malik.lcftbhook.client.ClientPendingState;
import dev.malik.lcftbhook.service.ClaimVisibilityService;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen$ConfigEntryButton", remap = false)
public class EditConfigScreenConfigEntryButtonMixin {
    @Shadow(remap = false)
    private ConfigValue<?> configValue;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/config/ConfigValue;getCanEdit()Z",
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private void lcFtbHook$lockClaimVisibility(CallbackInfo ci) {
        if (ClaimVisibilityService.isClaimVisibilityConfigId(propertyKey(configValue))) {
            configValue.setCanEdit(false);
        }
    }

    /**
     * FTB truncates the value column after {@code getStringForGUI}. Land entries
     * with {@code /5 chunks} plus a pending tag are often wider than build
     * entries, so the pending suffix was clipped. Replace the value-column
     * {@code drawString} text (the Color4I overload) with the full formatted line.
     */
    @WrapOperation(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/ui/Theme;drawString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;IILdev/ftb/mods/ftblibrary/icon/Color4I;I)I"
            ),
            remap = false
    )
    private int lcFtbHook$drawFullFormattedValue(
            Theme theme,
            GuiGraphics graphics,
            Object text,
            int x,
            int y,
            Color4I color,
            int flags,
            Operation<Integer> original
    ) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ConfigValue raw = configValue;
        Component formatted = buildFormattedLine(configValue, configValue.getValue(), (config, value) ->
                raw.getStringForGUI(value));
        return original.call(theme, graphics, formatted, x, y, color, flags);
    }

    @WrapOperation(
            method = "getValueStr",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/config/ConfigValue;getStringForGUI(Ljava/lang/Object;)Lnet/minecraft/network/chat/Component;"
            ),
            remap = false
    )
    private Component lcFtbHook$renderValueStr(
            ConfigValue<?> config,
            Object value,
            Operation<Component> original
    ) {
        return buildFormattedLine(config, value, original::call);
    }

    @Inject(method = "addMouseOverText", at = @At("RETURN"), remap = false)
    private void lcFtbHook$appendEntryTooltip(
            dev.ftb.mods.ftblibrary.util.TooltipList list,
            CallbackInfo ci
    ) {
        String propertyKey = propertyKey(configValue);
        Long basePrice = ProtectionPriceDisplay.pricePerChunkForConfigId(propertyKey);
        if (basePrice != null) {
            list.blankLine();
            long displayPrice = ProtectionPriceDisplay.effectiveProtectionPrice(basePrice);
            if (ProtectionPriceDisplay.isLandProtectionPropertyKey(propertyKey)) {
                list.add(Component.translatable(
                        "gui.lc_ftb_hook.protection_price_per_n_chunks",
                        ProtectionPriceDisplay.formatPricePerChunk(displayPrice),
                        ProtectionPriceDisplay.upkeepPeriodLabel(),
                        ProtectionPriceDisplay.landChunkGroupSize()
                ).withStyle(ChatFormatting.GRAY));
            } else {
                list.add(Component.translatable(
                        "gui.lc_ftb_hook.protection_price_per_chunk",
                        ProtectionPriceDisplay.formatPricePerChunk(displayPrice),
                        ProtectionPriceDisplay.upkeepPeriodLabel()
                ).withStyle(ChatFormatting.GRAY));
            }
            if (ProtectionPriceDisplay.showsIncomingWarSurcharge()) {
                list.add(Component.translatable(
                        "gui.lc_ftb_hook.protection_price_war_incoming",
                        ProtectionPriceDisplay.incomingWarCount(),
                        ProtectionPriceDisplay.incomingWarFactorLabel(),
                        ProtectionPriceDisplay.formatPricePerChunk(basePrice),
                        ProtectionPriceDisplay.formatPricePerChunk(displayPrice)
                ).withStyle(ChatFormatting.GOLD));
            }
        }

        if (ClientPendingState.hasPendingProperty(propertyKey)) {
            list.blankLine();
            list.add(Component.translatable("message.lc_ftb_hook.protection_change_pending")
                    .withStyle(ChatFormatting.GOLD));
            Object pendingValue = ClientPendingState.getDisplayValue(propertyKey, configValue.getValue());
            @SuppressWarnings({"unchecked", "rawtypes"})
            Component pendingText = ((ConfigValue) configValue).getStringForGUI(pendingValue);
            list.add(Component.translatable("gui.lc_ftb_hook.pending_value", pendingText)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    @FunctionalInterface
    private interface ValueTextFormatter {
        Component format(ConfigValue<?> config, Object value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Component buildFormattedLine(
            ConfigValue<?> config,
            Object value,
            ValueTextFormatter formatter
    ) {
        String propertyKey = propertyKey(config);

        Long basePrice = ProtectionPriceDisplay.pricePerChunkForConfigId(propertyKey);
        long price = basePrice != null ? ProtectionPriceDisplay.effectiveProtectionPrice(basePrice) : 0L;
        boolean hasPrice = basePrice != null && basePrice > 0;
        boolean hasPending = ClientPendingState.hasPendingProperty(propertyKey);

        if (!hasPrice && !hasPending) {
            return formatter.format(config, value);
        }

        Component valueText = formatter.format(config, value);
        TextColor protectionColor = ProtectionPriceDisplay.protectionAllowBooleanColor(propertyKey, value);
        int valueRgb = protectionColor != null
                ? protectionColor.getValue()
                : ((ConfigValue) config).getColor(value).rgba() & 0xFFFFFF;
        MutableComponent styledValue = valueText.copy().withStyle(style ->
                style.getColor() == null
                        ? style.withColor(protectionColor != null ? protectionColor : TextColor.fromRgb(valueRgb))
                        : style);
        MutableComponent line = Component.empty().append(styledValue);

        if (hasPrice) {
            line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            if (ProtectionPriceDisplay.isLandProtectionPropertyKey(propertyKey)) {
                int groupSize = ProtectionPriceDisplay.landChunkGroupSize();
                if (ProtectionPriceDisplay.isActiveBillableSetting(propertyKey, value)) {
                    line.append(Component.translatable(
                            "gui.lc_ftb_hook.protection_price_active_per_n_chunks",
                            ProtectionPriceDisplay.formatPricePerChunk(price),
                            groupSize
                    ).withStyle(ChatFormatting.GREEN));
                } else {
                    line.append(Component.translatable(
                            "gui.lc_ftb_hook.protection_price_inactive_per_n_chunks",
                            ProtectionPriceDisplay.formatPricePerChunk(price),
                            groupSize
                    ).withStyle(ChatFormatting.GRAY));
                }
            } else if (ProtectionPriceDisplay.isActiveBillableSetting(propertyKey, value)) {
                line.append(Component.translatable(
                        "gui.lc_ftb_hook.protection_price_active",
                        ProtectionPriceDisplay.formatPricePerChunk(price)
                ).withStyle(ChatFormatting.GREEN));
            } else {
                line.append(Component.translatable(
                        "gui.lc_ftb_hook.protection_price_inactive",
                        ProtectionPriceDisplay.formatPricePerChunk(price)
                ).withStyle(ChatFormatting.GRAY));
            }
        }

        if (hasPending) {
            Object pendingValue = ClientPendingState.getDisplayValue(propertyKey, value);
            if (!java.util.Objects.equals(pendingValue, value)) {
                Component pendingText = formatter.format(config, pendingValue);
                line.append(Component.literal(" ").append(
                        Component.translatable("gui.lc_ftb_hook.pending_value", pendingText)
                                .withStyle(ChatFormatting.GOLD)
                ));
            } else {
                line.append(Component.literal(" ").append(
                        Component.translatable("gui.lc_ftb_hook.pending")
                                .withStyle(ChatFormatting.GOLD)
                ));
            }
        }

        return line;
    }

    private static String propertyKey(ConfigValue<?> config) {
        return ProtectionPriceDisplay.protectionPropertyKey(config.id, config.getPath());
    }
}
