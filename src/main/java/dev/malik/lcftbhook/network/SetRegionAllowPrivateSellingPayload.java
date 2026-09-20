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

public record SetRegionAllowPrivateSellingPayload(UUID regionId, boolean allow) implements CustomPacketPayload {
    public static final Type<SetRegionAllowPrivateSellingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "set_region_allow_private_selling"));
    public static final StreamCodec<FriendlyByteBuf, SetRegionAllowPrivateSellingPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.regionId);
                buffer.writeBoolean(payload.allow);
            },
            buffer -> new SetRegionAllowPrivateSellingPayload(buffer.readUUID(), buffer.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetRegionAllowPrivateSellingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!FTBTeamsAPI.api().isManagerLoaded()) {
                return;
            }
            Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
            if (team == null || !team.getRankForPlayer(player.getUUID()).isMemberOrBetter()) {
                return;
            }
            RegionService.setAllowPrivateSelling(player.server, team, payload.regionId, payload.allow);
        });
    }
}
