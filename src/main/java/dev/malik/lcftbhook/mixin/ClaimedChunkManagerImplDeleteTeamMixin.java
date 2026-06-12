package dev.malik.lcftbhook.mixin;

import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.PartyDisbandSettlementService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClaimedChunkManagerImpl.class, remap = false)
public class ClaimedChunkManagerImplDeleteTeamMixin {
    @Inject(method = "deleteTeam", at = @At("HEAD"), remap = false)
    private void lcFtbHook$settleBeforeDelete(Team team, CallbackInfo ci) {
        if (team == null || !team.isPartyTeam()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        try {
            PartyDisbandSettlementService.settle(server, team);
        } catch (Throwable error) {
            LCFtbHook.LOGGER.error(
                    "Party disband settlement failed for {} - FTB party deletion will continue",
                    team.getId(),
                    error
            );
        }
    }
}
