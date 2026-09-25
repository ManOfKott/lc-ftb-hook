package dev.malik.lcftbhook.client.xaero;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.client.BuyableChunkChecker;
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.client.ClientRegionMembership;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.client.ClientUnsettledChunks;
import dev.malik.lcftbhook.client.ScreenOpener;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.CancelListingPayload;
import dev.malik.lcftbhook.network.CreateRegionAndAssignPayload;
import dev.malik.lcftbhook.network.RequestRegionAssignmentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds the region-assignment + marketplace entries appended to Xaero World
 * Map's right-click menu. This is a plain, ordinary class - deliberately NOT
 * part of {@code GuiMapMarketplaceMixin} itself. Every lambda declared here
 * (and every {@code new SomeScreen(...)} it captures) would otherwise compile
 * as a synthetic method of the mixin class, and Sponge Mixin has to relocate
 * every one of those into {@code GuiMap} when the mixin is applied - which
 * intermittently produces an unresolvable merged class later (the exact bug
 * that crashed "New Region from Selection", see {@link LcRightClickOption}'s
 * javadoc for the first occurrence of this pattern). Living in a normal,
 * un-mixed-in class means none of this needs relocating at all.
 * {@code GuiMapMarketplaceMixin} only calls into {@link #populate} via
 * reflection, so its own merged bytecode never hard-references this class either.
 */
public final class XaeroMarketplaceMenu {
    private XaeroMarketplaceMenu() {
    }

    public static void populate(GuiMap self, MapTileSelection selection, ArrayList<RightClickOption> options) {
        Optional<MapDimension> dimOpt = MapDimension.getCurrent();
        if (dimOpt.isEmpty()) {
            return;
        }
        MapDimension dim = dimOpt.get();

        List<String> chunkKeys = new ArrayList<>();
        List<MapChunk> chunks = new ArrayList<>();
        for (int cx = selection.getLeft(); cx <= selection.getRight(); cx++) {
            for (int cz = selection.getTop(); cz <= selection.getBottom(); cz++) {
                MapChunk chunk = dim.getRegion(XZ.regionFromChunk(cx, cz)).getChunkForAbsoluteChunkPos(XZ.of(cx, cz));
                if (chunk != null && chunk.getClaimedDate().isPresent()) {
                    chunkKeys.add(ChunkPosKey.encode(dim.dimension.location(), cx, cz));
                    chunks.add(chunk);
                }
            }
        }
        if (chunkKeys.isEmpty()) {
            return;
        }

        UUID localPlayer = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        String firstKey = chunkKeys.get(0);
        ChunkOwnership ownership = ClientChunkOwnership.get(firstKey);
        boolean single = chunkKeys.size() == 1;

        // Both "Change region" and "New Region from Selection" need at least
        // one chunk in the selection that actually belongs to the viewer's
        // own team - the server already silently ignores every other team's
        // chunks when assigning (RegionService#handleAssignmentRequest), so
        // without this check a selection over only foreign/unclaimed
        // territory would do nothing (or, for the "new region" case, still
        // create a genuinely empty region).
        boolean anyOwnTeamChunk = false;
        for (MapChunk c : chunks) {
            Team t = c.getTeam().orElse(null);
            if (t != null && localPlayer != null && t.getRankForPlayer(localPlayer).isMemberOrBetter()) {
                anyOwnTeamChunk = true;
                break;
            }
        }
        if (anyOwnTeamChunk) {
            addOption(options, "gui.lc_ftb_hook.regions.assign_to", self, screen ->
                    ScreenOpener.openRegionPicker(null, regionId ->
                            PacketDistributor.sendToServer(new RequestRegionAssignmentPayload(chunkKeys, regionId))
                    ));
            addOption(options, "gui.lc_ftb_hook.regions.new_from_selection", self, screen ->
                    ScreenOpener.openTextPrompt(
                            null,
                            Component.translatable("gui.lc_ftb_hook.regions.new_from_selection"),
                            Component.translatable("gui.lc_ftb_hook.regions.create_ghost").getString(),
                            name -> PacketDistributor.sendToServer(new CreateRegionAndAssignPayload(name, chunkKeys))
                    ));
        }

        // Single-chunk only, own team only (needed for ClientRegions to
        // resolve the region at all - it's team-private). Shown regardless
        // of whether the chunk has a private owner - "manage the region" is
        // about the region's own settings, not this one chunk's override.
        if (single) {
            Team singleTeam = chunks.get(0).getTeam().orElse(null);
            if (singleTeam != null && localPlayer != null && singleTeam.getRankForPlayer(localPlayer).isMemberOrBetter()) {
                UUID singleRegionId = ClientRegionMembership.rawRegionOf(firstKey);
                Region singleRegion = singleRegionId != null ? ClientRegions.get(singleRegionId) : ClientRegions.getDefault();
                if (singleRegion != null) {
                    UUID resolvedRegionId = singleRegion.id();
                    String label = "» " + Component.translatable("gui.lc_ftb_hook.regions.manage_region", singleRegion.name()).getString();
                    addOption(options, label, self, screen -> ScreenOpener.openRegionSettings(null, resolvedRegionId));
                }
            }
        }

        // "Sell as Country" needs at least one chunk in the selection that's
        // both state-owned (no private owner already) and actually part of
        // the viewer's own team's territory - checking only the first chunk
        // (as before) could offer this for someone else's land entirely.
        boolean anySellableAsCountry = false;
        for (int i = 0; i < chunkKeys.size(); i++) {
            ChunkOwnership o = i == 0 ? ownership : ClientChunkOwnership.get(chunkKeys.get(i));
            if (!o.isStateOwned() || o.isListed() || ClientUnsettledChunks.isUnsettled(chunkKeys.get(i))) {
                continue;
            }
            Team t = chunks.get(i).getTeam().orElse(null);
            if (t != null && localPlayer != null && t.getRankForPlayer(localPlayer).isMemberOrBetter()) {
                anySellableAsCountry = true;
                break;
            }
        }
        if (anySellableAsCountry) {
            addOption(options, "gui.lc_ftb_hook.marketplace.sell_as_country", self, screen ->
                    ScreenOpener.openSalePrice(null, chunkKeys, true));
        }
        if (single && !ownership.isStateOwned() && ownership.privateOwner().equals(localPlayer)) {
            // The land's own owning team, not necessarily the viewer's - see
            // resolveRegion's javadoc.
            Team firstOwningTeam = chunks.get(0).getTeam().orElse(null);
            if (!ownership.isListed() && !ClientUnsettledChunks.isUnsettled(firstKey)
                    && resolveRegion(firstKey, firstOwningTeam != null ? firstOwningTeam.getTeamId() : null).allowPrivateSelling()) {
                addOption(options, "gui.lc_ftb_hook.marketplace.sell", self, screen ->
                        ScreenOpener.openSalePrice(null, chunkKeys, false));
            }
            addOption(options, "gui.lc_ftb_hook.marketplace.settings", self, screen ->
                    ScreenOpener.openOverride(null, firstKey));
        }
        boolean anyPrivatelyOwnedInOwnTeam = false;
        for (int i = 0; i < chunkKeys.size(); i++) {
            ChunkOwnership o = i == 0 ? ownership : ClientChunkOwnership.get(chunkKeys.get(i));
            if (o.isStateOwned()) {
                continue;
            }
            Team t = chunks.get(i).getTeam().orElse(null);
            if (t != null && localPlayer != null && t.getRankForPlayer(localPlayer).isOfficerOrBetter()) {
                anyPrivatelyOwnedInOwnTeam = true;
                break;
            }
        }
        if (anyPrivatelyOwnedInOwnTeam) {
            addOption(options, "gui.lc_ftb_hook.marketplace.expropriate", self, screen ->
                    ScreenOpener.openExpropriatePrice(null, chunkKeys));
        }

        if (!single) {
            boolean anyCancelableListing = false;
            for (int i = 0; i < chunkKeys.size(); i++) {
                ChunkOwnership o = i == 0 ? ownership : ClientChunkOwnership.get(chunkKeys.get(i));
                if (!o.isListed()) {
                    continue;
                }
                if (o.isStateOwned()) {
                    Team t = chunks.get(i).getTeam().orElse(null);
                    if (t != null && localPlayer != null && t.getRankForPlayer(localPlayer).isOfficerOrBetter()) {
                        anyCancelableListing = true;
                        break;
                    }
                } else if (localPlayer != null && localPlayer.equals(o.privateOwner())) {
                    anyCancelableListing = true;
                    break;
                }
            }
            if (anyCancelableListing) {
                addOption(options, "gui.lc_ftb_hook.marketplace.cancel_listings_in_selection", self, screen ->
                        PacketDistributor.sendToServer(new dev.malik.lcftbhook.network.CancelListingsPayload(chunkKeys)));
            }
        }

        if (ownership.isListed()) {
            boolean allBuyable = true;
            for (int i = 0; i < chunkKeys.size(); i++) {
                ChunkOwnership entryOwnership = i == 0 ? ownership : ClientChunkOwnership.get(chunkKeys.get(i));
                Team entryTeam = chunks.get(i).getTeam().orElse(null);
                if (!BuyableChunkChecker.isBuyable(entryOwnership, entryTeam, chunkKeys.get(i), localPlayer)) {
                    allBuyable = false;
                    break;
                }
            }
            if (allBuyable) {
                addOption(options, "gui.lc_ftb_hook.marketplace.buy", self, screen ->
                        ScreenOpener.openBuyConfirm(null, chunkKeys, false));
            }

            // Buying "as country" is only actually possible server-side if
            // every chunk in the selection is listed and the buyer is an
            // officer of every one of those chunks' owning teams - otherwise
            // the server rejects the whole purchase outright. Checking that
            // here avoids offering an option that's guaranteed to fail.
            boolean allBuyableAsCountry = true;
            for (int i = 0; i < chunkKeys.size(); i++) {
                ChunkOwnership entryOwnership = i == 0 ? ownership : ClientChunkOwnership.get(chunkKeys.get(i));
                Team entryTeam = chunks.get(i).getTeam().orElse(null);
                if (!entryOwnership.isListed() || entryTeam == null || localPlayer == null
                        || !entryTeam.getRankForPlayer(localPlayer).isOfficerOrBetter()) {
                    allBuyableAsCountry = false;
                    break;
                }
            }
            if (allBuyableAsCountry) {
                addOption(options, "gui.lc_ftb_hook.marketplace.buy_as_country", self, screen ->
                        ScreenOpener.openBuyConfirm(null, chunkKeys, true));
            }
            if (single && (ownership.isStateOwned() || localPlayer.equals(ownership.privateOwner()))) {
                addOption(options, "gui.lc_ftb_hook.marketplace.cancel_listing", self, screen ->
                        PacketDistributor.sendToServer(new CancelListingPayload(firstKey)));
            }
        }
    }

    /**
     * {@code teamId} is the LAND's owning team, not necessarily the viewer's
     * own - uses the globally-broadcast public region cache (not
     * {@code ClientRegions}, which only ever holds the viewer's own team's
     * regions) so this resolves correctly for a privately-owned chunk sitting
     * in another team's territory too, same as {@code ProtectionInfoLines}.
     */
    private static Region resolveRegion(String chunkKey, @javax.annotation.Nullable UUID teamId) {
        UUID regionId = ClientRegionMembership.rawRegionOf(chunkKey);
        Region region = regionId != null ? dev.malik.lcftbhook.client.ClientPublicRegions.get(regionId)
                : teamId != null ? dev.malik.lcftbhook.client.ClientPublicRegions.getDefaultFor(teamId) : null;
        return region != null ? region : Region.createDefault();
    }

    private static void addOption(List<RightClickOption> options, String translationKeyOrLiteral, GuiMap self, Consumer<Screen> action) {
        boolean literal = translationKeyOrLiteral.startsWith("» ");
        String name = literal ? translationKeyOrLiteral.substring(2) : translationKeyOrLiteral;
        Component title = literal ? Component.literal(name) : Component.translatable(translationKeyOrLiteral);
        options.add(new LcRightClickOption(name, options.size(), self, title, action));
    }
}
