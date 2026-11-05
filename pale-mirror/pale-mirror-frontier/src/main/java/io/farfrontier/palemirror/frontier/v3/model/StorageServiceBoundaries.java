package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Ordinary declared storage participates in the same access/clearance protocol as existing service points. */
final class StorageServiceBoundaries implements ServiceBoundaryProvider {
    private final ServicePointGeometryCache geometries = new ServicePointGeometryCache();
    private final ImmutableInputView<Object> versions=new ImmutableInputView<>();
    @Override public ServicePointId.Kind kind() { return ServicePointId.Kind.RESOURCE_STORAGE; }
    @Override public Object version(FrontierWorldState state) { return versions.get(List.of(state.inventory().containers(),state.inventory().surfaces()),Object::new); }
    @Override public List<Declaration> declarations(FrontierWorldState state) {
        return state.inventory().containers().values().stream().filter(record -> record.purpose() == ContainerPurpose.RESOURCE_STORAGE)
                .map(record -> {
                    var endpoint = endpoint(state, record.id());
                    return new Declaration(new ServicePointId(kind(), endpoint.settlementId(), endpoint.containerId()),
                            StorageInteractionStations.boundary(state.inventory().surfaces().get(record.id()).position()),
                            ShipmentEndpointComposition.supportingFacility(state, endpoint).map(facility -> {
                                var area = facility.placement().volume(facility.definition().fabric());
                                return new io.farfrontier.palemirror.frontier.v3.model.geometry.BlockVolume(area.min(), area.max().offset(-1, -1, -1));
                            }));
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
        return geometries.get(declaration.pointId(), request(state, declaration), knowledge(state, declaration));
    }
    @Override public SearchFootprint searchFootprint(FrontierWorldState state, Declaration declaration) {
        return request(state, declaration).searchFootprint();
    }
    private ServicePointGeometryCache.Request request(FrontierWorldState state, Declaration declaration) {
        var endpoint = endpoint(state, declaration.pointId());
        int population = SettlementServiceAccessPoints.population(state, endpoint.settlementId());
        return new ServicePointGeometryCache.Request(
                endpoint.facilityId().orElse(endpoint.containerId()), endpoint.settlementId(), endpoint.station(),
                declaration.boundary(), endpoint.station(), List.of(), population, true);
    }
}
