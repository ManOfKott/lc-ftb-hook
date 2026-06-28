package dev.malik.lcftbhook.data;

import dev.malik.lcftbhook.LCFtbHook;
import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FtbHookSavedData extends SavedData {
    private static final String DATA_NAME = LCFtbHook.MOD_ID + "_team_accounts";

    private final Map<UUID, TeamLinkEntry> teamLinks = new HashMap<>();

    public static FtbHookSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(FtbHookSavedData::new, FtbHookSavedData::load), DATA_NAME);
    }

    private static FtbHookSavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        FtbHookSavedData data = new FtbHookSavedData();
        ListTag list = tag.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID teamId = entryTag.getUUID("TeamId");
            long lcTeamId = entryTag.contains("LcTeamId", Tag.TAG_LONG) ? entryTag.getLong("LcTeamId") : -1L;
            BankAccount legacyAccount = null;
            if (entryTag.contains("Account", Tag.TAG_COMPOUND)) {
                legacyAccount = new BankAccount(() -> data.setDirty(), entryTag.getCompound("Account"), lookup);
            }
            boolean locked = entryTag.getBoolean("ProtectionLocked");
            TeamPendingState pending = loadPendingState(entryTag);
            Set<String> landChunks = new HashSet<>();
            if (entryTag.contains("LandChunks", Tag.TAG_LIST)) {
                ListTag landList = entryTag.getList("LandChunks", Tag.TAG_STRING);
                for (int j = 0; j < landList.size(); j++) {
                    landChunks.add(landList.getString(j));
                }
            }
            Set<UUID> warTargets = new HashSet<>();
            if (entryTag.contains("WarTargets", Tag.TAG_LIST)) {
                ListTag warList = entryTag.getList("WarTargets", Tag.TAG_INT_ARRAY);
                for (int j = 0; j < warList.size(); j++) {
                    warTargets.add(net.minecraft.nbt.NbtUtils.loadUUID(warList.get(j)));
                }
            }
            data.teamLinks.put(teamId, new TeamLinkEntry(teamId, lcTeamId, legacyAccount, locked, pending, landChunks, warTargets));
        }
        return data;
    }

    private static TeamPendingState loadPendingState(CompoundTag entryTag) {
        Map<String, String> pendingProperties = new HashMap<>();
        if (entryTag.contains("PendingProperties", Tag.TAG_COMPOUND)) {
            CompoundTag propertiesTag = entryTag.getCompound("PendingProperties");
            for (String key : propertiesTag.getAllKeys()) {
                pendingProperties.put(key, propertiesTag.getString(key));
            }
        }

        Set<String> pendingLoads = new HashSet<>();
        if (entryTag.contains("PendingForceLoads", Tag.TAG_LIST)) {
            ListTag loads = entryTag.getList("PendingForceLoads", Tag.TAG_STRING);
            for (int i = 0; i < loads.size(); i++) {
                pendingLoads.add(loads.getString(i));
            }
        }

        Set<String> pendingUnloads = new HashSet<>();
        if (entryTag.contains("PendingForceUnloads", Tag.TAG_LIST)) {
            ListTag unloads = entryTag.getList("PendingForceUnloads", Tag.TAG_STRING);
            for (int i = 0; i < unloads.size(); i++) {
                pendingUnloads.add(unloads.getString(i));
            }
        }

        Set<UUID> pendingWarDeclares = new HashSet<>();
        if (entryTag.contains("PendingWarDeclares", Tag.TAG_LIST)) {
            ListTag declares = entryTag.getList("PendingWarDeclares", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < declares.size(); i++) {
                pendingWarDeclares.add(net.minecraft.nbt.NbtUtils.loadUUID(declares.get(i)));
            }
        }

        Set<UUID> pendingWarEnds = new HashSet<>();
        if (entryTag.contains("PendingWarEnds", Tag.TAG_LIST)) {
            ListTag ends = entryTag.getList("PendingWarEnds", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < ends.size(); i++) {
                pendingWarEnds.add(net.minecraft.nbt.NbtUtils.loadUUID(ends.get(i)));
            }
        }

        Set<String> pendingLandChunks = new HashSet<>();
        if (entryTag.contains("PendingLandChunks", Tag.TAG_LIST)) {
            ListTag landPending = entryTag.getList("PendingLandChunks", Tag.TAG_STRING);
            for (int i = 0; i < landPending.size(); i++) {
                pendingLandChunks.add(landPending.getString(i));
            }
        }

        Set<String> pendingBuildChunks = new HashSet<>();
        if (entryTag.contains("PendingBuildChunks", Tag.TAG_LIST)) {
            ListTag buildPending = entryTag.getList("PendingBuildChunks", Tag.TAG_STRING);
            for (int i = 0; i < buildPending.size(); i++) {
                pendingBuildChunks.add(buildPending.getString(i));
            }
        }

        if (entryTag.contains("AutoSuspendedWars", Tag.TAG_LIST)) {
            ListTag suspendedWars = entryTag.getList("AutoSuspendedWars", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < suspendedWars.size(); i++) {
                pendingWarDeclares.add(net.minecraft.nbt.NbtUtils.loadUUID(suspendedWars.get(i)));
            }
        }

        return new TeamPendingState(
                pendingProperties,
                pendingLoads,
                pendingUnloads,
                pendingLandChunks,
                pendingBuildChunks,
                pendingWarDeclares,
                pendingWarEnds
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (TeamLinkEntry entry : teamLinks.values()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("TeamId", entry.ftbTeamId());
            if (entry.lcTeamId() > 0) {
                entryTag.putLong("LcTeamId", entry.lcTeamId());
            }
            if (entry.legacyAccount() != null) {
                entryTag.put("Account", entry.legacyAccount().save(lookup));
            }
            entryTag.putBoolean("ProtectionLocked", entry.protectionLocked());
            savePendingState(entryTag, entry.pendingState());
            if (!entry.landChunks().isEmpty()) {
                ListTag landList = new ListTag();
                entry.landChunks().forEach(key -> landList.add(StringTag.valueOf(key)));
                entryTag.put("LandChunks", landList);
            }
            if (!entry.warTargets().isEmpty()) {
                ListTag warList = new ListTag();
                entry.warTargets().forEach(id -> warList.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
                entryTag.put("WarTargets", warList);
            }
            list.add(entryTag);
        }
        tag.put("Teams", list);
        return tag;
    }

    private static void savePendingState(CompoundTag entryTag, TeamPendingState pendingState) {
        if (!pendingState.pendingProperties().isEmpty()) {
            CompoundTag propertiesTag = new CompoundTag();
            pendingState.pendingProperties().forEach(propertiesTag::putString);
            entryTag.put("PendingProperties", propertiesTag);
        }
        if (!pendingState.pendingForceLoads().isEmpty()) {
            ListTag loads = new ListTag();
            pendingState.pendingForceLoads().forEach(key -> loads.add(StringTag.valueOf(key)));
            entryTag.put("PendingForceLoads", loads);
        }
        if (!pendingState.pendingForceUnloads().isEmpty()) {
            ListTag unloads = new ListTag();
            pendingState.pendingForceUnloads().forEach(key -> unloads.add(StringTag.valueOf(key)));
            entryTag.put("PendingForceUnloads", unloads);
        }
        if (!pendingState.pendingLandChunks().isEmpty()) {
            ListTag landPending = new ListTag();
            pendingState.pendingLandChunks().forEach(key -> landPending.add(StringTag.valueOf(key)));
            entryTag.put("PendingLandChunks", landPending);
        }
        if (!pendingState.pendingBuildChunks().isEmpty()) {
            ListTag buildPending = new ListTag();
            pendingState.pendingBuildChunks().forEach(key -> buildPending.add(StringTag.valueOf(key)));
            entryTag.put("PendingBuildChunks", buildPending);
        }
        if (!pendingState.pendingWarDeclares().isEmpty()) {
            ListTag declares = new ListTag();
            pendingState.pendingWarDeclares().forEach(id -> declares.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
            entryTag.put("PendingWarDeclares", declares);
        }
        if (!pendingState.pendingWarEnds().isEmpty()) {
            ListTag ends = new ListTag();
            pendingState.pendingWarEnds().forEach(id -> ends.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
            entryTag.put("PendingWarEnds", ends);
        }
    }

    public TeamLinkEntry getOrCreateLink(UUID ftbTeamId) {
        return teamLinks.computeIfAbsent(ftbTeamId, id -> {
            setDirty();
            return new TeamLinkEntry(id, -1L, null, false, new TeamPendingState(), Set.of(), Set.of());
        });
    }

    @Nullable
    public TeamLinkEntry get(UUID ftbTeamId) {
        return teamLinks.get(ftbTeamId);
    }

    @Nullable
    public TeamLinkEntry findByLcTeamId(long lcTeamId) {
        if (lcTeamId <= 0) {
            return null;
        }
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() == lcTeamId) {
                return entry;
            }
        }
        return null;
    }

    public java.util.Collection<TeamLinkEntry> getAllLinks() {
        return java.util.List.copyOf(teamLinks.values());
    }

    @Nullable
    public TeamLinkEntry removeLink(UUID ftbTeamId) {
        TeamLinkEntry removed = teamLinks.remove(ftbTeamId);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /** Clears the LC bank link only; keeps land chunks, pending state, and protection lock. */
    public boolean clearLcTeamLink(UUID ftbTeamId) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        if (entry == null || (entry.lcTeamId() <= 0 && entry.legacyAccount() == null)) {
            return false;
        }
        teamLinks.put(ftbTeamId, entry.withLcTeamId(-1L).withLegacyAccount(null));
        setDirty();
        return true;
    }

    public void removeLinkByLcTeamId(long lcTeamId) {
        TeamLinkEntry entry = findByLcTeamId(lcTeamId);
        if (entry != null) {
            removeLink(entry.ftbTeamId());
        }
    }

    public TeamPendingState getPendingState(UUID ftbTeamId) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        return entry == null ? new TeamPendingState() : entry.pendingState();
    }

    public void setPendingState(UUID ftbTeamId, TeamPendingState pendingState) {
        TeamLinkEntry entry = getOrCreateLink(ftbTeamId);
        teamLinks.put(ftbTeamId, entry.withPendingState(pendingState));
        setDirty();
    }

    public void setLcTeamId(UUID ftbTeamId, long lcTeamId) {
        TeamLinkEntry entry = getOrCreateLink(ftbTeamId);
        if (entry.lcTeamId() != lcTeamId) {
            teamLinks.put(ftbTeamId, entry.withLcTeamId(lcTeamId));
            setDirty();
        }
    }

    public void clearLegacyAccount(UUID ftbTeamId) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        if (entry != null && entry.legacyAccount() != null) {
            teamLinks.put(ftbTeamId, entry.withLegacyAccount(null));
            setDirty();
        }
    }

    public void setProtectionLocked(UUID teamId, boolean locked) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry != null && entry.protectionLocked() != locked) {
            teamLinks.put(teamId, entry.withProtectionLocked(locked));
            setDirty();
        } else if (entry == null && locked) {
            teamLinks.put(teamId, new TeamLinkEntry(teamId, -1L, null, true, new TeamPendingState(), Set.of(), Set.of()));
            setDirty();
        }
    }

    public Set<String> getLandChunks(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? Set.of() : entry.landChunks();
    }

    public boolean isLandChunk(UUID teamId, String chunkKey) {
        return getLandChunks(teamId).contains(chunkKey);
    }

    /**
     * Marks or unmarks a chunk as land chunk. Returns true if the stored
     * state actually changed.
     */
    public boolean setLandChunk(UUID teamId, String chunkKey, boolean land) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
        if (entry.landChunks().contains(chunkKey) == land) {
            return false;
        }
        Set<String> updated = new HashSet<>(entry.landChunks());
        if (land) {
            updated.add(chunkKey);
        } else {
            updated.remove(chunkKey);
        }
        teamLinks.put(teamId, entry.withLandChunks(updated));
        setDirty();
        return true;
    }

    /** Removes a chunk from every team's land set (e.g. after unclaiming). */
    public boolean clearLandChunk(String chunkKey) {
        boolean changed = false;
        for (TeamLinkEntry entry : java.util.List.copyOf(teamLinks.values())) {
            if (entry.landChunks().contains(chunkKey)) {
                Set<String> updated = new HashSet<>(entry.landChunks());
                updated.remove(chunkKey);
                teamLinks.put(entry.ftbTeamId(), entry.withLandChunks(updated));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public Set<String> getAllLandChunks() {
        Set<String> all = new HashSet<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            all.addAll(entry.landChunks());
        }
        return all;
    }

    public boolean isProtectionLocked(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry != null && entry.protectionLocked();
    }

    public boolean isManagedLcTeam(long lcTeamId) {
        if (lcTeamId <= 0) {
            return false;
        }
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() == lcTeamId) {
                return true;
            }
        }
        return false;
    }

    public Set<Long> getLinkedLcTeamIds() {
        Set<Long> linkedIds = new HashSet<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() > 0) {
                linkedIds.add(entry.lcTeamId());
            }
        }
        return linkedIds;
    }

    public Set<UUID> getWarTargets(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? Set.of() : entry.warTargets();
    }

    public boolean isAtWarWith(UUID declarerTeamId, UUID targetTeamId) {
        return getWarTargets(declarerTeamId).contains(targetTeamId);
    }

    public boolean setWarTarget(UUID declarerTeamId, UUID targetTeamId, boolean atWar) {
        TeamLinkEntry entry = getOrCreateLink(declarerTeamId);
        Set<UUID> updated = new HashSet<>(entry.warTargets());
        if (atWar) {
            if (!updated.add(targetTeamId)) {
                return false;
            }
        } else if (!updated.remove(targetTeamId)) {
            return false;
        }
        teamLinks.put(declarerTeamId, entry.withWarTargets(Set.copyOf(updated)));
        setDirty();
        return true;
    }

    public Set<UUID> collectWarPartnerIds(UUID teamId) {
        Set<UUID> partners = new HashSet<>();
        partners.addAll(getWarTargets(teamId));
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.warTargets().contains(teamId)) {
                partners.add(entry.ftbTeamId());
            }
        }
        partners.remove(teamId);
        return partners;
    }

    public void clearWarReferences(UUID teamId) {
        boolean changed = false;
        TeamLinkEntry ownEntry = teamLinks.get(teamId);
        if (ownEntry != null && !ownEntry.warTargets().isEmpty()) {
            teamLinks.put(teamId, ownEntry.withWarTargets(Set.of()));
            changed = true;
        }
        for (TeamLinkEntry entry : java.util.List.copyOf(teamLinks.values())) {
            UUID entryTeamId = entry.ftbTeamId();
            TeamPendingState pending = entry.pendingState();
            TeamPendingState cleaned = pending.withoutWarReferences(teamId);
            if (cleaned != pending) {
                teamLinks.put(entryTeamId, entry.withPendingState(cleaned));
                changed = true;
            }
            if (entry.warTargets().contains(teamId)) {
                Set<UUID> updated = new HashSet<>(entry.warTargets());
                updated.remove(teamId);
                teamLinks.put(entryTeamId, teamLinks.get(entryTeamId).withWarTargets(Set.copyOf(updated)));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public int countIncomingWars(UUID targetTeamId) {
        int count = 0;
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.warTargets().contains(targetTeamId)) {
                count++;
            }
        }
        return count;
    }

    public record TeamLinkEntry(
            UUID ftbTeamId,
            long lcTeamId,
            @Nullable BankAccount legacyAccount,
            boolean protectionLocked,
            TeamPendingState pendingState,
            Set<String> landChunks,
            Set<UUID> warTargets
    ) {
        TeamLinkEntry withLcTeamId(long id) {
            return new TeamLinkEntry(ftbTeamId, id, legacyAccount, protectionLocked, pendingState, landChunks, warTargets);
        }

        TeamLinkEntry withLegacyAccount(@Nullable BankAccount account) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, account, protectionLocked, pendingState, landChunks, warTargets);
        }

        TeamLinkEntry withProtectionLocked(boolean locked) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, locked, pendingState, landChunks, warTargets);
        }

        TeamLinkEntry withPendingState(TeamPendingState pending) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pending, landChunks, warTargets);
        }

        TeamLinkEntry withLandChunks(Set<String> chunks) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, chunks, warTargets);
        }

        TeamLinkEntry withWarTargets(Set<UUID> targets) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, landChunks, targets);
        }
    }
}
