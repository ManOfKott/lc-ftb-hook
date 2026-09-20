package dev.malik.lcftbhook.mixin;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionResolution;
import dev.malik.lcftbhook.service.RegionService;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every claimed chunk now belongs to a Region (the auto-created Default
 * catches unassigned ones), and explosions/mob griefing are read from that
 * Region's own stored value (further loosened by a private owner's
 * marketplace override, if any) instead of a flat team-wide build/land split.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl", remap = false)
public abstract class ClaimedChunkRegionProtectionMixin {
    @Inject(method = "allowExplosions", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$regionExplosions(CallbackInfoReturnable<Boolean> cir) {
        ClaimedChunk chunk = (ClaimedChunk) (Object) this;
        Region region = RegionService.resolveRegion(chunk);
        ChunkOwnership ownership = resolveOwnership(chunk);
        cir.setReturnValue(ProtectionResolution.isAtMinimum(region, ownership, ProtectionProperty.ALLOW_EXPLOSIONS));
    }

    @Inject(method = "allowMobGriefing", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$regionMobGriefing(CallbackInfoReturnable<Boolean> cir) {
        ClaimedChunk chunk = (ClaimedChunk) (Object) this;
        Region region = RegionService.resolveRegion(chunk);
        ChunkOwnership ownership = resolveOwnership(chunk);
        cir.setReturnValue(ProtectionResolution.isAtMinimum(region, ownership, ProtectionProperty.ALLOW_MOB_GRIEFING));
    }

    private static ChunkOwnership resolveOwnership(ClaimedChunk chunk) {
        Team team = chunk.getTeamData().getTeam();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (team == null || server == null) {
            return ChunkOwnership.EMPTY;
        }
        return FtbHookSavedData.get(server).getChunkOwnership(team.getTeamId(), ChunkPosKey.encode(chunk.getPos()));
    }
}
