package dev.malik.lcftbhook.data;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TeamPendingState {
    private final Map<String, String> pendingProperties;
    private final Set<String> pendingForceLoads;
    private final Set<String> pendingForceUnloads;

    public TeamPendingState() {
        this(new HashMap<>(), new HashSet<>(), new HashSet<>());
    }

    public TeamPendingState(
            Map<String, String> pendingProperties,
            Set<String> pendingForceLoads,
            Set<String> pendingForceUnloads
    ) {
        this.pendingProperties = new HashMap<>(pendingProperties);
        this.pendingForceLoads = new HashSet<>(pendingForceLoads);
        this.pendingForceUnloads = new HashSet<>(pendingForceUnloads);
    }

    public Map<String, String> pendingProperties() {
        return Collections.unmodifiableMap(pendingProperties);
    }

    public Set<String> pendingForceLoads() {
        return Collections.unmodifiableSet(pendingForceLoads);
    }

    public Set<String> pendingForceUnloads() {
        return Collections.unmodifiableSet(pendingForceUnloads);
    }

    public boolean isEmpty() {
        return pendingProperties.isEmpty() && pendingForceLoads.isEmpty() && pendingForceUnloads.isEmpty();
    }

    public boolean hasPendingProperty(String propertyId) {
        return pendingProperties.containsKey(propertyId);
    }

    public boolean isPendingForceLoad(String chunkKey) {
        return pendingForceLoads.contains(chunkKey);
    }

    public boolean isPendingForceUnload(String chunkKey) {
        return pendingForceUnloads.contains(chunkKey);
    }

    public TeamPendingState withPendingProperty(String propertyId, String value) {
        TeamPendingState copy = copy();
        copy.pendingProperties.put(propertyId, value);
        return copy;
    }

    public TeamPendingState withoutPendingProperty(String propertyId) {
        if (!pendingProperties.containsKey(propertyId)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingProperties.remove(propertyId);
        return copy;
    }

    public TeamPendingState withPendingForceLoad(String chunkKey) {
        TeamPendingState copy = copy();
        copy.pendingForceLoads.add(chunkKey);
        copy.pendingForceUnloads.remove(chunkKey);
        return copy;
    }

    public TeamPendingState withoutPendingForceLoad(String chunkKey) {
        if (!pendingForceLoads.contains(chunkKey)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingForceLoads.remove(chunkKey);
        return copy;
    }

    public TeamPendingState withPendingForceUnload(String chunkKey) {
        TeamPendingState copy = copy();
        copy.pendingForceUnloads.add(chunkKey);
        copy.pendingForceLoads.remove(chunkKey);
        return copy;
    }

    public TeamPendingState withoutPendingForceUnload(String chunkKey) {
        if (!pendingForceUnloads.contains(chunkKey)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingForceUnloads.remove(chunkKey);
        return copy;
    }

    public TeamPendingState cleared() {
        return new TeamPendingState();
    }

    public TeamPendingState copy() {
        return new TeamPendingState(pendingProperties, pendingForceLoads, pendingForceUnloads);
    }
}
