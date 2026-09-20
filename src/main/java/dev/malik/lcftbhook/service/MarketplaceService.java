package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.bank.BankAccountHelper;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.PlayerAccessList;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.SyncChunkOwnershipPayload;
import dev.malik.lcftbhook.util.MoneyMessageUtil;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chunk marketplace: officers/the owner can list individual chunks for sale
 * (state-owned "Sell as Country") or a private owner can resell their own
 * chunk ("Sell"); any eligible player can buy, or officers/the owner can
 * force-buy back ("Buy as Country"). A sold chunk stays part of the team's
 * territory - upkeep/region membership/dismantle order are unaffected - the
 * private owner can only ever loosen that one chunk's protection below the
 * owning region's baseline.
 */
public final class MarketplaceService {
    private static final int MAX_CHUNK_LABEL_LENGTH = 24;

    private MarketplaceService() {
    }

    public static void listForSale(ServerPlayer actor, List<String> chunkKeys, long pricePerChunk, boolean asCountry) {
        if (chunkKeys.isEmpty()) {
            return;
        }
        if (pricePerChunk <= 0) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_invalid_price"), false);
            return;
        }
        MinecraftServer server = actor.server;
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        int listed = 0;
        int denied = 0;
        int notFound = 0;

        for (String chunkKey : chunkKeys) {
            ClaimedChunk chunk = resolveChunk(chunkKey);
            if (chunk == null) {
                notFound++;
                continue;
            }
            Team team = chunk.getTeamData().getTeam();
            if (team == null) {
                notFound++;
                continue;
            }
            ChunkOwnership ownership = savedData.getChunkOwnership(team.getTeamId(), chunkKey);

            boolean eligible = asCountry
                    ? ownership.isStateOwned() && team.getRankForPlayer(actor.getUUID()).isOfficerOrBetter()
                    : !ownership.isStateOwned() && actor.getUUID().equals(ownership.privateOwner());
            // A private owner reselling their own chunk (not "as country" - that's the
            // team choosing to sell off state land, unaffected by this setting) needs
            // the owning Region to still allow it - an officer/owner may have disabled
            // private reselling for that Region after the chunk was already sold.
            if (eligible && !asCountry) {
                Region region = RegionService.resolveRegion(server, team.getTeamId(), chunkKey);
                eligible = region.allowPrivateSelling();
            }
            // A freshly claimed chunk (newly added to the team's territory,
            // not previously claimed) can't be listed until the team's next
            // successful upkeep settlement - prevents immediately flipping
            // land the moment it's claimed. A freshly BOUGHT chunk has no
            // such restriction - a private buyer must be able to resell
            // right away. Matches the client-side menu gating in
            // ChunkContextMenuBuilder/XaeroMarketplaceMenu.
            if (eligible && savedData.isChunkFreshlyClaimed(team.getTeamId(), chunkKey)) {
                eligible = false;
            }
            if (!eligible) {
                LCFtbHook.LOGGER.info(
                        "marketplace listForSale denied: chunk={} asCountry={} isStateOwned={} privateOwner={} actorRank={}",
                        chunkKey, asCountry, ownership.isStateOwned(), ownership.privateOwner(), team.getRankForPlayer(actor.getUUID())
                );
                denied++;
                continue;
            }

            savedData.setChunkOwnership(team.getTeamId(), chunkKey, ownership.withListingPrice(pricePerChunk));
            listed++;
        }

