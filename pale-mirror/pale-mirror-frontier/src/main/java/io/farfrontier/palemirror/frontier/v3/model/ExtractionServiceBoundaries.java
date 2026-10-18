package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

final class ExtractionServiceBoundaries implements ServiceBoundaryProvider {
    private final ServicePointGeometryCache geometry = new ServicePointGeometryCache();
    @Override public ServicePointId.Kind kind() { return ServicePointId.Kind.EXTRACTIVE_STORAGE; }
    @Override public Object version(FrontierWorldState state) { return state.inventory().containers(); }
    @Override public List<Declaration> declarations(FrontierWorldState state) {
        return state.inventory().containers().values().stream()
                .filter(container -> container.purpose() == ContainerPurpose.EXTRACTIVE_STORAGE)
                .map(container -> endpoint(state, container.id())).map(endpoint -> new Declaration(
                    new ServicePointId(kind(), endpoint.settlementId(), endpoint.containerId()),
                    new ServiceAccessBoundary(Set.of(endpoint.station())))).toList();
    }
    private ShipmentEndpoint.ExtractiveSite endpoint(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId container) {
        var declared = state.inventory().containers().get(container).storage().orElseThrow().endpoint();
        if (!(declared instanceof ShipmentEndpoint.ExtractiveSite endpoint))
            throw new IllegalArgumentException("extractive storage lacks its declared endpoint");
        ShipmentEndpointComposition.validate(state, endpoint);
        return endpoint;
    }
    @Override public KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, Declaration declaration) {
        declaration.identity().validate(state.inventory());
        return KnownPedestrianRouteKnowledge.forFrontier(state);
    }
    @Override public synchronized ServiceAccessPoint geometry(FrontierWorldState state, Declaration declaration) {
        var knowledge = knowledge(state, declaration);
        var endpoint = endpoint(state, declaration.pointId());
        return geometry.get(declaration.pointId(), new ServicePointGeometryCache.Request(
                endpoint.siteId(), endpoint.settlementId(), endpoint.station(), declaration.boundary(),
                endpoint.station(), List.of(), 0, false), knowledge);
    }
}
