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
            data.teamLinks.put(teamId, new TeamLinkEntry(teamId, lcTeamId, legacyAccount, locked, pending));
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

        return new TeamPendingState(pendingProperties, pendingLoads, pendingUnloads);
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
    }

    public TeamLinkEntry getOrCreateLink(UUID ftbTeamId) {
        return teamLinks.computeIfAbsent(ftbTeamId, id -> {
            setDirty();
            return new TeamLinkEntry(id, -1L, null, false, new TeamPendingState());
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
            teamLinks.put(teamId, new TeamLinkEntry(teamId, -1L, null, true, new TeamPendingState()));
            setDirty();
        }
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

    public record TeamLinkEntry(
            UUID ftbTeamId,
            long lcTeamId,
            @Nullable BankAccount legacyAccount,
            boolean protectionLocked,
            TeamPendingState pendingState
    ) {
        TeamLinkEntry withLcTeamId(long id) {
            return new TeamLinkEntry(ftbTeamId, id, legacyAccount, protectionLocked, pendingState);
        }

        TeamLinkEntry withLegacyAccount(@Nullable BankAccount account) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, account, protectionLocked, pendingState);
        }

        TeamLinkEntry withProtectionLocked(boolean locked) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, locked, pendingState);
        }

        TeamLinkEntry withPendingState(TeamPendingState pending) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pending);
        }
    }
}