        if (listed > 0) {
            broadcastChunkOwnership(server);
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_listed", listed), false);
        }
        if (denied > 0) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_list_denied", denied), false);
        }
        if (notFound > 0) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_not_found", notFound), false);
        }
    }

    public static void cancelListing(ServerPlayer actor, String chunkKey) {
        MinecraftServer server = actor.server;
        ClaimedChunk chunk = resolveChunk(chunkKey);
        if (chunk == null) {
            return;
        }
        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        ChunkOwnership ownership = savedData.getChunkOwnership(team.getTeamId(), chunkKey);
        if (!ownership.isListed()) {
            return;
        }
        boolean eligible = ownership.isStateOwned()
                ? team.getRankForPlayer(actor.getUUID()).isOfficerOrBetter()
                : actor.getUUID().equals(ownership.privateOwner());
        if (!eligible) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_denied"), false);
            return;
        }
        savedData.setChunkOwnership(team.getTeamId(), chunkKey, ownership.withListingPrice(null));
        broadcastChunkOwnership(server);
    }

    /**
     * Cancels every listing in {@code chunkKeys} the actor is actually
     * eligible to cancel (their own private listing, or - as officer/owner -
     * a state-owned "as country" listing), silently skipping the rest (not
     * listed, or not theirs to cancel) rather than rejecting the whole
     * selection over one ineligible chunk.
     */
    public static void cancelListings(ServerPlayer actor, List<String> chunkKeys) {
        MinecraftServer server = actor.server;
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        int cancelled = 0;
        for (String chunkKey : chunkKeys) {
            ClaimedChunk chunk = resolveChunk(chunkKey);
            if (chunk == null) {
                continue;
            }
            Team team = chunk.getTeamData().getTeam();
            if (team == null) {
                continue;
            }
            ChunkOwnership ownership = savedData.getChunkOwnership(team.getTeamId(), chunkKey);
            if (!ownership.isListed()) {
                continue;
            }
            boolean eligible = ownership.isStateOwned()
                    ? team.getRankForPlayer(actor.getUUID()).isOfficerOrBetter()
                    : actor.getUUID().equals(ownership.privateOwner());
            if (!eligible) {
                continue;
            }
            savedData.setChunkOwnership(team.getTeamId(), chunkKey, ownership.withListingPrice(null));
            cancelled++;
        }
        if (cancelled > 0) {
            broadcastChunkOwnership(server);
        }
    }

    public static void buy(ServerPlayer buyer, List<String> chunkKeys, boolean asCountry) {
        if (chunkKeys.isEmpty()) {
            return;
        }
        MinecraftServer server = buyer.server;
        FtbHookSavedData savedData = FtbHookSavedData.get(server);

        // Hard error if any selected chunk isn't currently listed - buying an
        // unlisted chunk in a selection is a user-facing mistake, not a
        // silent partial purchase.
        record Entry(String key, ClaimedChunk chunk, Team team, ChunkOwnership ownership) {
        }
        List<Entry> entries = new java.util.ArrayList<>();
        for (String chunkKey : chunkKeys) {
            ClaimedChunk chunk = resolveChunk(chunkKey);
            Team team = chunk != null ? chunk.getTeamData().getTeam() : null;
            if (chunk == null || team == null) {
                continue;
            }
            ChunkOwnership ownership = savedData.getChunkOwnership(team.getTeamId(), chunkKey);
            if (!ownership.isListed()) {
                buyer.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_not_listed", chunkKey), false);
                return;
            }
            entries.add(new Entry(chunkKey, chunk, team, ownership));
        }
        if (entries.isEmpty()) {
            return;
        }

        if (asCountry) {
            for (Entry entry : entries) {
                if (!entry.team().getRankForPlayer(buyer.getUUID()).isOfficerOrBetter()) {
                    buyer.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_denied"), false);
                    return;
                }
            }
        } else {
            for (Entry entry : entries) {
                if (buyer.getUUID().equals(entry.ownership().privateOwner())) {
                    buyer.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_already_yours"), false);
                    return;
                }
                Region entryRegion = RegionService.resolveRegion(server, entry.team().getTeamId(), entry.key());
                String access = entryRegion.buyerAccess();
                var rank = entry.team().getRankForPlayer(buyer.getUUID());
                boolean allowed = switch (access) {
                    case dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_TEAM -> rank.isMemberOrBetter();
                    case dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALLIES -> rank.isAllyOrBetter();
                    default -> true;
                };
                if (!allowed) {
                    buyer.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_members_only"), false);
                    return;
                }
            }
        }

        long totalCopper = 0L;
        for (Entry entry : entries) {
            totalCopper += entry.ownership().listingPricePerChunk();
        }
        MoneyValue total = MoneyUtil.fromCopper(totalCopper);

        if (asCountry) {
            // A multi-chunk "as country" selection can span several teams (the
            // actor must be an officer of each - checked above); every team
            // pays only for its own chunks so one team can never end up
            // subsidizing another team's buyback.
            Map<UUID, Long> costPerTeam = new HashMap<>();
            Map<UUID, Team> teamsById = new HashMap<>();
            for (Entry entry : entries) {
                UUID teamId = entry.team().getTeamId();
                teamsById.put(teamId, entry.team());
                costPerTeam.merge(teamId, entry.ownership().listingPricePerChunk(), Long::sum);
            }
            Map<UUID, IBankAccount> accountsById = new HashMap<>();
            for (Map.Entry<UUID, Long> costEntry : costPerTeam.entrySet()) {
                IBankAccount account = BankAccountHelper.getAccountForTeam(server, teamsById.get(costEntry.getKey()));
                MoneyValue teamCost = MoneyUtil.fromCopper(costEntry.getValue());
                if (account == null || !account.getMoneyStorage().containsValue(teamCost)) {
                    buyer.displayClientMessage(Component.translatable(
                            "message.lc_ftb_hook.insufficient_funds",
                            MoneyMessageUtil.formatValue(teamCost),
                            account != null ? MoneyMessageUtil.formatBalance(account) : MoneyMessageUtil.formatValue(MoneyValue.empty())
                    ), false);
                    return;
                }
                accountsById.put(costEntry.getKey(), account);
            }
            for (Map.Entry<UUID, IBankAccount> accountEntry : accountsById.entrySet()) {
                accountEntry.getValue().withdrawMoney(MoneyUtil.fromCopper(costPerTeam.get(accountEntry.getKey())));
            }
        } else {
            IBankAccount payerAccount = personalAccount(buyer.getUUID());
            if (payerAccount == null || !payerAccount.getMoneyStorage().containsValue(total)) {
                buyer.displayClientMessage(Component.translatable(
                        "message.lc_ftb_hook.insufficient_funds",
                        MoneyMessageUtil.formatValue(total),
                        payerAccount != null ? MoneyMessageUtil.formatBalance(payerAccount) : MoneyMessageUtil.formatValue(MoneyValue.empty())
                ), false);
                return;
            }
            payerAccount.withdrawMoney(total);
        }

        for (Entry entry : entries) {
            long price = entry.ownership().listingPricePerChunk();
            IBankAccount sellerAccount = entry.ownership().isStateOwned()
                    ? BankAccountHelper.getAccountForTeam(server, entry.team())
                    : personalAccount(entry.ownership().privateOwner());
            if (sellerAccount != null) {
                sellerAccount.depositMoney(MoneyUtil.fromCopper(price));
            }

            ChunkOwnership updated = asCountry
                    ? ChunkOwnership.resetToStateOwned()
                    : new ChunkOwnership(buyer.getUUID(), null, Map.of(), Map.of(), null);
            savedData.setChunkOwnership(entry.team().getTeamId(), entry.key(), updated);
            // A change of ownership resets protection to minimum until the
            // team's next successful upkeep settlement - same as a fresh claim.
            savedData.markChunkUnsettled(entry.team().getTeamId(), entry.key());
        }

        broadcastChunkOwnership(server);
        broadcastUnsettledChunks(server);
        buyer.displayClientMessage(Component.translatable(
                "message.lc_ftb_hook.marketplace_bought", entries.size(), MoneyMessageUtil.formatValue(total)
        ), false);
    }

    /**
     * Single-chunk only (no batch). Refuses if {@code actor} isn't the
     * chunk's private owner, or if {@code value} would be stricter than the
     * owning region's current value for that property (loosen-only).
     */
    public static void setChunkOverride(ServerPlayer actor, String chunkKey, ProtectionProperty property, String value) {
        MinecraftServer server = actor.server;
        ClaimedChunk chunk = resolveChunk(chunkKey);
        if (chunk == null) {
            return;
        }
        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        ChunkOwnership ownership = savedData.getChunkOwnership(team.getTeamId(), chunkKey);
        if (ownership.isStateOwned() || !actor.getUUID().equals(ownership.privateOwner())) {
            return;
        }

        Region region = RegionService.resolveRegion(server, team.getTeamId(), chunkKey);
        String regionValue = region.propertyValue(property);
        if (!ProtectionResolution.isLooserOrEqual(property, value, regionValue)) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_override_too_strict"), false);
            return;
        }

        savedData.setChunkOwnership(team.getTeamId(), chunkKey, ownership.withOverrideProperty(property.id(), value));
        broadcastChunkOwnership(server);
    }

    /**
     * A private owner's custom whitelist/blacklist for one of the 3 privacy-mode
     * properties on their own chunk (see {@link PlayerAccessList}) - always
     * settable regardless of whether the owning Region's baseline currently
     * supports protection for that property. It's simply not CONSULTED while
     * it doesn't (see {@code ChunkTeamDataRegionPrivacyMixin}'s early return
     * when the region is at minimum) - this lets an owner pre-configure a
     * list now so it's already in place the moment an officer raises the
     * region's protection later, instead of forcing them to remember to set
     * it up again afterward.
     */
    public static void setAccessList(ServerPlayer actor, String chunkKey, ProtectionProperty property, boolean whitelist, Set<UUID> players) {
        if (!property.isPrivacyMode()) {
            return;
        }
        MinecraftServer server = actor.server;
        ClaimedChunk chunk = resolveChunk(chunkKey);
        if (chunk == null) {
            return;
        }
        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        ChunkOwnership ownership = savedData.getChunkOwnership(team.getTeamId(), chunkKey);
        if (ownership.isStateOwned() || !actor.getUUID().equals(ownership.privateOwner())) {
            return;
        }

        savedData.setChunkOwnership(team.getTeamId(), chunkKey, ownership.withAccessList(property.id(), new PlayerAccessList(whitelist, players)));
        broadcastChunkOwnership(server);
    }

    /**
     * A private owner's custom label for their own chunk, shown next to the
     * region name in the hover info panel (see {@code ProtectionInfoLines}).
     * Purely cosmetic - blank clears it.
     */
    public static void setLabel(ServerPlayer actor, String chunkKey, String label) {
        MinecraftServer server = actor.server;
        ClaimedChunk chunk = resolveChunk(chunkKey);
        if (chunk == null) {
            return;
        }
        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        ChunkOwnership ownership = savedData.getChunkOwnership(team.getTeamId(), chunkKey);
        if (ownership.isStateOwned() || !actor.getUUID().equals(ownership.privateOwner())) {
            return;
        }

        String trimmed = label.trim();
        if (trimmed.length() > MAX_CHUNK_LABEL_LENGTH) {
            trimmed = trimmed.substring(0, MAX_CHUNK_LABEL_LENGTH);
        }
        savedData.setChunkOwnership(team.getTeamId(), chunkKey, ownership.withLabel(trimmed));
        broadcastChunkOwnership(server);
    }

    /**
     * A team officer/owner force-buys back one or more privately-owned
     * chunks for a set compensation, paid per chunk to each chunk's own
     * private owner. State-owned chunks in the selection are simply ignored
     * (not an error) - only the actor's own team's chunks are eligible.
     */
    public static void expropriate(ServerPlayer actor, List<String> chunkKeys, long compensationPerChunk) {
        if (chunkKeys.isEmpty() || compensationPerChunk < 0) {
            return;
        }
        MinecraftServer server = actor.server;
        FtbHookSavedData savedData = FtbHookSavedData.get(server);

        record Entry(String key, ChunkOwnership ownership) {
        }
        Team actorTeam = null;
        List<Entry> toExpropriate = new java.util.ArrayList<>();
        for (String chunkKey : chunkKeys) {
            ClaimedChunk chunk = resolveChunk(chunkKey);
            if (chunk == null) {
                continue;
            }
            Team team = chunk.getTeamData().getTeam();
            if (team == null || !team.getRankForPlayer(actor.getUUID()).isOfficerOrBetter()) {
                continue;
            }
            if (actorTeam == null) {
                actorTeam = team;
            } else if (!actorTeam.getTeamId().equals(team.getTeamId())) {
                continue;
            }
            ChunkOwnership ownership = savedData.getChunkOwnership(team.getTeamId(), chunkKey);
            if (ownership.isStateOwned()) {
                continue;
            }
            toExpropriate.add(new Entry(chunkKey, ownership));
        }
        if (toExpropriate.isEmpty() || actorTeam == null) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_nothing_to_expropriate"), false);
            return;
        }

        long totalCost = compensationPerChunk * toExpropriate.size();
        IBankAccount teamAccount = BankAccountHelper.getAccountForTeam(server, actorTeam);
        MoneyValue total = MoneyUtil.fromCopper(totalCost);
        if (totalCost > 0 && (teamAccount == null || !teamAccount.getMoneyStorage().containsValue(total))) {
            actor.displayClientMessage(Component.translatable(
                    "message.lc_ftb_hook.insufficient_funds",
                    MoneyMessageUtil.formatValue(total),
                    teamAccount != null ? MoneyMessageUtil.formatBalance(teamAccount) : MoneyMessageUtil.formatValue(MoneyValue.empty())
            ), false);
            return;
        }
        if (totalCost > 0 && teamAccount != null) {
            teamAccount.withdrawMoney(total);
        }

        for (Entry entry : toExpropriate) {
            if (compensationPerChunk > 0) {
                IBankAccount ownerAccount = personalAccount(entry.ownership().privateOwner());
                if (ownerAccount != null) {
                    ownerAccount.depositMoney(MoneyUtil.fromCopper(compensationPerChunk));
                }
            }
            savedData.setChunkOwnership(actorTeam.getTeamId(), entry.key(), ChunkOwnership.resetToStateOwned());
        }

        broadcastChunkOwnership(server);
        actor.displayClientMessage(Component.translatable(
                "message.lc_ftb_hook.marketplace_expropriated", toExpropriate.size(), MoneyMessageUtil.formatValue(total)
        ), false);
    }

    private static IBankAccount personalAccount(UUID playerId) {
        return PlayerBankReference.of(playerId).get();
    }

    private static ClaimedChunk resolveChunk(String chunkKey) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return null;
        }
        return FTBChunksAPI.api().getManager().getChunk(ChunkPosKey.toChunkDimPos(chunkKey));
    }

    public static SyncChunkOwnershipPayload createOwnershipPayload(MinecraftServer server) {
        return new SyncChunkOwnershipPayload(FtbHookSavedData.get(server).getAllChunkOwnership());
    }

    public static void broadcastChunkOwnership(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(createOwnershipPayload(server));
    }

    /**
     * Called wherever a chunk's freshly-claimed state changes (fresh claim, or
     * upkeep settlement clearing it) - drives the client's "not sellable yet"
     * marketplace gating exclusively. Deliberately sources from
     * {@code getAllFreshlyClaimedChunks}, not {@code getAllUnsettledChunks} -
     * a freshly-bought chunk is also "unsettled" (protection reset), but must
     * stay immediately resellable, so it's excluded here.
     */
    public static void broadcastUnsettledChunks(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(new dev.malik.lcftbhook.network.SyncUnsettledChunksPayload(FtbHookSavedData.get(server).getAllFreshlyClaimedChunks()));
    }

    public static void syncOwnershipToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createOwnershipPayload(player.server));
    }

    public static void syncUnsettledChunksToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new dev.malik.lcftbhook.network.SyncUnsettledChunksPayload(
                FtbHookSavedData.get(player.server).getAllFreshlyClaimedChunks()));
    }
}
