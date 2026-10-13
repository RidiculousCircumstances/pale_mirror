package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Machine ownership supplies installed identity/geometry; transport never interprets recipes. */
final class ProductionShipmentEndpointPort implements ShipmentEndpointPort {
    @Override public ShipmentEndpoint.Kind kind() { return ShipmentEndpoint.Kind.PRODUCTION_PORT; }
    @Override public ShipmentReceiverReplacement replacementPolicy() { return ProductionShipmentReceiverReplacement.INSTANCE; }
    private ShipmentEndpoint.ProductionPort declaration(ShipmentEndpoint endpoint) {
        if (!(endpoint instanceof ShipmentEndpoint.ProductionPort value))
            throw new IllegalArgumentException("production endpoint has foreign nominal schema");
        return value;
    }
    private SettlementStructure facility(FrontierBootstrap bootstrap, ShipmentEndpoint.ProductionPort value) {
        return FrontierWorldStateSupport.settlement(bootstrap, value.settlementId()).structures().stream()
                .filter(facility -> facility.id().equals(value.spec().facilityId())).findFirst().orElseThrow();
    }
    @Override public void validate(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var value = declaration(endpoint); var record = state.inventory().containers().get(value.containerId());
        if (record == null || !record.ownerId().equals(value.settlementId())
                || !record.productionStation().equals(java.util.Optional.of(value.spec())))
            throw new IllegalArgumentException("shipment lost its exact installed machine port");
        declaredStations(state.bootstrap(), endpoint);
    }
    @Override public List<KnownPedestrianRouteKnowledge.SettlementPassage> passages(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); var value = declaration(endpoint);
        return List.of(new KnownPedestrianRouteKnowledge.SettlementPassage(value.settlementId(),
                new KnownPedestrianRouteKnowledge.Passage(facility(state.bootstrap(), value), KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)));
    }
    @Override public List<SurfaceAnchor> declaredStations(FrontierBootstrap bootstrap, ShipmentEndpoint endpoint) {
        var value = declaration(endpoint); var port = SettlementWorkshopServicePort.forWorkshop(facility(bootstrap, value));
        if (!port.workStation().equals(value.station()) || !port.exteriorApproach().equals(value.exteriorApproach()))
            throw new IllegalArgumentException("production endpoint lost declared facility access geometry");
        return List.of(value.station());
    }
    @Override public List<SurfaceAnchor> stations(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return List.of(endpoint.station());
    }
    @Override public SurfaceAnchor exteriorApproach(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return declaration(endpoint).exteriorApproach();
    }
    @Override public SurfaceAnchor interactionStation(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return endpoint.station();
    }
}
