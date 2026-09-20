package dev.malik.lcftbhook.client.xaero;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.client.BuyableChunkChecker;
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.client.ClientRegionMembership;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Two always-on overlays on Xaero's World Map (registered into Xaero's own
 * {@code HighlighterRegistry}, the same mechanism ftbxaerocompat uses for
 * its claim-color tinting) - never the Minimap, since this class is only
 * ever registered into the World Map session's registry (see
 * {@code WorldMapSessionHighlighterMixin}, which hooks
 * {@code xaero.map.WorldMapSession}, not the separate Minimap mod):
 * <ul>
 *   <li>A Region BORDER line - same-team chunks in different Regions get a
 *   line drawn on the shared edge only, interior untinted, matching FTB's
 *   own claim-manager map.</li>
 *   <li>A continuous, solid marketplace fill - purple for chunks the local
 *   player already privately owns, orange for chunks listed for sale and
 *   actually buyable by them, blue for chunks privately owned by someone
 *   else that aren't listed. Always visible (no Ctrl needed), at a higher
 *   opacity than the thin border lines/native team tint, specifically so it
 *   stays readable even if a Region's own look happens to be similar.</li>
 * </ul>
 * <p>
 * This used to be Ctrl-hold-only and blinking. That was dropped for two
 * reasons: Xaero's highlighter caching only re-checks a highlighter on an
 * explicit external invalidation, not by polling every frame, which made a
 * live per-frame blink require pumping that invalidation on a timer -
 * workable, but it kept the whole cache thrashing while Ctrl was held
 * (laggy), and Minecraft's Ctrl key state can get stuck "held" if the game
 * window loses focus while the key is physically down (e.g. Alt-Tab or
 * screenshotting), leaving the highlight seemingly stuck on. A static,
 * always-on color needs neither: {@link #calculateRegionHash} only has to
 * change when the underlying data does, and that's already driven by
 * {@link ClientRegionMembership#update} / {@link ClientChunkOwnership#update}
 * explicitly calling {@link XaeroHighlightRefresh#refresh()} on sync.
 * <p>
 * {@code getColors} returns {@code [center, top, right, bottom, left]}
 * (confirmed from {@code ChunkHighlighter.getChunkHighlitColor}'s bytecode -
 * it blends each of those 4 edges against the corresponding neighboring
 * chunk's own reported color). Leaving {@code center} transparent and only
 * setting the edges that actually border a different Region is what turns
 * the border feature into a line instead of a solid-color fill; the
 * marketplace fill instead sets all 5 to the same solid color.
 * <p>
 * {@code regionHasHighlights} must NOT depend on a live, per-frame value:
 * Xaero's {@code RegionHighlightExistenceTracker} caches a "no highlights
 * here" result the first time a region is checked and only re-checks it on
 * an explicit data-invalidation event, not every frame - so it always
 * reports "might have highlights" whenever a dimension is loaded, matching
 * ftbxaerocompat's own unconditional pattern.
 * <p>
 * A plain (non-mixin) class - safe to reference lc_ftb_hook classes
 * directly, unlike the mixins that register an instance of this class.
 */
public class BuyableChunkHighlighter extends ChunkHighlighter {
    private static final int ORANGE_RGB = 0xFF9800;
    private static final int PURPLE_RGB = 0x9C27B0;
    private static final int BLUE_RGB = 0x2196F3;
    // Fully opaque (was 170/255) so the highlight fully replaces the
    // underlying team-color tint instead of alpha-blending with it - at
    // partial alpha the land's own color showed through and visibly mixed
    // with the marketplace color underneath.
    private static final int MARKETPLACE_ALPHA = 255;
    private static final int BORDER_RGB = 0xFFFFFF;
    private static final int BORDER_ALPHA = 200;

    public BuyableChunkHighlighter() {
        super(true);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> key, int regionX, int regionZ) {
        return MapDimension.getCurrent().isPresent();
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> key, int x, int z) {
        if (resolveMarketplaceColor(x, z) != 0) {
            return true;
        }
        return hasAnyBorderEdge(x, z);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> key, int x, int z) {
        int marketplaceRgb = resolveMarketplaceColor(x, z);
        int marketplacePacked = marketplaceRgb != 0 ? pack(marketplaceRgb, MARKETPLACE_ALPHA) : 0;

        RegionInfo self = regionInfo(x, z);
        int top = self != null ? borderColor(self, regionInfo(x, z - 1)) : 0;
        int right = self != null ? borderColor(self, regionInfo(x + 1, z)) : 0;
        int bottom = self != null ? borderColor(self, regionInfo(x, z + 1)) : 0;
        int left = self != null ? borderColor(self, regionInfo(x - 1, z)) : 0;

        if (marketplacePacked == 0 && top == 0 && right == 0 && bottom == 0 && left == 0) {
            return null;
        }

        // Region border edges take priority over the marketplace fill on
        // that specific edge (so the boundary line stays visible even on a
        // marketplace-highlighted chunk); any edge without a border just
        // continues the marketplace fill, same as the center.
        resultStore[0] = marketplacePacked;
        resultStore[1] = top != 0 ? top : marketplacePacked;
        resultStore[2] = right != 0 ? right : marketplacePacked;
        resultStore[3] = bottom != 0 ? bottom : marketplacePacked;
        resultStore[4] = left != 0 ? left : marketplacePacked;
        return resultStore;
    }

    private static boolean hasAnyBorderEdge(int x, int z) {
        RegionInfo self = regionInfo(x, z);
        if (self == null) {
            return false;
        }
        return borderColor(self, regionInfo(x, z - 1)) != 0
                || borderColor(self, regionInfo(x + 1, z)) != 0
                || borderColor(self, regionInfo(x, z + 1)) != 0
                || borderColor(self, regionInfo(x - 1, z)) != 0;
    }

    /** 0 = no border on this edge (same Region, different team, or no neighbor data). */
    private static int borderColor(RegionInfo self, @Nullable RegionInfo neighbor) {
        if (neighbor == null || !neighbor.team().equals(self.team()) || java.util.Objects.equals(self.region(), neighbor.region())) {
            return 0;
        }
        return pack(BORDER_RGB, BORDER_ALPHA);
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> key, int regionX, int regionZ) {
        // Stable constant - this only needs to change when the underlying
        // Region/ownership data does, and that's already handled by an
        // explicit XaeroHighlightRefresh.refresh() call on sync.
        return 0;
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> key, int x, int z) {
        return Component.empty();
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> key, int x, int z) {
        return null;
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> key, int x, int z, int width) {
    }

    /** {@code region} null means claimed-but-unassigned (the team's Default region). */
    private record RegionInfo(UUID team, @Nullable UUID region) {
    }

    /** 0 = no marketplace highlight for this chunk. */
    private static int resolveMarketplaceColor(int chunkX, int chunkZ) {
        Optional<MapDimension> dimOpt = MapDimension.getCurrent();
        if (dimOpt.isEmpty()) {
            return 0;
        }
        MapDimension dim = dimOpt.get();
        MapChunk chunk = getChunk(dim, chunkX, chunkZ);
        if (chunk == null || chunk.getClaimedDate().isEmpty()) {
            return 0;
        }
        String chunkKey = ChunkPosKey.encode(dim.dimension.location(), chunkX, chunkZ);
        ChunkOwnership ownership = ClientChunkOwnership.get(chunkKey);
        UUID localPlayer = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        Team team = chunk.getTeam().orElse(null);

        if (localPlayer != null && localPlayer.equals(ownership.privateOwner())) {
            return PURPLE_RGB;
        }
        if (BuyableChunkChecker.isBuyable(ownership, team, chunkKey, localPlayer)) {
            return ORANGE_RGB;
        }
        // Privately owned by someone else - shown regardless of whether it's
        // ALSO listed, so ownership awareness doesn't disappear just because
        // a members-only/allies-only restriction (or simply belonging to an
        // unrelated team) makes it non-buyable right now for this viewer.
        // Only the buyable-orange highlight (above) is gated on eligibility.
        if (!ownership.isStateOwned()) {
            return BLUE_RGB;
        }
        return 0;
    }

    @Nullable
    private static RegionInfo regionInfo(int chunkX, int chunkZ) {
        Optional<MapDimension> dimOpt = MapDimension.getCurrent();
        if (dimOpt.isEmpty()) {
            return null;
        }
        MapDimension dim = dimOpt.get();
        MapChunk chunk = getChunk(dim, chunkX, chunkZ);
        if (chunk == null || chunk.getClaimedDate().isEmpty()) {
            return null;
        }
        Team team = chunk.getTeam().orElse(null);
        if (team == null) {
            return null;
        }
        String chunkKey = ChunkPosKey.encode(dim.dimension.location(), chunkX, chunkZ);
        return new RegionInfo(team.getTeamId(), ClientRegionMembership.rawRegionOf(chunkKey));
    }

    private static int pack(int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (b << 24) | (g << 16) | (r << 8) | alpha;
    }

    @Nullable
    private static MapChunk getChunk(MapDimension dim, int chunkX, int chunkZ) {
        MapRegion r = dim.getRegion(XZ.regionFromChunk(chunkX, chunkZ));
        return r != null ? r.getChunkForAbsoluteChunkPos(XZ.of(chunkX, chunkZ)) : null;
    }
}
