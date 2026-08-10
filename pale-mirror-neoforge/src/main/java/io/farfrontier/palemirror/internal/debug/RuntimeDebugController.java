package io.farfrontier.palemirror.internal.debug;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.world.CampaignRegionBootstrapper;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Narrow facade which keeps debug command plumbing out of the core server coordinator. */
public final class RuntimeDebugController {
    private final MinecraftServer server;
    private final PaleMirrorSavedData data;
    private final DomainCommandProcessor commands;
    private final Consumer<List<DomainEvent>> eventHandler;
    private final RuntimeDebugService service = new RuntimeDebugService();

    public RuntimeDebugController(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands,
                                  Consumer<List<DomainEvent>> eventHandler) {
        this.server = server;
        this.data = data;
        this.commands = commands;
        this.eventHandler = eventHandler;
    }

    public boolean automaticBindingEnabled() { return service.automaticBindingEnabled(); }
    public void renderZoneMarkers() { service.renderZoneMarkers(server, data); }
    public String discoveryStatus() { return service.discoveryStatus(); }
    public String discoveryMode(RuntimeDebugService.DiscoveryMode mode) { return service.setDiscoveryMode(mode); }
    public String settlementCandidates(ServerPlayer player) { return service.settlementCandidates(server, data, player); }
    public String nearestSettlement(ServerPlayer player) { return service.nearestCandidate(server, data, player); }
    public RuntimeDebugService.ActionResult bindNearest(ServerPlayer player) {
        return service.bindNearest(server, data, commands, player);
    }
    public RuntimeDebugService.ActionResult bind(WorldObjectId id) { return service.bind(server, data, commands, id); }
    public String regionStatus() { return service.regionStatus(data); }
    public String verify() { return service.verify(server, data); }
    public String zoneMarkers(ServerPlayer player, boolean enabled) { return service.setZoneMarkers(player, enabled); }
    public String zoneMarkerStatus(ServerPlayer player) { return service.zoneMarkerStatus(player); }
    public RuntimeDebugService.ActionResult resetPreview(UUID operator) {
        return service.previewReset(operator, server.overworld().getGameTime(), data);
    }
    public RuntimeDebugService.ActionResult resetConfirm(UUID operator, String token) {
        return service.confirmReset(operator, token, server.overworld().getGameTime(), data);
    }

    public RuntimeDebugService.ActionResult triggerPrimaryInfection() {
        var region = data.worldState().livingRegion(CampaignRegionBootstrapper.IRONHILL_ID).orElse(null);
        if (region == null) return new RuntimeDebugService.ActionResult(false, "No living region is bound.");
        List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.TriggerFacilityInfection(
                region.primaryFacilityId(), "debug:operator-trigger:" + data.worldState().simulationStep()));
        eventHandler.accept(events);
        if (!events.isEmpty()) data.setDirty();
        return events.isEmpty()
                ? new RuntimeDebugService.ActionResult(false, "Primary mine did not accept infection in its current state.")
                : new RuntimeDebugService.ActionResult(true, "Triggered canonical infection for " + region.primaryFacilityId().value()
                + "; produced " + events.size() + " domain event(s).");
    }
}
