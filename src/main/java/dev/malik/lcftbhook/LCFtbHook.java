package dev.malik.lcftbhook;

import com.mojang.logging.LogUtils;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.command.ClearWarsCommand;
import dev.malik.lcftbhook.command.SeedTestTeamsCommand;
import dev.malik.lcftbhook.command.UpkeepDetailsCommand;
import dev.malik.lcftbhook.command.UpkeepPriorityCommand;
import dev.malik.lcftbhook.handler.ChunkClaimHandler;
import dev.malik.lcftbhook.handler.ForceLoadHandler;
import dev.malik.lcftbhook.handler.TaxCollectorPlacementHandler;
import dev.malik.lcftbhook.handler.TeamLifecycleHandler;
import dev.malik.lcftbhook.handler.TeamPropertyHandler;
import dev.malik.lcftbhook.network.RequestClaimPricesPayload;
import dev.malik.lcftbhook.network.RequestLandChunksPayload;
import dev.malik.lcftbhook.network.RequestPendingStatePayload;
import dev.malik.lcftbhook.network.SyncClaimPricesPayload;
import dev.malik.lcftbhook.network.SyncLandChunksPayload;
import dev.malik.lcftbhook.network.SyncPendingStatePayload;
import dev.malik.lcftbhook.network.RequestWarStatePayload;
import dev.malik.lcftbhook.network.SyncWarStatePayload;
import dev.malik.lcftbhook.network.ToggleChunkTypeBatchPayload;
import dev.malik.lcftbhook.network.ToggleChunkTypePayload;
import dev.malik.lcftbhook.network.ToggleWarPayload;
import dev.malik.lcftbhook.client.ClientPendingRefreshHandler;
import dev.malik.lcftbhook.service.UpkeepService;
import dev.malik.lcftbhook.teams.LandProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(LCFtbHook.MOD_ID)
public class LCFtbHook {
    public static final String MOD_ID = "lc_ftb_hook";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LCFtbHook(IEventBus modEventBus, ModContainer modContainer) {
        ModCompatibility.validateOrThrow();

        modContainer.registerConfig(ModConfig.Type.SERVER, LCFtbHookConfig.SERVER_SPEC);

        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(UpkeepDetailsCommand::register);
        NeoForge.EVENT_BUS.addListener(UpkeepPriorityCommand::register);
        NeoForge.EVENT_BUS.addListener(ClearWarsCommand::register);
        NeoForge.EVENT_BUS.addListener(SeedTestTeamsCommand::register);

        LandProperties.register();

        NeoForge.EVENT_BUS.register(new UpkeepService());
        NeoForge.EVENT_BUS.register(new TeamLifecycleHandler());
        NeoForge.EVENT_BUS.register(new TaxCollectorPlacementHandler());

        new ChunkClaimHandler();
        new TeamPropertyHandler();
        new ForceLoadHandler();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            new ClientPendingRefreshHandler();
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToClient(
                SyncClaimPricesPayload.TYPE,
                SyncClaimPricesPayload.STREAM_CODEC,
                SyncClaimPricesPayload::handleClient
        );
        registrar.playToClient(
                SyncPendingStatePayload.TYPE,
                SyncPendingStatePayload.STREAM_CODEC,
                SyncPendingStatePayload::handleClient
        );
        registrar.playToClient(
                SyncLandChunksPayload.TYPE,
                SyncLandChunksPayload.STREAM_CODEC,
                SyncLandChunksPayload::handleClient
        );
        registrar.playToClient(
                SyncWarStatePayload.TYPE,
                SyncWarStatePayload.STREAM_CODEC,
                SyncWarStatePayload::handleClient
        );
        registrar.playToServer(
                RequestClaimPricesPayload.TYPE,
                RequestClaimPricesPayload.STREAM_CODEC,
                RequestClaimPricesPayload::handleServer
        );
        registrar.playToServer(
                RequestPendingStatePayload.TYPE,
                RequestPendingStatePayload.STREAM_CODEC,
                RequestPendingStatePayload::handleServer
        );
        registrar.playToServer(
                RequestLandChunksPayload.TYPE,
                RequestLandChunksPayload.STREAM_CODEC,
                RequestLandChunksPayload::handleServer
        );
        registrar.playToServer(
                ToggleChunkTypePayload.TYPE,
                ToggleChunkTypePayload.STREAM_CODEC,
                ToggleChunkTypePayload::handleServer
        );
        registrar.playToServer(
                ToggleChunkTypeBatchPayload.TYPE,
                ToggleChunkTypeBatchPayload.STREAM_CODEC,
                ToggleChunkTypeBatchPayload::handleServer
        );
        registrar.playToServer(
                RequestWarStatePayload.TYPE,
                RequestWarStatePayload.STREAM_CODEC,
                RequestWarStatePayload::handleServer
        );
        registrar.playToServer(
                ToggleWarPayload.TYPE,
                ToggleWarPayload.STREAM_CODEC,
                ToggleWarPayload::handleServer
        );
    }
}
