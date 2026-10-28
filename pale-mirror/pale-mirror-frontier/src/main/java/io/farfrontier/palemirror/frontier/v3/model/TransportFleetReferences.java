package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet;
import java.util.Map;

/** Cross-owner closure; assets do not duplicate body condition, pose, stock or money. */
final class TransportFleetReferences {
    private static final ImmutableInvariantCheck<Void> REFERENCES = new ImmutableInvariantCheck<>();
    private TransportFleetReferences() { }
    static void validate(FrontierBootstrap bootstrap, TransportFleet fleet, Map<SubjectId, ActorLocation> actors,
                         ExactInventory inventory, ShipmentState shipments) {
        var kinds = ActorIdentityDeclarations.declaredKinds(bootstrap, actors);
        REFERENCES.validate(() -> validateCurrent(bootstrap, fleet, kinds, inventory, shipments), bootstrap, fleet.assets(),
                kinds, inventory.containers(), inventory.surfaces(), shipments.missions());
    }
    private static void validateCurrent(FrontierBootstrap bootstrap, TransportFleet fleet, Map<SubjectId, ActorKind> kinds,
                                        ExactInventory inventory, ShipmentState shipments) {
        for (var asset : fleet.assets().values()) {
            FrontierWorldStateSupport.settlement(bootstrap, asset.homeSettlementId());
            FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), asset.homeStation().support());
            var kind = kinds.get(asset.actorId());
            var container = inventory.containers().get(asset.containerId());
            var surface = inventory.surfaces().get(asset.containerId());
            if (kind != ActorKind.PACK_ANIMAL || container == null || surface == null
                    || !container.ownerId().equals(asset.homeSettlementId()) || container.slotCount() != asset.stackSlots()
                    || !surface.location().equals(new ContainerLocation.Mobile(asset.actorId())))
                throw new IllegalArgumentException("transport asset lost its exact actor/container declaration: " + asset.actorId());
            asset.missionId().ifPresent(id -> {
                var mission = shipments.missions().get(id);
                if (mission == null || mission.stage() == TransportMission.Stage.COMPLETE
                        || !mission.transportAssetId().equals(java.util.Optional.of(asset.actorId()))
                        || !mission.organization().home().settlementId().equals(asset.homeSettlementId()))
                    throw new IllegalArgumentException("transport asset has a dangling mission reservation");
            });
        }
        for (var mission : shipments.missions().values()) mission.transportAssetId().ifPresent(actor -> {
            var asset = fleet.require(actor);
            if (!asset.homeSettlementId().equals(mission.organization().home().settlementId())
                    || mission.stage() != TransportMission.Stage.COMPLETE && !asset.missionId().equals(java.util.Optional.of(mission.id()))
                    || mission.stage() == TransportMission.Stage.COMPLETE && asset.missionId().equals(java.util.Optional.of(mission.id())))
                throw new IllegalArgumentException("transport mission lost its exact finite-asset reservation");
        });
        for (var surface : inventory.surfaces().values()) {
            if (surface.location() instanceof ContainerLocation.Mobile mobile) {
                var asset = fleet.assets().get(mobile.actorId());
                if (asset == null || !asset.containerId().equals(surface.containerId()))
                    throw new IllegalArgumentException("mobile container has no exact transport producer");
            }
        }
    }
}
