package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.RegionPropertyKey;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.teams.FtbTeamCatalog;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record UpkeepBreakdown(
        UUID teamId,
        MoneyValue totalCost,
        int periodMinutes,
        int chunkCount,
        int forceLoadCount,
        long protectionCopper,
        long baseUpkeepCopper,
        long incomingWarCopper,
        long outgoingWarCopper,
        int incomingWarCount,
        int outgoingWarCount,
        List<RegionProtectionSection> regionSections,
        List<WarLine> warLines,
        List<PendingProtectionLine> pendingProtections,
        List<PendingWarLine> pendingWars,
        int pendingForceLoadCount,
        int pendingForceUnloadCount,
        int pendingRegionAssignmentCount
) {
    public record ProtectionLine(String labelKey, long pricePerChunk) {
    }

    public record RegionProtectionSection(
            String regionName,
            List<ProtectionLine> lines,
            long basePrice,
            int billableChunks,
            long protectionCopper
    ) {
    }

    public record WarLine(String displayName, long warCostCopper, boolean incoming) {
    }

    public record PendingProtectionLine(String labelKey, String regionName, String desiredValue, boolean dismantled) {
    }

    public record PendingWarLine(String displayName, boolean endWar) {
    }

    public static UpkeepBreakdown capture(
            MinecraftServer server,
            Team team,
            int forceLoadCount,
            MoneyValue totalCost,
            TeamPendingState pendingState
    ) {
        int periodMinutes = LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get();
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        var chunkData = dev.ftb.mods.ftbchunks.api.FTBChunksAPI.api().getManager().getOrCreateData(team);

        ProtectionPricing.ChunkCounts counts = ProtectionPricing.countBillableChunks(server, team, chunkData, pendingState);
        WarService.WarCostBreakdown warCosts = WarService.calculateWarCosts(server, team);

        List<RegionProtectionSection> sections = new ArrayList<>();
        long totalProtectionCopper = 0L;
        for (var rc : counts.perRegion().values()) {
            if (rc.billableChunks() <= 0) {
                continue;
            }
            Region region = savedData.getRegion(teamId, rc.regionId());
            if (region == null) {
                continue;
            }
            List<ProtectionLine> lines = collectProtectionLines(region);
            if (lines.isEmpty()) {
                continue;
            }
            long basePrice = ProtectionPricing.regionBasePrice(region, java.util.Map.of());
            // basePrice is priced per ProtectionProperty.PRICE_UNIT_CHUNKS
            // chunks, not per single chunk - see ProtectionPricing.calculateProtectionCopper.
            long copper = basePrice > 0 ? (basePrice * rc.billableChunks()) / ProtectionProperty.PRICE_UNIT_CHUNKS : 0L;
            totalProtectionCopper += copper;
            sections.add(new RegionProtectionSection(region.name(), lines, basePrice, rc.billableChunks(), copper));
        }

        return new UpkeepBreakdown(
                teamId,
                totalCost,
                periodMinutes,
                counts.totalChunks(),
                forceLoadCount,
                totalProtectionCopper,
                warCosts.baseUpkeepCopper(),
                warCosts.incomingWarCopper(),
                warCosts.outgoingWarCopper(),
                warCosts.incomingWarCount(),
                warCosts.outgoingWarCount(),
                sections,
                collectWarLines(server, team),
                collectPendingProtections(server, teamId, pendingState),
                collectPendingWars(server, team, pendingState),
                pendingState.pendingForceLoads().size(),
                pendingState.pendingForceUnloads().size(),
                pendingState.pendingRegionAssignments().size()
        );
    }

    public boolean hasPendingItems() {
        return !pendingProtections.isEmpty()
                || !pendingWars.isEmpty()
                || pendingForceLoadCount > 0
                || pendingForceUnloadCount > 0
                || pendingRegionAssignmentCount > 0;
    }

    private static List<WarLine> collectWarLines(MinecraftServer server, Team team) {
        List<WarLine> lines = new ArrayList<>();
        for (WarService.WarTeamView view : WarService.buildBilledIncomingViews(server, team)) {
            lines.add(new WarLine(view.displayName(), view.warCostCopper(), true));
        }
        for (WarService.WarTeamView view : WarService.buildBilledOutgoingViews(server, team)) {
            lines.add(new WarLine(view.displayName(), view.warCostCopper(), false));
        }
        return lines;
    }

    private static List<ProtectionLine> collectProtectionLines(Region region) {
        List<ProtectionLine> lines = new ArrayList<>();
        for (ProtectionProperty property : ProtectionProperty.values()) {
            if (!region.isAtMinimum(property)) {
                lines.add(new ProtectionLine(
                        "message.lc_ftb_hook.upkeep_detail." + property.id(),
                        property.configPrice()
                ));
            }
        }
        return lines;
    }

    private static List<PendingProtectionLine> collectPendingProtections(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        List<PendingProtectionLine> lines = new ArrayList<>();
        for (var entry : pendingState.pendingProperties().entrySet()) {
            UUID regionId = RegionPropertyKey.regionId(entry.getKey());
            ProtectionProperty property;
            try {
                property = ProtectionProperty.byId(RegionPropertyKey.propertyId(entry.getKey()));
            } catch (IllegalArgumentException e) {
                continue;
            }
            Region region = savedData.getRegion(teamId, regionId);
            String regionName = region != null ? region.name() : Region.DEFAULT_NAME;
            String labelKey = "message.lc_ftb_hook.upkeep_priority.protection." + property.id();
            if (ProtectionRollbackService.isDismantled(savedData, teamId, regionId, property, pendingState)) {
                lines.add(new PendingProtectionLine(labelKey, regionName, entry.getValue(), true));
            } else if (ProtectionRollbackService.hasPendingApply(savedData, teamId, regionId, property, pendingState)) {
                lines.add(new PendingProtectionLine(labelKey, regionName, entry.getValue(), false));
            }
        }
        return lines;
    }

    private static List<PendingWarLine> collectPendingWars(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState
    ) {
        List<PendingWarLine> lines = new ArrayList<>();
        for (UUID targetId : pendingState.pendingWarDeclares()) {
            lines.add(new PendingWarLine(resolveTeamName(server, targetId), false));
        }
        for (UUID targetId : pendingState.pendingWarEnds()) {
            lines.add(new PendingWarLine(resolveTeamName(server, targetId), true));
        }
        return lines;
    }

    private static String resolveTeamName(MinecraftServer server, UUID teamId) {
        Team team = FtbTeamCatalog.resolve(server, teamId);
        return team != null ? WarService.displayName(team) : teamId.toString();
    }

    public long totalWarCopper() {
        return incomingWarCopper + outgoingWarCopper;
    }
}
