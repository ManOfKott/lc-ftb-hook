package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.RegionPropertyKey;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Region-based protection billing. Every region (including the auto-created
 * Default) prices its own non-minimum protection properties per chunk it
 * contains; the team-wide free chunk allowance is consumed against the
 * highest price-per-chunk region(s) first.
 */
public final class ProtectionPricing {
    private ProtectionPricing() {
    }

    public record RegionChunkCount(UUID regionId, int totalChunks, int billableChunks) {
    }

    public record ChunkCounts(int totalChunks, Map<UUID, RegionChunkCount> perRegion) {
        public static final ChunkCounts EMPTY = new ChunkCounts(0, Map.of());
    }

    public static ChunkCounts countBillableChunks(MinecraftServer server, Team team) {
        return countBillableChunks(server, team, new TeamPendingState());
    }

    public static ChunkCounts countBillableChunks(MinecraftServer server, Team team, TeamPendingState pendingState) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return ChunkCounts.EMPTY;
        }
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        return countBillableChunks(server, team, chunkData, pendingState);
    }

    public static ChunkCounts countBillableChunks(
            MinecraftServer server,
            Team team,
            ChunkTeamData chunkData,
            TeamPendingState pendingState
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();

        Map<UUID, Integer> raw = new LinkedHashMap<>();
        int total = 0;
        for (ClaimedChunk chunk : chunkData.getClaimedChunks()) {
            total++;
            String key = ChunkPosKey.encode(chunk.getPos());
            UUID regionId = effectiveRegion(savedData, teamId, key, pendingState);
            raw.merge(regionId, 1, Integer::sum);
        }

        List<UUID> byPriceDesc = new ArrayList<>(raw.keySet());
        byPriceDesc.sort(Comparator.comparingLong((UUID id) ->
                regionBasePrice(savedData, teamId, id, pendingState)).reversed());

        int remainingAllowance = FreeChunkAllowance.allowance();
        Map<UUID, RegionChunkCount> perRegion = new LinkedHashMap<>();
        for (UUID regionId : byPriceDesc) {
            int count = raw.get(regionId);
            int consumed = Math.min(remainingAllowance, count);
            remainingAllowance -= consumed;
            int billable = count - consumed;
            perRegion.put(regionId, new RegionChunkCount(regionId, count, billable));
        }
        return new ChunkCounts(total, perRegion);
    }

    private static UUID effectiveRegion(
            FtbHookSavedData savedData,
            UUID teamId,
            String chunkKey,
            TeamPendingState pendingState
    ) {
        if (pendingState.isPendingRegionAssignment(chunkKey)) {
            return pendingState.pendingRegionAssignmentTarget(chunkKey);
        }
        return savedData.getChunkRegion(teamId, chunkKey);
    }

    /** Sum of config prices for every non-minimum property on this region, given a pending-overlay map. */
    public static long regionBasePrice(Region region, Map<String, String> pendingOverrides) {
        long base = 0L;
        for (ProtectionProperty property : ProtectionProperty.values()) {
            String key = RegionPropertyKey.encode(region.id(), property.id());
            String value = pendingOverrides.containsKey(key) ? pendingOverrides.get(key) : region.propertyValue(property);
            if (!property.isAtMinimum(value)) {
                base += property.configPrice();
            }
        }
        return base;
    }

    private static long regionBasePrice(FtbHookSavedData savedData, UUID teamId, UUID regionId, TeamPendingState pendingState) {
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null) {
            region = Region.createDefault();
        }
        return regionBasePrice(region, pendingState.pendingProperties());
    }

    public static long calculateProtectionCopper(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            ChunkCounts counts
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        long copper = 0L;
        for (RegionChunkCount rc : counts.perRegion().values()) {
            if (rc.billableChunks() <= 0) {
                continue;
            }
            long base = regionBasePrice(savedData, teamId, rc.regionId(), pendingState);
            if (base > 0) {
                copper += base * rc.billableChunks();
            }
        }
        return copper;
    }

    public static long calculateForceLoadCopper(int forceLoadCount) {
        long forceLoadPrice = dev.malik.lcftbhook.config.LCFtbHookConfig.SERVER.forceLoadUpkeepPrice.get();
        if (forceLoadPrice > 0 && forceLoadCount > 0) {
            return forceLoadPrice * forceLoadCount;
        }
        return 0L;
    }

    public static MoneyValue calculateTotalUpkeepCost(MinecraftServer server, Team team, TeamPendingState pendingState) {
        return MoneyUtil.fromCopper(calculateTotalUpkeepCopper(server, team, pendingState));
    }

    public static long calculateTotalUpkeepCopper(MinecraftServer server, Team team, TeamPendingState pendingState) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        ChunkCounts counts = countBillableChunks(server, team, chunkData, pendingState);
        int forceLoadCount = countEffectiveForceLoads(chunkData, pendingState);
        long protectionCopper = UpkeepGating.shouldChargeProtection(server, team)
                ? calculateProtectionCopper(
                        server,
                        team.getTeamId(),
                        new TeamPendingState(ProtectionRollbackService.pricingProperties(server, team, pendingState),
                                pendingState.pendingForceLoads(), pendingState.pendingForceUnloads(),
                                pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds()),
                        counts
                )
                : 0L;
        long forceLoadCopper = UpkeepGating.shouldChargeForceLoad(server, team)
                ? calculateForceLoadCopper(forceLoadCount)
                : 0L;
        return protectionCopper + forceLoadCopper;
    }

    public static long calculateTotalUpkeepCopper(
            MinecraftServer server,
            Team team,
            TeamPendingState pendingState,
            Map<String, String> pricingOverrides
    ) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        ChunkCounts counts = countBillableChunks(server, team, chunkData, pendingState);
        int forceLoadCount = countEffectiveForceLoads(chunkData, pendingState);
        TeamPendingState overlay = new TeamPendingState(pricingOverrides,
                pendingState.pendingForceLoads(), pendingState.pendingForceUnloads(),
                pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds());
        long protectionCopper = UpkeepGating.shouldChargeProtection(server, team)
                ? calculateProtectionCopper(server, team.getTeamId(), overlay, counts)
                : 0L;
        long forceLoadCopper = UpkeepGating.shouldChargeForceLoad(server, team)
                ? calculateForceLoadCopper(forceLoadCount)
                : 0L;
        return protectionCopper + forceLoadCopper;
    }

    public static int countEffectiveForceLoads(ChunkTeamData chunkData, TeamPendingState pendingState) {
        int count = chunkData.getForceLoadedChunks().size();
        count += pendingState.pendingForceLoads().size();
        count -= pendingState.pendingForceUnloads().size();
        return Math.max(count, 0);
    }
}
