package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Installed equipment is a shared service point; its worker does not own a private queue. */
final class ProductionServiceBoundaries implements ServiceBoundaryProvider {
    private final ServicePointGeometryCache geometries = new ServicePointGeometryCache();
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
        return geometries.get(declaration.pointId(), request(state, declaration), knowledge(state, declaration));
    }
    @Override public Set<TerrainColumn> searchColumns(FrontierWorldState state, Declaration declaration) {
        return request(state, declaration).searchColumns();
    }
    private ServicePointGeometryCache.Request request(FrontierWorldState state, Declaration declaration) {
        var endpoint = endpoint(state, declaration);
        int population = SettlementServiceAccessPoints.population(state, declaration.settlementId());
        return new ServicePointGeometryCache.Request(
                endpoint.spec().facilityId(), declaration.settlementId(), endpoint.station(), declaration.boundary(),
                endpoint.exteriorApproach(), List.of(), population, true);
    }
}
