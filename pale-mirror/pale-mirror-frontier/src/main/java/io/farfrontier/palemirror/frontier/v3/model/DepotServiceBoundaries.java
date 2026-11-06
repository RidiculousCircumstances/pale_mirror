package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

final class DepotServiceBoundaries implements ServiceBoundaryProvider {
    @Override public ServicePointId.Kind kind() { return ServicePointId.Kind.SETTLEMENT_DEPOT; }
    @Override public Object version(FrontierWorldState state) { return state.inventory().containers(); }
    @Override public List<Declaration> declarations(FrontierWorldState state) {
        return state.inventory().containers().values().stream()
                .filter(record -> record.purpose() == ContainerPurpose.SETTLEMENT_DEPOT)
                .map(record -> {
                    if (!(record.storage().orElseThrow().endpoint() instanceof ShipmentEndpoint.Depot endpoint))
                        throw new IllegalArgumentException("depot service lacks its exact endpoint declaration");
                    var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), endpoint.settlementId()).structures().stream()
                            .filter(structure -> structure.id().equals(endpoint.facilityId())).findFirst().orElseThrow();
                    return new Declaration(new ServicePointId(kind(), record.ownerId(), record.id()),
                            SettlementDepotServicePort.forDepot(facility).accessBoundary());
                }).toList();
    }
    private SettlementStructure depot(FrontierWorldState state, Declaration declaration) {
        declaration.identity().validate(state.inventory());
        return FrontierWorldStateSupport.settlement(state.bootstrap(), declaration.settlementId()).structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
    }
    @Override public KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, Declaration declaration) {
        return KnownPedestrianRouteKnowledge.forSettlement(state, declaration.settlementId(), List.of(
                new KnownPedestrianRouteKnowledge.Passage(depot(state, declaration), KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
    @Override public ServiceAccessPoint geometry(FrontierWorldState state, Declaration declaration) {
        return SettlementServiceAccessPoints.forDepot(state,
                FrontierWorldStateSupport.settlement(state.bootstrap(), declaration.settlementId()), depot(state, declaration));
    }
    @Override public SearchFootprint searchFootprint(FrontierWorldState state, Declaration declaration) {
        return SettlementServiceAccessPoints.searchFootprint(state,
                FrontierWorldStateSupport.settlement(state.bootstrap(), declaration.settlementId()), depot(state, declaration));
    }
}
