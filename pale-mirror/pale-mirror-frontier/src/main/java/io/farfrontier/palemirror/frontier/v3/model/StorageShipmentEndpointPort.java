package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach;

/** Registered ordinary storage geometry; the logistics coordinator does not interpret buildings. */
final class StorageShipmentEndpointPort implements ShipmentEndpointPort {
    @Override public ShipmentEndpoint.Kind kind() { return ShipmentEndpoint.Kind.DECLARED_STORAGE; }
    private ShipmentEndpoint.Storage declaration(FrontierWorldState state, ShipmentEndpoint endpoint) {
        if (!(endpoint instanceof ShipmentEndpoint.Storage storage))
            throw new IllegalArgumentException("storage endpoint has a foreign nominal schema");
        var container = state.inventory().containers().get(storage.containerId());
        var surface = state.inventory().surfaces().get(storage.containerId());
        if (container == null || surface == null || !container.ownerId().equals(storage.settlementId())
                || container.storage().isEmpty() || !container.storage().orElseThrow().endpoint().equals(storage)
                || !surface.fixed() || !BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(storage.station(), surface.position()))
            throw new IllegalArgumentException("storage endpoint lost its exact declared owner, policy or reachable surface");
        return storage;
    }
    @Override public void validate(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var storage = declaration(state, endpoint);
        if (storage.facilityId().isPresent()) facility(state, storage);
    }
    private SettlementStructure facility(FrontierWorldState state, ShipmentEndpoint.Storage storage) {
        return FrontierWorldStateSupport.settlement(state.bootstrap(), storage.settlementId()).structures().stream()
                .filter(value -> storage.facilityId().filter(value.id()::equals).isPresent()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("storage access lost its declared facility"));
    }
    @Override public List<KnownPedestrianRouteKnowledge.SettlementPassage> passages(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var storage = declaration(state, endpoint);
        return storage.facilityId().isEmpty() ? List.of() : List.of(new KnownPedestrianRouteKnowledge.SettlementPassage(
                storage.settlementId(), new KnownPedestrianRouteKnowledge.Passage(facility(state, storage),
                    KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
    @Override public SurfaceAnchor exteriorApproach(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return ((ShipmentEndpoint.Storage) endpoint).exteriorApproach();
    }
    @Override public List<SurfaceAnchor> stations(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return declaredStations(state.bootstrap(), endpoint);
    }
    @Override public List<SurfaceAnchor> declaredStations(FrontierBootstrap bootstrap, ShipmentEndpoint endpoint) {
        if (!(endpoint instanceof ShipmentEndpoint.Storage))
            throw new IllegalArgumentException("storage endpoint has a foreign nominal schema");
        return List.of(endpoint.station());
    }
    @Override public SurfaceAnchor interactionStation(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return endpoint.station();
    }
}
