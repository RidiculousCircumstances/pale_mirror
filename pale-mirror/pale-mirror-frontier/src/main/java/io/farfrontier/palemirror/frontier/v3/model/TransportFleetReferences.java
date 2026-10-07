package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet;
import java.util.Map;

/** Cross-owner closure; assets do not duplicate body condition, pose, stock or money. */
final class TransportFleetReferences {
    private TransportFleetReferences() { }
    static void validate(FrontierBootstrap bootstrap, TransportFleet fleet, Map<SubjectId, ActorLocation> actors,
                         ExactInventory inventory, ShipmentState shipments) {
        for (var asset : fleet.assets().values()) {
            FrontierWorldStateSupport.settlement(bootstrap, asset.homeSettlementId());
            FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), asset.homeStation().support());
            var body = actors.get(asset.actorId());
            var container = inventory.containers().get(asset.containerId());
            var surface = inventory.surfaces().get(asset.containerId());
            if (body == null || body.kind() != ActorKind.PACK_ANIMAL || container == null || surface == null
                    || !container.ownerId().equals(asset.homeSettlementId()) || container.slotCount() != asset.stackSlots()
                    || !surface.location().equals(new ContainerLocation.Mobile(asset.actorId())))
                throw new IllegalArgumentException("transport asset lost its exact actor/container declaration: " + asset.actorId());
            asset.missionId().ifPresent(id -> {
                var mission = shipments.missions().get(id);
                if (mission == null || mission.stage() == TransportMission.Stage.COMPLETE
                        || !mission.sender().settlementId().equals(asset.homeSettlementId()))
                    throw new IllegalArgumentException("transport asset has a dangling mission reservation");
            });
        }
        for (var surface : inventory.surfaces().values()) {
            if (surface.location() instanceof ContainerLocation.Mobile mobile) {
                var asset = fleet.assets().get(mobile.actorId());
                if (asset == null || !asset.containerId().equals(surface.containerId()))
                    throw new IllegalArgumentException("mobile container has no exact transport producer");
            }
        }
    }
}
