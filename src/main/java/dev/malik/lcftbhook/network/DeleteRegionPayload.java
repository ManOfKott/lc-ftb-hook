package dev.malik.lcftbhook.network;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record DeleteRegionPayload(UUID regionId) implements CustomPacketPayload {
    public static final Type<DeleteRegionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "delete_region"));
    public static final StreamCodec<FriendlyByteBuf, DeleteRegionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUUID(payload.regionId),
            buffer -> new DeleteRegionPayload(buffer.readUUID())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(DeleteRegionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!FTBTeamsAPI.api().isManagerLoaded()) {
                return;
            }
            Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
            if (team == null || !team.getRankForPlayer(player.getUUID()).isOfficerOrBetter()) {
                return;
            }
            RegionService.deleteRegion(player.server, team, payload.regionId);
        });
    }
}
