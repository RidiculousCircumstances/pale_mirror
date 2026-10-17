package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Installed equipment is a shared service point; its worker does not own a private queue. */
final class ProductionServiceBoundaries implements ServiceBoundaryProvider {
    private record Geometry(KnownPedestrianRouteKnowledge knowledge, ShipmentEndpoint.ProductionPort endpoint,
                            int population, ServiceAccessPoint point) { }
    private final Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Geometry> geometries = new LinkedHashMap<>();
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
    @Override public synchronized ServiceAccessPoint geometry(FrontierWorldState state, Declaration declaration) {
        var endpoint = endpoint(state, declaration); var knowledge = knowledge(state, declaration);
        int population = SettlementServiceAccessPoints.population(state, declaration.settlementId());
        var prior = geometries.get(declaration.pointId());
        if (prior != null && prior.knowledge() == knowledge && prior.endpoint().equals(endpoint)
                && prior.population() == population) return prior.point();
        var egress = ServiceClearanceTargets.egressRegion(declaration.boundary(), knowledge);
        var waiting = ServiceWaitingTargets.select(declaration.boundary(), knowledge, endpoint.exteriorApproach(),
                List.of(), egress, population);
        var point = new ServiceAccessPoint(endpoint.spec().facilityId(), declaration.settlementId(), endpoint.station(),
                declaration.boundary(), waiting, egress);
        if (geometries.size() >= 64) geometries.clear();
        geometries.put(declaration.pointId(), new Geometry(knowledge, endpoint, population, point));
        return point;
    }
}
