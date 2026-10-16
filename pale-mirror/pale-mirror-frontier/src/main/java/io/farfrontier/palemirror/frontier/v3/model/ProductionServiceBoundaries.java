package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Installed equipment is a shared service point; its worker does not own a private queue. */
final class ProductionServiceBoundaries implements ServiceBoundaryProvider {
    @Override public ServicePointId.Kind kind() { return ServicePointId.Kind.PRODUCTION_STATION; }
    @Override public Object version(FrontierWorldState state) { return state.inventory().containers(); }
    @Override public List<Declaration> declarations(FrontierWorldState state) {
        return state.inventory().containers().values().stream().filter(record -> record.purpose() == ContainerPurpose.PRODUCTION_STATION)
                .map(record -> new Declaration(new ServicePointId(kind(), record.ownerId(), record.id()),
                        new ServiceAccessBoundary(Set.of(record.productionStation().orElseThrow().workerStation())))).toList();
    }
    private ShipmentEndpoint.ProductionPort endpoint(FrontierWorldState state, Declaration declaration) {
        declaration.identity().validate(state.inventory());
        var spec = state.inventory().containers().get(declaration.pointId()).productionStation().orElseThrow();
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), declaration.settlementId()).structures().stream()
                .filter(value -> value.id().equals(spec.facilityId())).findFirst().orElseThrow();
        return new ShipmentEndpoint.ProductionPort(declaration.settlementId(), spec, ActorContainerItemOrder.StationPort.INPUT,
                SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach());
    }
    @Override public KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, Declaration declaration) {
        return ShipmentEndpointComposition.knowledge(state, List.of(endpoint(state, declaration)));
    }
    @Override public ServiceAccessPoint geometry(FrontierWorldState state, Declaration declaration) {
        var endpoint = endpoint(state, declaration); var knowledge = knowledge(state, declaration);
        var egress = ServiceClearanceTargets.egressRegion(declaration.boundary(), knowledge);
        var waiting = ServiceWaitingTargets.select(declaration.boundary(), knowledge, endpoint.exteriorApproach(),
                List.of(), egress, SettlementServiceAccessPoints.population(state, declaration.settlementId()));
        return new ServiceAccessPoint(endpoint.spec().facilityId(), declaration.settlementId(), endpoint.station(),
                declaration.boundary(), waiting, egress);
    }
}
