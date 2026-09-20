package dev.malik.lcftbhook.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.malik.lcftbhook.network.RequestPlayerProfilePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side cache of resolved {@link GameProfile}s (name + skin texture
 * property) for players the client's own tab-list-backed {@code PlayerInfo}
 * doesn't know about (offline, or never seen online this session) - see
 * {@code PlayerProfileResolver}'s javadoc for why this needs a server round
 * trip instead of being resolvable purely client-side.
 */
public final class ClientPlayerProfiles {
    private static final Map<UUID, GameProfile> PROFILES = new HashMap<>();
    private static final Set<UUID> REQUESTED = new HashSet<>();

    private ClientPlayerProfiles() {
    }

    @Nullable
    public static GameProfile get(UUID playerId) {
        return PROFILES.get(playerId);
    }

    /** Sends a request at most once per player UUID per client session - callers query this every frame while hovering, so this must not spam the server. */
    public static void request(UUID playerId) {
        if (REQUESTED.add(playerId)) {
            PacketDistributor.sendToServer(new RequestPlayerProfilePayload(playerId));
        }
    }

    public static void update(UUID playerId, String name, String textureValue, String textureSignature) {
        GameProfile profile = new GameProfile(playerId, name);
        if (!textureValue.isEmpty()) {
            profile.getProperties().put("textures", new Property("textures", textureValue, textureSignature.isEmpty() ? null : textureSignature));
        }
        PROFILES.put(playerId, profile);
    }
}
