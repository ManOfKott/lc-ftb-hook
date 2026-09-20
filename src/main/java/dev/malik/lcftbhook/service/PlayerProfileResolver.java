package dev.malik.lcftbhook.service;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.malik.lcftbhook.network.SyncPlayerProfilePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a player's real name + skin from just their UUID, for the
 * marketplace hover info panel ({@code ProtectionInfoLines}/{@code
 * ProtectionInfoPanelRenderer}) showing a privately-owned chunk's owner -
 * needed because the client's own {@code PlayerInfo} map (populated from
 * {@code ClientboundPlayerInfoUpdatePacket}) only ever knows about players
 * who have been online THIS session; an offline owner (or one on a
 * different team the viewer has never actually seen online) resolves to
 * nothing there, regardless of team visibility - this isn't a team
 * filtering issue at all.
 * <p>
 * Two-step server-side resolution: {@code server.getProfileCache()} (backed
 * by usercache.json) gives the correct NAME for anyone who has ever joined
 * THIS server, entirely independent of current online status. That name is
 * then fed into vanilla's own {@link SkullBlockEntity#fetchGameProfile(String)}
 * - the exact mechanism player-head items use to show a real skin for an
 * arbitrary username - which does a genuine Mojang lookup and works even on
 * an offline-mode dev server, since it's name-based, not tied to this
 * server's (possibly fake, offline-mode) UUID.
 */
public final class PlayerProfileResolver {
    private static final Map<UUID, GameProfile> CACHE = new ConcurrentHashMap<>();

    private PlayerProfileResolver() {
    }

    public static void resolveAndSync(MinecraftServer server, UUID playerId) {
        GameProfile cached = CACHE.get(playerId);
        if (cached != null) {
            broadcast(server, playerId, cached);
            return;
        }
        if (server.getProfileCache() == null) {
            return;
        }
        server.getProfileCache().get(playerId).ifPresent(localProfile -> {
            String name = localProfile.getName();
            if (name == null || name.isBlank()) {
                return;
            }
            SkullBlockEntity.fetchGameProfile(name).thenAcceptAsync(result -> {
                GameProfile resolved = result.orElse(localProfile);
                CACHE.put(playerId, resolved);
                broadcast(server, playerId, resolved);
            }, server);
        });
    }

    private static void broadcast(MinecraftServer server, UUID playerId, GameProfile profile) {
        Property textures = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        PacketDistributor.sendToAllPlayers(new SyncPlayerProfilePayload(
                playerId,
                profile.getName(),
                textures != null ? textures.value() : "",
                textures != null && textures.hasSignature() ? textures.signature() : ""
        ));
    }
}
