package dev.malik.lcftbhook.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.malik.lcftbhook.client.ClientPendingState;
import dev.malik.lcftbhook.service.ClaimVisibilityService;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import net.minecraft.ChatFormatting;
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

    @WrapOperation(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/config/ConfigValue;getStringForGUI(Ljava/lang/Object;)Lnet/minecraft/network/chat/Component;"
            ),
            remap = false
    )
    private Component lcFtbHook$renderDrawValue(
            ConfigValue<?> config,
            Object value,
            Operation<Component> original
    ) {
        return formatEntryValue(config, value, original);
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
        return formatEntryValue(config, value, original);
    }

    @Inject(method = "addMouseOverText", at = @At("RETURN"), remap = false)
    private void lcFtbHook$appendEntryTooltip(
            dev.ftb.mods.ftblibrary.util.TooltipList list,
            CallbackInfo ci
    ) {
        String propertyKey = propertyKey(configValue);
        Long price = ProtectionPriceDisplay.pricePerChunkForConfigId(propertyKey);
        if (price != null) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.protection_price_per_chunk",
                    ProtectionPriceDisplay.formatPricePerChunk(price),
                    ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GRAY));
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Component formatEntryValue(
            ConfigValue<?> config,
            Object value,
            Operation<Component> original
    ) {
        String propertyKey = propertyKey(config);

        Long price = ProtectionPriceDisplay.pricePerChunkForConfigId(propertyKey);
        boolean hasPrice = price != null && price > 0;
        boolean hasPending = ClientPendingState.hasPendingProperty(propertyKey);

        // Untouched entries keep FTB's own rendering (base color, hover
        // brightening) by returning the original component unchanged.
        if (!hasPrice && !hasPending) {
            return original.call(config, value);
        }

        // Always render the actual (editable) value so cycling through
        // options stays visible while a pending change is queued.
        //
        // FTB draws the whole line with configValue.getColor() as the base
        // color, which can mismatch the value we render here. Style the value
        // text explicitly with the color belonging to the rendered value so
        // text and color always agree.
        Component valueText = original.call(config, value);
        int valueRgb = ((ConfigValue) config).getColor(value).rgba() & 0xFFFFFF;
        MutableComponent styledValue = valueText.copy().withStyle(style ->
                style.getColor() == null
                        ? style.withColor(TextColor.fromRgb(valueRgb))
                        : style);
        MutableComponent line = Component.empty().append(styledValue);

        if (hasPrice) {
            line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            if (ProtectionPriceDisplay.isActiveBillableSetting(propertyKey, value)) {
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

        // Show the pending tag whenever a change is queued, regardless of
        // whether the locally displayed value already matches it. This keeps
        // the indicator visible directly after Accept, before the server
        // revert has been synced back.
        if (hasPending) {
            Object pendingValue = ClientPendingState.getDisplayValue(propertyKey, value);
            if (!java.util.Objects.equals(pendingValue, value)) {
                Component pendingText = original.call(config, pendingValue);
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
        String path = config.getPath();
        if (path != null && !path.isBlank()) {
            return path;
        }
        return config.id;
    }
}
