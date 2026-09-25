package dev.malik.lcftbhook.client;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.PlayerAccessList;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import dev.malik.lcftbhook.service.ProtectionResolution;
import dev.malik.lcftbhook.util.MoneyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the hovered-chunk info panel's content (owner/region/sale/every
 * protection property) - shared between the Xaero World Map panel and the
 * FTB Chunks claim manager panel so both show identical information.
 */
public final class ProtectionInfoLines {
    private ProtectionInfoLines() {
    }

    public record Content(List<Component> lines, @Nullable UUID skinOwner) {
        public static final Content EMPTY = new Content(List.of(), null);
    }

    public static Content build(@Nullable MapChunk chunk, ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (chunk == null || chunk.getClaimedDate().isEmpty() || chunk.getTeam().isEmpty()) {
            return Content.EMPTY;
        }
        Team team = chunk.getTeam().get();

        String chunkKey = ChunkPosKey.encode(dimension.location(), chunkX, chunkZ);
        ChunkOwnership ownership = ClientChunkOwnership.get(chunkKey);
        UUID regionId = ClientRegionMembership.rawRegionOf(chunkKey);
        // Uses the globally-broadcast public region cache, not ClientRegions
        // (which only ever holds the viewer's own team's regions, kept
        // team-private since it also carries chunk counts/prices) - a chunk's
        // protection status needs to resolve correctly for every team, not
        // just the viewer's own, so a member of another team sees the exact
        // same info that team itself would.
        Region region = regionId != null ? ClientPublicRegions.get(regionId) : ClientPublicRegions.getDefaultFor(team.getTeamId());

        boolean privatelyOwned = !ownership.isStateOwned();

        List<Component> lines = new ArrayList<>();
        // The country/team name always shows - using FTB Teams' own configured
        // color via getColoredName() - even for a privately-owned chunk, since
        // the chunk legally stays part of the team's territory regardless.
        lines.add(team.getColoredName());
        if (privatelyOwned) {
            lines.add(Component.literal(resolveName(ownership.privateOwner())).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (region != null) {
            MutableComponent regionLine = Component.translatable("gui.lc_ftb_hook.chunk_region_short", region.name());
            if (ownership.label() != null && !ownership.label().isEmpty()) {
                // Same color as the region name itself - the label reads as
                // a continuation of it ("Region: Village - Shop"), not a
                // separate piece of info.
                regionLine.append(" - " + ownership.label());
            }
            lines.add(regionLine.withStyle(ChatFormatting.AQUA));
        }
        // Only shown if the viewer could actually buy it (or it's their own
        // listing) - a members-only/allies-only restriction (or simply not
        // being eligible for some other reason) hides the price, matching
        // the same eligibility the orange map highlight already uses, so a
        // listing you can't act on doesn't dangle a price in front of you.
        UUID localPlayer = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        boolean forSaleVisible = ownership.isListed() && (
                (localPlayer != null && localPlayer.equals(ownership.privateOwner()))
                        || dev.malik.lcftbhook.client.BuyableChunkChecker.isBuyable(ownership, team, chunkKey, localPlayer)
        );
        if (forSaleVisible) {
            lines.add(Component.translatable(
                    "gui.lc_ftb_hook.marketplace.for_sale_tooltip",
                    MoneyUtil.fromCopper(ownership.listingPricePerChunk()).getText()
            ).withStyle(ChatFormatting.GOLD));
        }
        if (ClientUnsettledChunks.isUnsettled(chunkKey)) {
            lines.add(Component.translatable("gui.lc_ftb_hook.marketplace_not_sellable_yet_1")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            lines.add(Component.translatable("gui.lc_ftb_hook.marketplace_not_sellable_yet_2")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (ClientPendingState.isPendingRegionAssignment(dimension, chunkX, chunkZ)) {
            UUID pendingTargetId = ClientPendingState.pendingRegionAssignmentTarget(dimension, chunkX, chunkZ);
            Region pendingTarget = pendingTargetId != null ? ClientRegions.get(pendingTargetId) : null;
            lines.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_region_short",
                    pendingTarget != null ? pendingTarget.name() : "?"
            ).withStyle(ChatFormatting.YELLOW));
        }
        if (chunk.getForceLoadedDate().isPresent()) {
            lines.add(Component.translatable("gui.lc_ftb_hook.chunk_force_loaded_short").withStyle(ChatFormatting.RED));
        }
        if (ClientPendingState.isPendingForceLoad(dimension, chunkX, chunkZ)) {
            lines.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_forceload", ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.YELLOW));
        } else if (ClientPendingState.isPendingForceUnload(dimension, chunkX, chunkZ)) {
            lines.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_forceunload", ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.YELLOW));
        }
        if (region != null) {
            // Uniform for every viewer: shows the chunk's actual effective
            // setting (region baseline, loosened by any private-owner
            // override) using the same tier notation and green=protected/
            // red=unprotected color convention as Region Settings and the
            // private chunk override screen - not a per-viewer "can I
            // personally interact" boolean, since that read the same "false"
            // value backwards (green there meant "allowed", i.e. unprotected,
            // the opposite of the convention everywhere else).
            for (ProtectionProperty property : ProtectionProperty.values()) {
                String liveValue = ProtectionResolution.effectiveValue(region, ownership, property);
                boolean protectedValue = !property.isAtMinimum(liveValue);
                MutableComponent line = Component.translatable("gui.lc_ftb_hook.protection_short." + property.id())
                        .append(": ")
                        .append(ProtectionValueFormat.formatValue(liveValue, property).copy()
                                .withStyle(protectedValue ? ChatFormatting.GREEN : ChatFormatting.RED));
                // Privacy-mode properties only: if the viewer is individually
                // whitelisted on this chunk's own access list for this
                // property, they have access regardless of the tier shown
                // above - flag that explicitly rather than silently omit it.
                if (property.isPrivacyMode() && localPlayer != null) {
                    PlayerAccessList accessList = ownership.accessLists().get(property.id());
                    if (accessList != null && accessList.whitelist() && accessList.players().contains(localPlayer)) {
                        line.append(Component.translatable("gui.lc_ftb_hook.protection_access_you_suffix")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                }
                lines.add(line);
            }
        }

        return new Content(lines, privatelyOwned ? ownership.privateOwner() : null);
    }

    private static String resolveName(UUID playerId) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            var info = connection.getPlayerInfo(playerId);
            if (info != null) {
                return info.getProfile().getName();
            }
        }
        // Offline, or simply never seen online this session (regardless of
        // team) - the tab-list-backed PlayerInfo above has nothing for them.
        // ClientPlayerProfiles is a server-resolved fallback for exactly
        // this case; request() is a no-op after the first call per UUID.
        var resolved = ClientPlayerProfiles.get(playerId);
        if (resolved != null) {
            return resolved.getName();
        }
        ClientPlayerProfiles.request(playerId);
        return playerId.toString().substring(0, 8);
    }
}
