package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.RegionPropertyKey;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.network.SyncPublicRegionsPayload;
import dev.malik.lcftbhook.network.SyncRegionMembershipPayload;
import dev.malik.lcftbhook.network.SyncRegionsPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side handling of Regions: named, user-orderable subdivisions of a
 * team's claimed chunks that replace the old flat land/build chunk split.
 * Region creation/deletion/rename/reorder are immediate; chunk-to-region
 * assignment and protection property edits are queued (like the old chunk
 * type toggles) since both affect upkeep billing.
 */
public final class RegionService {
    private RegionService() {
    }

    public static Region createRegion(MinecraftServer server, Team team, String name) {
        Region region = Region.create(name);
        // New regions insert at regionOrder index 0 (dismantled first) until
        // dragged elsewhere - RegionListScreen's reversed display puts that
        // at the visual BOTTOM (least-prioritized), which is the right
        // default for an unconfigured region.
        FtbHookSavedData.get(server).addRegion(team.getTeamId(), region, true);
        syncRegionsToTeam(server, team);
        return region;
    }

    /** Creates a new region and queues the given selection into it in one action, e.g. from the marketplace/map context menu's "New Region from Selection...". */
    public static void createRegionAndAssign(ServerPlayer player, String name, List<String> chunkKeys) {
        if (name == null || name.isBlank() || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null || !team.getRankForPlayer(player.getUUID()).isMemberOrBetter()) {
            return;
        }
        Region region = createRegion(player.server, team, name.trim());
        handleAssignmentRequest(player, chunkKeys, region.id());
    }

    public static boolean deleteRegion(MinecraftServer server, Team team, UUID regionId) {
        boolean removed = FtbHookSavedData.get(server).removeRegion(team.getTeamId(), regionId);
        if (removed) {
            syncRegionsToTeam(server, team);
            broadcastRegionMembership(server);
            PendingStateSync.syncTeam(server, team);
        }
        return removed;
    }

