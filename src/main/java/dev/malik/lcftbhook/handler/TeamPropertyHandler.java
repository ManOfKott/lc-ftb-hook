package dev.malik.lcftbhook.handler;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamPropertiesChangedEvent;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyCollection;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.service.ClaimVisibilityService;
import dev.malik.lcftbhook.service.ProtectionPricing;
import dev.malik.lcftbhook.service.ProtectionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class TeamPropertyHandler {
    public TeamPropertyHandler() {
        TeamEvent.PROPERTIES_CHANGED.register(this::onTeamPropertiesChanged);
    }

    private void onTeamPropertiesChanged(TeamPropertiesChangedEvent event) {
        if (ProtectionService.isReverting() || ProtectionService.isApplying()) {
            LCFtbHook.LOGGER.info("[PendingDebug] PROPERTIES_CHANGED ignored (reverting={}, applying={})",
                    ProtectionService.isReverting(), ProtectionService.isApplying());
            return;
        }

        Team team = event.getTeam();
        if (!team.isValid()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPropertyCollection previous = event.getPreviousProperties();
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());

        ProtectionService.tryUnlock(server, team);

        if (hasPropertyChanged(previous, team, FTBChunksProperties.CLAIM_VISIBILITY)
                && team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY) != PrivacyMode.PUBLIC) {
            LCFtbHook.LOGGER.info("[PendingDebug] Team {}: claim_visibility change to {} rejected (always public)",
                    team.getShortName(), team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY));
            ProtectionService.runReverting(() -> {
                team.setProperty(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
                team.syncOnePropertyToAll(server, FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
            });
            notifyProtectionDenied(team, "message.lc_ftb_hook.claim_visibility_locked");
        }

        for (TeamProperty<?> property : ProtectionPricing.PROTECTION_PROPERTIES) {
            String key = ProtectionPricing.propertyKey(property);

            if (!hasPropertyChanged(previous, team, property)) {
                // The submitted value matches the live server value. Untouched
                // entries never land here while a change is queued, because the
                // client pre-fills queued values into the screen (so they come
                // back as a "change" matching the pending value). Ending up
                // here with a queued change means the player switched the
                // setting back, which makes the queued change obsolete.
                if (pendingState.pendingProperties().containsKey(key)) {
                    pendingState = pendingState.withoutPendingProperty(key);
                    savedData.setPendingState(team.getTeamId(), pendingState);
                    LCFtbHook.LOGGER.info("[PendingDebug] Team {}: {} switched back to current value, pending cancelled; pending now: {}",
                            team.getShortName(), key, pendingState.pendingProperties());
                    notifyTeam(team, "message.lc_ftb_hook.protection_pending_cancelled");
                }
                continue;
            }

            Object previousValue = previous.get(property);
            Object newValue = team.getProperty(property);
            LCFtbHook.LOGGER.info("[PendingDebug] Team {}: {} changed {} -> {}",
                    team.getShortName(), key, previousValue, newValue);

            if (savedData.isProtectionLocked(team.getTeamId())) {
                LCFtbHook.LOGGER.info("[PendingDebug] Team {}: protections locked, reverting {}",
                        team.getShortName(), key);
                revertProperty(server, team, previous, property);
                notifyProtectionDenied(team, "message.lc_ftb_hook.protection_locked_change");
                continue;
            }

            String serializedNew = ProtectionPricing.serializePropertyValue(property, newValue);
            String existingPending = pendingState.pendingProperties().get(key);

            if (existingPending != null) {
                // A change for this property is already queued: further edits
                // replace the queued value instead of being applied directly.
                if (existingPending.equals(serializedNew)) {
                    LCFtbHook.LOGGER.info("[PendingDebug] Team {}: {} matches queued value {}, keeping pending",
                            team.getShortName(), key, existingPending);
                    revertProperty(server, team, previous, property);
                    continue;
                }

                // If the submitted value costs the same as the value that is
                // currently active (e.g. Allies -> Private while a change to
                // Public is queued), the change is cost-neutral: apply it
                // immediately and drop the queued change.
                TeamPendingState droppedState = pendingState.withoutPendingProperty(key);
                long activePrice = ProtectionPricing.calculateBasePrice(previous, droppedState.pendingProperties());
                long submittedPrice = ProtectionPricing.calculateBasePrice(
                        previous, droppedState.withPendingProperty(key, serializedNew).pendingProperties());
                if (activePrice == submittedPrice) {
                    pendingState = droppedState;
                    savedData.setPendingState(team.getTeamId(), pendingState);
                    LCFtbHook.LOGGER.info("[PendingDebug] Team {}: {} = {} is cost-neutral vs active value, applied immediately, pending dropped; pending now: {}",
                            team.getShortName(), key, serializedNew, pendingState.pendingProperties());
                    if (!ProtectionService.canAffordNextPeriod(server, team, pendingState)) {
                        revertProperty(server, team, previous, property);
                        savedData.setProtectionLocked(team.getTeamId(), true);
                        notifyProtectionDenied(team, "message.lc_ftb_hook.insufficient_funds_protection");
                    }
                    continue;
                }

                TeamPendingState replacedState = pendingState.withPendingProperty(key, serializedNew);
                if (!ProtectionService.canAffordNextPeriod(server, team, replacedState)) {
                    LCFtbHook.LOGGER.info("[PendingDebug] Team {}: cannot afford replacement pending {}, reverting",
                            team.getShortName(), key);
                    revertProperty(server, team, previous, property);
                    savedData.setProtectionLocked(team.getTeamId(), true);
                    notifyProtectionDenied(team, "message.lc_ftb_hook.insufficient_funds_protection");
                    continue;
                }

                pendingState = replacedState;
                savedData.setPendingState(team.getTeamId(), pendingState);
                revertProperty(server, team, previous, property);
                LCFtbHook.LOGGER.info("[PendingDebug] Team {}: {} pending value replaced {} -> {}; pending now: {}",
                        team.getShortName(), key, existingPending, serializedNew, pendingState.pendingProperties());
                notifyProtectionPending(team);
                continue;
            }

            TeamPendingState simulatedState = pendingState.withPendingProperty(key, serializedNew);

            // Prices must be calculated from the PREVIOUS property collection:
            // when this event fires, the team already carries the new values,
            // so using the team would always yield oldPrice == newPrice.
            long oldPrice = ProtectionPricing.calculateBasePrice(previous, pendingState.pendingProperties());
            long newPrice = ProtectionPricing.calculateBasePrice(previous, simulatedState.pendingProperties());
            LCFtbHook.LOGGER.info("[PendingDebug] Team {}: {} base price {} -> {}",
                    team.getShortName(), key, oldPrice, newPrice);

            if (oldPrice == newPrice) {
                TeamPendingState afterImmediate = simulatedState.withoutPendingProperty(key);
                savedData.setPendingState(team.getTeamId(), afterImmediate);
                if (!ProtectionService.canAffordNextPeriod(server, team, afterImmediate)) {
                    LCFtbHook.LOGGER.info("[PendingDebug] Team {}: cannot afford next period, reverting {}",
                            team.getShortName(), key);
                    revertProperty(server, team, previous, property);
                    savedData.setProtectionLocked(team.getTeamId(), true);
                    notifyProtectionDenied(team, "message.lc_ftb_hook.insufficient_funds_protection");
                } else {
                    LCFtbHook.LOGGER.info("[PendingDebug] Team {}: {} applied immediately (price unchanged)",
                            team.getShortName(), key);
                }
                continue;
            }

            if (!ProtectionService.canAffordNextPeriod(server, team, simulatedState)) {
                LCFtbHook.LOGGER.info("[PendingDebug] Team {}: cannot afford {} with pending change, reverting",
                        team.getShortName(), key);
                revertProperty(server, team, previous, property);
                savedData.setProtectionLocked(team.getTeamId(), true);
                notifyProtectionDenied(team, "message.lc_ftb_hook.insufficient_funds_protection");
                continue;
            }

            pendingState = simulatedState;
            savedData.setPendingState(team.getTeamId(), pendingState);
            revertProperty(server, team, previous, property);
            LCFtbHook.LOGGER.info("[PendingDebug] Team {}: {} queued as pending; pending now: {}",
                    team.getShortName(), key, pendingState.pendingProperties());
            notifyProtectionPending(team);
        }

        PendingStateSync.syncTeam(server, team);
    }

    private static <T> boolean hasPropertyChanged(TeamPropertyCollection previous, Team team, TeamProperty<T> property) {
        return !previous.get(property).equals(team.getProperty(property));
    }

    private static <T> void revertProperty(MinecraftServer server, Team team, TeamPropertyCollection previous, TeamProperty<T> property) {
        T value = previous.get(property);
        ProtectionService.runReverting(() -> {
            team.setProperty(property, value);
            // setProperty alone does not sync to clients, so without this the
            // client UI keeps showing the value we just reverted.
            team.syncOnePropertyToAll(server, property, value);
        });
        LCFtbHook.LOGGER.info("[PendingDebug] Team {}: reverted {} to {} (synced to clients)",
                team.getShortName(), ProtectionPricing.propertyKey(property), value);
    }

    private static void notifyProtectionDenied(Team team, String messageKey) {
        notifyTeam(team, messageKey);
    }

    private static void notifyProtectionPending(Team team) {
        notifyTeam(team, "message.lc_ftb_hook.protection_change_pending");
    }

    private static void notifyTeam(Team team, String messageKey) {
        Component message = Component.translatable(messageKey);
        for (ServerPlayer member : team.getOnlineMembers()) {
            member.displayClientMessage(message, false);
        }
    }
}
