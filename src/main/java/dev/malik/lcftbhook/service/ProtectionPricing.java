package dev.malik.lcftbhook.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyCollection;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ProtectionPricing {
    public static final Set<TeamProperty<?>> PROTECTION_PROPERTIES = Set.of(
            FTBChunksProperties.ALLOW_MOB_GRIEFING,
            FTBChunksProperties.ALLOW_EXPLOSIONS,
            FTBChunksProperties.ALLOW_PVP,
            FTBChunksProperties.BLOCK_INTERACT_MODE,
            FTBChunksProperties.BLOCK_EDIT_MODE,
            FTBChunksProperties.ENTITY_INTERACT_MODE
    );

    private ProtectionPricing() {
    }

    public static long calculateBasePrice(Team team) {
        return calculateBasePrice(team, Map.of());
    }

    public static long calculateBasePrice(Team team, Map<String, String> pendingProperties) {
        long base = 0L;
        if (!getBooleanProperty(team, FTBChunksProperties.ALLOW_MOB_GRIEFING, pendingProperties)) {
            base += LCFtbHookConfig.SERVER.mobGriefProtectionPrice.get();
        }
        if (!getBooleanProperty(team, FTBChunksProperties.ALLOW_EXPLOSIONS, pendingProperties)) {
            base += LCFtbHookConfig.SERVER.explosionProtectionPrice.get();
        }
        if (!getBooleanProperty(team, FTBChunksProperties.ALLOW_PVP, pendingProperties)) {
            base += LCFtbHookConfig.SERVER.pvpDisablePrice.get();
        }
        if (getPrivacyProperty(team, FTBChunksProperties.BLOCK_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LCFtbHookConfig.SERVER.blockInteractProtectionPrice.get();
        }
        if (getPrivacyProperty(team, FTBChunksProperties.BLOCK_EDIT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LCFtbHookConfig.SERVER.blockEditProtectionPrice.get();
        }
        if (getPrivacyProperty(team, FTBChunksProperties.ENTITY_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LCFtbHookConfig.SERVER.entityInteractProtectionPrice.get();
        }
        return base;
    }

    public static long calculateBasePrice(TeamPropertyCollection properties) {
        return calculateBasePrice(properties, Map.of());
    }

    public static long calculateBasePrice(TeamPropertyCollection properties, Map<String, String> pendingProperties) {
        long base = 0L;
        if (!getBooleanProperty(properties, FTBChunksProperties.ALLOW_MOB_GRIEFING, pendingProperties)) {
            base += LCFtbHookConfig.SERVER.mobGriefProtectionPrice.get();
        }
        if (!getBooleanProperty(properties, FTBChunksProperties.ALLOW_EXPLOSIONS, pendingProperties)) {
            base += LCFtbHookConfig.SERVER.explosionProtectionPrice.get();
        }
        if (!getBooleanProperty(properties, FTBChunksProperties.ALLOW_PVP, pendingProperties)) {
            base += LCFtbHookConfig.SERVER.pvpDisablePrice.get();
        }
        if (getPrivacyProperty(properties, FTBChunksProperties.BLOCK_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LCFtbHookConfig.SERVER.blockInteractProtectionPrice.get();
        }
        if (getPrivacyProperty(properties, FTBChunksProperties.BLOCK_EDIT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LCFtbHookConfig.SERVER.blockEditProtectionPrice.get();
        }
        if (getPrivacyProperty(properties, FTBChunksProperties.ENTITY_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LCFtbHookConfig.SERVER.entityInteractProtectionPrice.get();
        }
        return base;
    }

    private static boolean getBooleanProperty(
            TeamPropertyCollection properties,
            TeamProperty<Boolean> property,
            Map<String, String> pendingProperties
    ) {
        String key = propertyKey(property);
        if (pendingProperties.containsKey(key)) {
            return deserializePropertyValue(property, pendingProperties.get(key), properties.get(property));
        }
        return properties.get(property);
    }

    private static PrivacyMode getPrivacyProperty(
            TeamPropertyCollection properties,
            TeamProperty<PrivacyMode> property,
            Map<String, String> pendingProperties
    ) {
        String key = propertyKey(property);
        if (pendingProperties.containsKey(key)) {
            return deserializePropertyValue(property, pendingProperties.get(key), properties.get(property));
        }
        return properties.get(property);
    }

    public static MoneyValue calculateTotalUpkeepCost(Team team, int chunkCount, int forceLoadCount) {
        return calculateTotalUpkeepCost(team, Map.of(), chunkCount, forceLoadCount);
    }

    public static MoneyValue calculateTotalUpkeepCost(
            Team team,
            Map<String, String> pendingProperties,
            int chunkCount,
            int forceLoadCount
    ) {
        long protectionCopper = 0L;
        long base = calculateBasePrice(team, pendingProperties);
        int billableChunks = FreeChunkAllowance.billableChunkCount(chunkCount);
        if (base > 0 && billableChunks > 0) {
            protectionCopper = base * billableChunks;
        }

        long forceLoadCopper = 0L;
        long forceLoadPrice = LCFtbHookConfig.SERVER.forceLoadUpkeepPrice.get();
        if (forceLoadPrice > 0 && forceLoadCount > 0) {
            forceLoadCopper = forceLoadPrice * forceLoadCount;
        }

        return MoneyUtil.fromCopper(protectionCopper + forceLoadCopper);
    }

    public static MoneyValue calculateTotalUpkeepCost(Team team, TeamPendingState pendingState) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        int forceLoadCount = countEffectiveForceLoads(chunkData, pendingState);
        return calculateTotalUpkeepCost(
                team,
                pendingState.pendingProperties(),
                chunkData.getClaimedChunks().size(),
                forceLoadCount
        );
    }

    public static int countEffectiveForceLoads(ChunkTeamData chunkData, TeamPendingState pendingState) {
        int count = chunkData.getForceLoadedChunks().size();
        count += pendingState.pendingForceLoads().size();
        count -= pendingState.pendingForceUnloads().size();
        return Math.max(count, 0);
    }

    public static boolean isProtectionProperty(TeamProperty<?> property) {
        return PROTECTION_PROPERTIES.contains(property);
    }

    public static String propertyKey(TeamProperty<?> property) {
        return property.getId().getPath();
    }

    @SuppressWarnings("unchecked")
    public static String serializePropertyValue(TeamProperty<?> property, Object value) {
        return ((TeamProperty<Object>) property).toString(value);
    }

    public static <T> T deserializePropertyValue(TeamProperty<T> property, String value, T fallback) {
        return property.fromString(value).orElse(fallback);
    }

    public static Map<String, String> withPendingProperty(
            Map<String, String> pendingProperties,
            TeamProperty<?> property,
            Object value
    ) {
        Map<String, String> copy = new HashMap<>(pendingProperties);
        copy.put(propertyKey(property), serializePropertyValue(property, value));
        return copy;
    }

    public static void applyMinimumProtections(net.minecraft.server.MinecraftServer server, Team team) {
        setAndSync(server, team, FTBChunksProperties.ALLOW_MOB_GRIEFING, true);
        setAndSync(server, team, FTBChunksProperties.ALLOW_EXPLOSIONS, true);
        setAndSync(server, team, FTBChunksProperties.ALLOW_PVP, true);
        setAndSync(server, team, FTBChunksProperties.BLOCK_INTERACT_MODE, PrivacyMode.PUBLIC);
        setAndSync(server, team, FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PUBLIC);
        setAndSync(server, team, FTBChunksProperties.ENTITY_INTERACT_MODE, PrivacyMode.PUBLIC);
        setAndSync(server, team, FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
    }

    private static <T> void setAndSync(net.minecraft.server.MinecraftServer server, Team team, TeamProperty<T> property, T value) {
        team.setProperty(property, value);
        team.syncOnePropertyToAll(server, property, value);
    }

    private static boolean getBooleanProperty(
            Team team,
            TeamProperty<Boolean> property,
            Map<String, String> pendingProperties
    ) {
        String key = propertyKey(property);
        if (pendingProperties.containsKey(key)) {
            return deserializePropertyValue(property, pendingProperties.get(key), team.getProperty(property));
        }
        return team.getProperty(property);
    }

    private static PrivacyMode getPrivacyProperty(
            Team team,
            PrivacyProperty property,
            Map<String, String> pendingProperties
    ) {
        String key = propertyKey(property);
        if (pendingProperties.containsKey(key)) {
            return deserializePropertyValue(property, pendingProperties.get(key), team.getProperty(property));
        }
        return team.getProperty(property);
    }
}