    public static boolean renameRegion(MinecraftServer server, Team team, UUID regionId, String newName) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Region region = savedData.getRegion(team.getTeamId(), regionId);
        if (region == null || region.isDefault() || newName == null || newName.isBlank()) {
            return false;
        }
        savedData.updateRegion(team.getTeamId(), region.withName(newName.trim()));
        syncRegionsToTeam(server, team);
        return true;
    }

    public static boolean setAllowPrivateSelling(MinecraftServer server, Team team, UUID regionId, boolean allow) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Region region = savedData.getRegion(team.getTeamId(), regionId);
        if (region == null) {
            return false;
        }
        savedData.updateRegion(team.getTeamId(), region.withAllowPrivateSelling(allow));
        syncRegionsToTeam(server, team);
        return true;
    }

    /**
     * Who may buy a marketplace listing within this region. Re-syncing (both
     * the team-private and the globally-broadcast public region payloads,
     * both already sent by {@link #syncRegionsToTeam}) is what makes this
     * apply to already-listed chunks immediately - buyability/highlighting
     * are computed live from this same data on every render, not cached.
     */
    public static boolean setBuyerAccess(MinecraftServer server, Team team, UUID regionId, String access) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Region region = savedData.getRegion(team.getTeamId(), regionId);
        if (region == null) {
            return false;
        }
        savedData.updateRegion(team.getTeamId(), region.withBuyerAccess(access));
        syncRegionsToTeam(server, team);
        return true;
    }

    public static boolean reorderRegions(MinecraftServer server, Team team, List<UUID> newOrder) {
        boolean applied = FtbHookSavedData.get(server).setRegionOrder(team.getTeamId(), newOrder);
        if (applied) {
            syncRegionsToTeam(server, team);
        }
        return applied;
    }

    public static Region resolveRegion(MinecraftServer server, UUID teamId, String chunkKey) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        if (savedData.isChunkUnsettled(teamId, chunkKey)) {
            // Newly claimed/bought chunks have no protection at all until the
            // team's next successful upkeep settlement - an empty-properties
            // region is exactly "every property at its minimum" per Region's
            // own sparse-map convention (absent = minimum).
            return Region.createDefault();
        }
        UUID regionId = savedData.getChunkRegion(teamId, chunkKey);
        Region region = savedData.getRegion(teamId, regionId);
        return region != null ? region : Region.createDefault();
    }

    public static Region resolveRegion(MinecraftServer server, ClaimedChunk chunk) {
        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return Region.createDefault();
        }
        return resolveRegion(server, team.getTeamId(), ChunkPosKey.encode(chunk.getPos()));
    }

    /** Convenience for mixin enforcement code running on the server thread, mirrors the old land-chunk lookup shape. */
    public static Region resolveRegion(ClaimedChunk chunk) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && chunk != null ? resolveRegion(server, chunk) : Region.createDefault();
    }

    public static void handleAssignmentRequest(ServerPlayer player, List<String> chunkKeys, UUID targetRegionId) {
        if (chunkKeys.isEmpty()) {
            return;
        }
        MinecraftServer server = player.server;
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null || !team.getRankForPlayer(player.getUUID()).isMemberOrBetter()) {
            player.displayClientMessage(Component.translatable("message.lc_ftb_hook.region_assign_denied"), false);
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        if (savedData.getRegion(team.getTeamId(), targetRegionId) == null) {
            return;
        }
        if (savedData.isProtectionLocked(team.getTeamId())) {
            player.displayClientMessage(Component.translatable("message.lc_ftb_hook.protection_locked_change"), false);
            return;
        }

        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());
        int queued = 0;
        for (String rawKey : chunkKeys) {
            var pos = ChunkPosKey.toChunkDimPos(rawKey);
            ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(pos);
            if (chunk == null || chunk.getTeamData().getTeam() == null
                    || !chunk.getTeamData().getTeam().getTeamId().equals(team.getTeamId())) {
                continue;
            }
            String key = ChunkPosKey.encode(pos);
            UUID currentRegion = savedData.getChunkRegion(team.getTeamId(), key);
            if (currentRegion.equals(targetRegionId) && !pendingState.isPendingRegionAssignment(key)) {
                continue;
            }
            pendingState = pendingState.withPendingRegionAssignment(key, targetRegionId);
            queued++;
        }

        if (queued > 0) {
            savedData.setPendingState(team.getTeamId(), pendingState);
            PendingStateSync.syncTeam(server, team);
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.region_assign_queued", queued), false
            );
        }
    }

    public static TeamPendingState applyPendingRegionAssignments(MinecraftServer server, Team team, TeamPendingState pendingState) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        TeamPendingState updated = pendingState;
        boolean changed = false;

        boolean ownershipChanged = false;
        for (var entry : new java.util.HashMap<>(pendingState.pendingRegionAssignments()).entrySet()) {
            String chunkKey = entry.getKey();
            UUID targetRegionId = entry.getValue();
            savedData.setChunkRegion(teamId, chunkKey, targetRegionId);
            ownershipChanged |= reclampOverrideToRegion(savedData, teamId, chunkKey, targetRegionId);
            updated = updated.withoutPendingRegionAssignment(chunkKey);
            changed = true;
        }

        if (changed) {
            broadcastRegionMembership(server);
        }
        if (ownershipChanged) {
            dev.malik.lcftbhook.service.MarketplaceService.broadcastChunkOwnership(server);
        }
        return updated;
    }

    /**
     * Keeps a privately-owned chunk's marketplace state consistent with
     * whichever Region it now belongs to, right as a reassignment takes
     * effect:
     * <ul>
     *   <li>Its protection override may only ever be as loose or looser than
     *   the owning Region's own value (never stricter - enforced at write
     *   time in {@code MarketplaceService#setChunkOverride}); any entry that
     *   the new Region's baseline made too strict gets clamped down to that
     *   baseline.</li>
     *   <li>If the chunk is currently listed for private resale and the new
     *   Region has {@code allowPrivateSelling} disabled, the listing is
     *   cancelled outright - the chunk stays privately owned, it just can no
     *   longer be actively for sale there.</li>
     * </ul>
     */
    private static boolean reclampOverrideToRegion(FtbHookSavedData savedData, UUID teamId, String chunkKey, UUID targetRegionId) {
        ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
        if (ownership.isStateOwned()) {
            return false;
        }
        Region newRegion = savedData.getRegion(teamId, targetRegionId);
        if (newRegion == null) {
            return false;
        }
        boolean changed = false;
        Map<String, String> clamped = ownership.protectionOverride();
        if (!clamped.isEmpty()) {
            var working = new java.util.HashMap<>(clamped);
            for (var overrideEntry : ownership.protectionOverride().entrySet()) {
                ProtectionProperty property;
                try {
                    property = ProtectionProperty.byId(overrideEntry.getKey());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String regionValue = newRegion.propertyValue(property);
                if (!ProtectionResolution.isLooserOrEqual(property, overrideEntry.getValue(), regionValue)) {
                    working.put(overrideEntry.getKey(), regionValue);
                    changed = true;
                }
            }
            if (changed) {
                clamped = Map.copyOf(working);
            }
        }

        Long listingPrice = ownership.listingPricePerChunk();
        if (ownership.isListed() && !newRegion.allowPrivateSelling()) {
            listingPrice = null;
            changed = true;
        }

        if (changed) {
            savedData.setChunkOwnership(teamId, chunkKey, new ChunkOwnership(
                    ownership.privateOwner(), listingPrice, clamped, ownership.accessLists(), ownership.label()
            ));
        }
        return changed;
    }

    /**
     * A region's own property value just moved from {@code oldValue} to
     * {@code newValue} - either the officer/owner deliberately edited it
     * (immediately, or a queued edit finally being committed at settlement),
     * or the automatic upkeep dismantle/restore cycle moved it.
     * <p>
     * Only a genuine cross-tier LOOSENING (see
     * {@link ProtectionResolution#strictness}) clears anything, and only
     * chunk-level overrides in THIS region for THIS property that are now
     * stricter than the new baseline: the region owner deliberately decided
     * this for the whole region, including privately-owned chunks in it, so
     * there's no "wait for funds and it springs back" case to preserve an
     * override for here - unlike an automatic dismantle
     * ({@link ProtectionRollbackService#suspendProtection}), which only ever
     * lowers a region's value (never higher than it was), so calling this
     * from there is always a no-op by construction. A same-tier move (e.g.
     * Allies -> Team) never clears anything either, since the override
     * stays looser-or-equal to the new value regardless.
     */
    static boolean clearOverridesInvalidatedByRegionChange(
            FtbHookSavedData savedData, UUID teamId, UUID regionId, ProtectionProperty property, String newValue
    ) {
        Map<String, ChunkOwnership> ownershipForTeam = savedData.getChunkOwnershipForTeam(teamId);
        if (ownershipForTeam.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (var entry : ownershipForTeam.entrySet()) {
            String chunkKey = entry.getKey();
            ChunkOwnership ownership = entry.getValue();
            String overrideValue = ownership.protectionOverride().get(property.id());
            if (overrideValue == null) {
                continue;
            }
            if (!savedData.getChunkRegion(teamId, chunkKey).equals(regionId)) {
                continue;
            }
            if (ProtectionResolution.isLooserOrEqual(property, overrideValue, newValue)) {
                continue;
            }
            savedData.setChunkOwnership(teamId, chunkKey, ownership.withoutOverrideProperty(property.id()));
            changed = true;
        }
        return changed;
    }

    /**
     * Applies a protection-property edit made from the Region settings
     * screen. Cost-neutral changes (vs. the currently billed price) apply
     * immediately; anything else is queued for the next upkeep settlement,
     * mirroring the old {@code TeamPropertyHandler} dance but without any
     * FTB property system to fight — the region's stored value already is
     * the live/enforced one.
     */
    public static void setRegionProperty(
            ServerPlayer player,
            UUID regionId,
            ProtectionProperty property,
            String newValue
    ) {
        MinecraftServer server = player.server;
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null || !team.getRankForPlayer(player.getUUID()).isMemberOrBetter()) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null) {
            return;
        }
        if (savedData.isProtectionLocked(teamId)) {
            player.displayClientMessage(Component.translatable("message.lc_ftb_hook.protection_locked_change"), false);
            return;
        }

        String key = RegionPropertyKey.encode(regionId, property.id());
        TeamPendingState pendingState = savedData.getPendingState(teamId);

        if (ProtectionRollbackService.isDismantled(savedData, teamId, regionId, property, pendingState)) {
            TeamPendingState updated;
            if (property.isAtMinimum(newValue)) {
                updated = pendingState.withoutPendingProperty(key);
                notifyTeam(team, "message.lc_ftb_hook.protection_pending_cancelled");
            } else {
                updated = pendingState.withPendingProperty(key, newValue);
                notifyTeam(team, "message.lc_ftb_hook.protection_change_pending");
            }
            savedData.setPendingState(teamId, updated);
            syncState(server, team);
            return;
        }

        String liveValue = region.propertyValue(property);
        if (newValue.equals(liveValue)) {
            if (pendingState.hasPendingProperty(key)) {
                savedData.setPendingState(teamId, pendingState.withoutPendingProperty(key));
                notifyTeam(team, "message.lc_ftb_hook.protection_pending_cancelled");
                syncState(server, team);
            }
            return;
        }

        // ProtectionPricing.calculateTotalUpkeepCopper(server, team, pendingState)
        // (the 3-arg overload) prices via ProtectionRollbackService.pricingProperties,
        // which deliberately EXCLUDES any pendingProperties entry whose region is
        // currently at minimum - that's correct for a genuine dismantle-restore
        // entry (never bill a suspended protection while it's off), but it's
        // structurally indistinguishable from the single most common edit there
        // is: turning a protection on for the first time (live value is, by
        // definition, still at minimum at this point). Using that overload for
        // "newPrice" silently dropped every such edit from the comparison, so
        // oldPrice == newPrice always held and the change applied immediately
        // instead of ever going pending - skipping the affordability check a
        // genuine cost increase is supposed to get. Building the pricing map
        // explicitly and force-including this one key (already proven NOT to be
        // a real dismantle by the isDismantled() check above) fixes that.
        Map<String, String> basePricing = ProtectionRollbackService.pricingProperties(server, team, pendingState);
        // basePricing carries THIS key's own value from whatever was most
        // recently queued for it (if anything) - e.g. cycling Private ->
        // Public -> Allies in one sitting leaves Public queued when the
        // Allies request arrives. Comparing against that stale queued
        // target instead of the true live value made every subsequent
        // switch look like a price change relative to it, even switches
        // back into the SAME tier the live value already sits in (Allies
        // after Private, both cost-neutral). Force this one key back to the
        // live value for the "old" side of the comparison - every OTHER
        // already-queued property still correctly uses its own pending
        // target via basePricing.
        Map<String, String> oldPricing = new java.util.HashMap<>(basePricing);
        oldPricing.put(key, liveValue);
        long oldPrice = ProtectionPricing.calculateTotalUpkeepCopper(server, team, pendingState, oldPricing);
        Map<String, String> newPricing = new java.util.HashMap<>(basePricing);
        newPricing.put(key, newValue);
        long newPrice = ProtectionPricing.calculateTotalUpkeepCopper(server, team, pendingState, newPricing);
        TeamPendingState simulated = pendingState.withPendingProperty(key, newValue);

        if (oldPrice == newPrice) {
            Region updatedRegion = property.isAtMinimum(newValue)
                    ? region.withoutProperty(property.id())
                    : region.withProperty(property.id(), newValue);
            savedData.updateRegion(teamId, updatedRegion);
            if (clearOverridesInvalidatedByRegionChange(savedData, teamId, regionId, property, newValue)) {
                MarketplaceService.broadcastChunkOwnership(server);
            }
            if (pendingState.hasPendingProperty(key)) {
                savedData.setPendingState(teamId, pendingState.withoutPendingProperty(key));
            }
        } else {
            savedData.setPendingState(teamId, simulated);
            notifyTeam(team, "message.lc_ftb_hook.protection_change_pending");
        }
        syncState(server, team);
    }

    public static void onChunkUnclaimed(MinecraftServer server, ClaimedChunk chunk) {
        String chunkKey = ChunkPosKey.encode(chunk.getPos());
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        boolean changed = savedData.clearChunkRegion(chunkKey);
        if (savedData.clearChunkOwnership(chunkKey)) {
            MarketplaceService.broadcastChunkOwnership(server);
        }

        Team team = chunk.getTeamData().getTeam();
        if (team != null) {
            TeamPendingState pending = savedData.getPendingState(team.getTeamId());
            TeamPendingState cleared = pending.withoutPendingRegionAssignment(chunkKey);
            if (cleared != pending) {
                savedData.setPendingState(team.getTeamId(), cleared);
                PendingStateSync.syncTeam(server, team);
            }
        }

        if (changed) {
            broadcastRegionMembership(server);
        }
    }

    public static int countChunks(MinecraftServer server, Team team, UUID regionId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        int count = 0;
        for (ClaimedChunk chunk : chunkData.getClaimedChunks()) {
            if (savedData.getChunkRegion(teamId, ChunkPosKey.encode(chunk.getPos())).equals(regionId)) {
                count++;
            }
        }
        return count;
    }

    /** How many of this region's chunks are currently force-loaded (actual FTB Chunks state, no pending deltas). */
    public static int countForceLoadedChunks(MinecraftServer server, Team team, UUID regionId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        int count = 0;
        for (ClaimedChunk chunk : chunkData.getForceLoadedChunks()) {
            if (savedData.getChunkRegion(teamId, ChunkPosKey.encode(chunk.getPos())).equals(regionId)) {
                count++;
            }
        }
        return count;
    }

    /** Same as {@link #countForceLoadedChunks}, plus this region's share of queued pending force-load/unload requests - mirrors {@code ProtectionPricing.countEffectiveForceLoads}, broken down per region. */
    public static int countEffectiveForceLoadedChunks(MinecraftServer server, Team team, UUID regionId, TeamPendingState pendingState) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        UUID teamId = team.getTeamId();
        int count = countForceLoadedChunks(server, team, regionId);
        for (String chunkKey : pendingState.pendingForceLoads()) {
            if (savedData.getChunkRegion(teamId, chunkKey).equals(regionId)) {
                count++;
            }
        }
        for (String chunkKey : pendingState.pendingForceUnloads()) {
            if (savedData.getChunkRegion(teamId, chunkKey).equals(regionId)) {
                count--;
            }
        }
        return Math.max(count, 0);
    }

    public static SyncRegionMembershipPayload createMembershipPayload(MinecraftServer server) {
        return new SyncRegionMembershipPayload(FtbHookSavedData.get(server).getAllChunkRegions());
    }

    public static void broadcastRegionMembership(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(createMembershipPayload(server));
    }

    public static void syncMembershipToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createMembershipPayload(player.server));
    }

    public static SyncRegionsPayload createRegionsPayload(MinecraftServer server, UUID teamId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        java.util.Map<UUID, Integer> counts = new java.util.HashMap<>();
        java.util.Map<UUID, Integer> billableCounts = new java.util.HashMap<>();
        java.util.Map<UUID, Long> currentUpkeep = new java.util.HashMap<>();
        java.util.Map<UUID, Long> pendingUpkeep = new java.util.HashMap<>();
        java.util.Map<UUID, Integer> forceLoadCounts = new java.util.HashMap<>();
        java.util.Map<UUID, Long> forceLoadCurrentUpkeep = new java.util.HashMap<>();
        java.util.Map<UUID, Long> forceLoadPendingUpkeep = new java.util.HashMap<>();
        Team team = dev.malik.lcftbhook.teams.FtbTeamCatalog.resolve(server, teamId);
        if (team != null && FTBChunksAPI.api().isManagerLoaded()) {
            TeamPendingState pendingState = savedData.getPendingState(teamId);
            ProtectionPricing.ChunkCounts billable = ProtectionPricing.countBillableChunks(server, team);
            boolean chargeForceLoad = UpkeepGating.shouldChargeForceLoad(server, team);
            for (UUID regionId : savedData.getRegionOrder(teamId)) {
                counts.put(regionId, countChunks(server, team, regionId));
                Region region = savedData.getRegion(teamId, regionId);
                ProtectionPricing.RegionChunkCount rc = billable.perRegion().get(regionId);
                int billableChunks = rc != null ? rc.billableChunks() : 0;
                billableCounts.put(regionId, billableChunks);
                if (region != null) {
                    // regionBasePrice is priced per ProtectionProperty.PRICE_UNIT_CHUNKS
                    // chunks, not per single chunk - see ProtectionPricing.calculateProtectionCopper.
                    currentUpkeep.put(regionId,
                            (ProtectionPricing.regionBasePrice(region, Map.of()) * billableChunks) / ProtectionProperty.PRICE_UNIT_CHUNKS);
                    pendingUpkeep.put(regionId,
                            (ProtectionPricing.regionBasePrice(region, pendingState.pendingProperties()) * billableChunks) / ProtectionProperty.PRICE_UNIT_CHUNKS);
                }
                int flCurrentCount = countForceLoadedChunks(server, team, regionId);
                int flPendingCount = countEffectiveForceLoadedChunks(server, team, regionId, pendingState);
                forceLoadCounts.put(regionId, flCurrentCount);
                forceLoadCurrentUpkeep.put(regionId, chargeForceLoad ? ProtectionPricing.calculateForceLoadCopper(flCurrentCount) : 0L);
                forceLoadPendingUpkeep.put(regionId, chargeForceLoad ? ProtectionPricing.calculateForceLoadCopper(flPendingCount) : 0L);
            }
        }
        return new SyncRegionsPayload(
                savedData.getRegionOrder(teamId), savedData.getRegions(teamId),
                counts, billableCounts, currentUpkeep, pendingUpkeep,
                forceLoadCounts, forceLoadCurrentUpkeep, forceLoadPendingUpkeep
        );
    }

    public static void syncRegionsToTeam(MinecraftServer server, Team team) {
        SyncRegionsPayload payload = createRegionsPayload(server, team.getTeamId());
        for (ServerPlayer member : team.getOnlineMembers()) {
            PacketDistributor.sendToPlayer(member, payload);
        }
        broadcastPublicRegions(server);
    }

    /**
     * Every team's region names + protection values, broadcast to everyone -
     * unlike {@link #syncRegionsToTeam}'s payload (chunk counts and copper
     * prices stay team-private), this carries neither, so hovering another
     * team's chunk can show that team's actual protection status even for a
     * non-Default region (previously only resolvable for the viewer's own
     * team, since it relied on the team-scoped cache).
     */
    public static SyncPublicRegionsPayload createPublicRegionsPayload(MinecraftServer server) {
        Map<UUID, Region> regionsById = new java.util.HashMap<>();
        Map<UUID, UUID> defaultRegionByTeam = new java.util.HashMap<>();
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        for (Team team : dev.malik.lcftbhook.teams.FtbTeamCatalog.allStoredTeams(server)) {
            UUID teamId = team.getTeamId();
            for (Region region : savedData.getRegions(teamId).values()) {
                regionsById.put(region.id(), region);
                if (region.isDefault()) {
                    defaultRegionByTeam.put(teamId, region.id());
                }
            }
        }
        return new SyncPublicRegionsPayload(regionsById, defaultRegionByTeam);
    }

    public static void broadcastPublicRegions(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(createPublicRegionsPayload(server));
    }

    public static void syncPublicRegionsToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createPublicRegionsPayload(player.server));
    }

    public static void syncRegionsToPlayer(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, createRegionsPayload(player.server, team.getTeamId()));
    }

    private static void syncState(MinecraftServer server, Team team) {
        PendingStateSync.syncTeam(server, team);
        syncRegionsToTeam(server, team);
        WarStateSync.onUpkeepFactorsChanged(server, team);
    }

    private static void notifyTeam(Team team, String messageKey) {
        Component message = Component.translatable(messageKey);
        for (ServerPlayer member : team.getOnlineMembers()) {
            member.displayClientMessage(message, false);
        }
    }
}
