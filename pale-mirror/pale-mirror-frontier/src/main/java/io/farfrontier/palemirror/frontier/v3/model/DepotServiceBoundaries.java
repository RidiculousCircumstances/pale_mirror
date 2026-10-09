package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

final class DepotServiceBoundaries implements ServiceBoundaryProvider {
    @Override public ServicePointId.Kind kind() { return ServicePointId.Kind.SETTLEMENT_DEPOT; }
    @Override public Object version(FrontierWorldState state) { return state.bootstrap(); }
    @Override public List<Declaration> declarations(FrontierWorldState state) {
        return state.bootstrap().settlements().stream().flatMap(home -> home.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT)
                .map(structure -> new Declaration(new ServicePointId(kind(), home.id(), FrontierWorldState.depotId(home.id())),
                        SettlementDepotServicePort.forDepot(structure).accessBoundary()))).toList();
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
}
