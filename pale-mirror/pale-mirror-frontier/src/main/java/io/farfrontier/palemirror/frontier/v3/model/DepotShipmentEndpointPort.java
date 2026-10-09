package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

final class DepotShipmentEndpointPort implements ShipmentEndpointPort {
    @Override public ShipmentEndpoint.Kind kind() { return ShipmentEndpoint.Kind.SETTLEMENT_DEPOT; }
    private SettlementStructure facility(FrontierWorldState state, ShipmentEndpoint endpoint) {
        if (!(endpoint instanceof ShipmentEndpoint.Depot declared))
            throw new IllegalArgumentException("depot endpoint has a foreign nominal schema");
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), declared.settlementId());
        return home.structures().stream().filter(value -> value.id().equals(declared.facilityId()) && value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("shipment declared an unknown settlement depot"));
    }
    @Override public void validate(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var depot = facility(state, endpoint);
        if (!FrontierWorldState.depotId(endpoint.settlementId()).equals(endpoint.containerId())
                || !state.inventory().containers().containsKey(endpoint.containerId())
                || !SettlementDepotServicePort.forDepot(depot).ownedAccessSurfaces().contains(endpoint.station()))
            throw new IllegalArgumentException("shipment endpoint is not a declared depot service station");
    }
    @Override public List<KnownPedestrianRouteKnowledge.SettlementPassage> passages(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint);
        return List.of(new KnownPedestrianRouteKnowledge.SettlementPassage(endpoint.settlementId(),
                new KnownPedestrianRouteKnowledge.Passage(facility(state, endpoint), KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
    @Override public SurfaceAnchor exteriorApproach(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return SettlementDepotServicePort.forDepot(facility(state, endpoint)).exteriorApproach();
    }
}
