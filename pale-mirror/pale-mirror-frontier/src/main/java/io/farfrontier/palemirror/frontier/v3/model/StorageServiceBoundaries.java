package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Ordinary declared storage participates in the same access/clearance protocol as existing service points. */
final class StorageServiceBoundaries implements ServiceBoundaryProvider {
    private record Geometry(KnownPedestrianRouteKnowledge knowledge, ShipmentEndpoint.Storage endpoint,
                            int population, ServiceAccessPoint point) { }
    private final Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Geometry> geometries = new LinkedHashMap<>();
    @Override public ServicePointId.Kind kind() { return ServicePointId.Kind.RESOURCE_STORAGE; }
    @Override public Object version(FrontierWorldState state) { return state.inventory().containers(); }
    @Override public List<Declaration> declarations(FrontierWorldState state) {
        return state.inventory().containers().values().stream().filter(record -> record.purpose() == ContainerPurpose.RESOURCE_STORAGE)
                .map(record -> {
                    var endpoint = endpoint(state, record.id());
                    return new Declaration(new ServicePointId(kind(), endpoint.settlementId(), endpoint.containerId()),
                            new ServiceAccessBoundary(Set.of(endpoint.station())));
                }).toList();
    }
    private ShipmentEndpoint.Storage endpoint(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId id) {
        var policy = state.inventory().containers().get(id).storage().orElseThrow();
        if (!(policy.endpoint() instanceof ShipmentEndpoint.Storage storage))
            throw new IllegalArgumentException("ordinary resource storage has no declared storage access geometry");
        ShipmentEndpointComposition.validate(state, storage); return storage;
    }
    @Override public KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, Declaration declaration) {
        declaration.identity().validate(state.inventory());
        return ShipmentEndpointComposition.knowledge(state, List.of(endpoint(state, declaration.pointId())));
    }
    @Override public synchronized ServiceAccessPoint geometry(FrontierWorldState state, Declaration declaration) {
        var endpoint = endpoint(state, declaration.pointId());
        var knowledge = knowledge(state, declaration);
        int population = SettlementServiceAccessPoints.population(state, endpoint.settlementId());
        var previous = geometries.get(declaration.pointId());
        if (previous != null && previous.knowledge() == knowledge && previous.endpoint().equals(endpoint)
                && previous.population() == population) return previous.point();
        var egress = ServiceClearanceTargets.egressRegion(declaration.boundary(), knowledge);
        var waiting = ServiceWaitingTargets.select(declaration.boundary(), knowledge, endpoint.station(), List.of(), egress, population);
        var point = new ServiceAccessPoint(endpoint.facilityId().orElse(endpoint.containerId()), endpoint.settlementId(), endpoint.station(),
                declaration.boundary(), waiting, egress);
        if (geometries.size() >= 64) geometries.clear();
        geometries.put(declaration.pointId(), new Geometry(knowledge, endpoint, population, point));
        return point;
    }
}
