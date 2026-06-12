package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record UpkeepBreakdown(
        UUID teamId,
        MoneyValue totalCost,
        int periodMinutes,
        int chunkCount,
        int billableChunkCount,
        int forceLoadCount,
        long basePricePerChunk,
        long protectionCopper,
        long forceLoadCopper,
        List<ProtectionLine> protectionLines
) {
    public record ProtectionLine(String labelKey, long pricePerChunk, String extraArg) {
        public ProtectionLine(String labelKey, long pricePerChunk) {
            this(labelKey, pricePerChunk, null);
        }
    }

    public static UpkeepBreakdown capture(Team team, int chunkCount, int forceLoadCount, MoneyValue totalCost) {
        int periodMinutes = LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get();
        long base = ProtectionPricing.calculateBasePrice(team);
        int billableChunks = FreeChunkAllowance.billableChunkCount(chunkCount);
        long protectionCopper = base > 0 && billableChunks > 0 ? base * billableChunks : 0L;
        long forceLoadUnit = LCFtbHookConfig.SERVER.forceLoadUpkeepPrice.get();
        long forceLoadCopper = forceLoadUnit > 0 && forceLoadCount > 0 ? forceLoadUnit * forceLoadCount : 0L;

        return new UpkeepBreakdown(
                team.getTeamId(),
                totalCost,
                periodMinutes,
                chunkCount,
                billableChunks,
                forceLoadCount,
                base,
                protectionCopper,
                forceLoadCopper,
                collectProtectionLines(team)
        );
    }

    private static List<ProtectionLine> collectProtectionLines(Team team) {
        List<ProtectionLine> lines = new ArrayList<>();
        var config = LCFtbHookConfig.SERVER;

        if (!team.getProperty(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ALLOW_MOB_GRIEFING)) {
            lines.add(new ProtectionLine("message.lc_ftb_hook.upkeep_detail.mob_grief", config.mobGriefProtectionPrice.get()));
        }
        if (!team.getProperty(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ALLOW_EXPLOSIONS)) {
            lines.add(new ProtectionLine("message.lc_ftb_hook.upkeep_detail.explosions", config.explosionProtectionPrice.get()));
        }
        if (!team.getProperty(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ALLOW_PVP)) {
            lines.add(new ProtectionLine("message.lc_ftb_hook.upkeep_detail.pvp", config.pvpDisablePrice.get()));
        }

        var interactMode = team.getProperty(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.BLOCK_INTERACT_MODE);
        if (interactMode != dev.ftb.mods.ftbteams.api.property.PrivacyMode.PUBLIC) {
            lines.add(new ProtectionLine(
                    "message.lc_ftb_hook.upkeep_detail.block_interact",
                    config.blockInteractProtectionPrice.get(),
                    interactMode.name()
            ));
        }

        var editMode = team.getProperty(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.BLOCK_EDIT_MODE);
        if (editMode != dev.ftb.mods.ftbteams.api.property.PrivacyMode.PUBLIC) {
            lines.add(new ProtectionLine(
                    "message.lc_ftb_hook.upkeep_detail.block_edit",
                    config.blockEditProtectionPrice.get(),
                    editMode.name()
            ));
        }

        var entityInteractMode = team.getProperty(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ENTITY_INTERACT_MODE);
        if (entityInteractMode != dev.ftb.mods.ftbteams.api.property.PrivacyMode.PUBLIC) {
            lines.add(new ProtectionLine(
                    "message.lc_ftb_hook.upkeep_detail.entity_interact",
                    config.entityInteractProtectionPrice.get(),
                    entityInteractMode.name()
            ));
        }

        return lines;
    }

    public long forceLoadUnitPrice() {
        return LCFtbHookConfig.SERVER.forceLoadUpkeepPrice.get();
    }
}
