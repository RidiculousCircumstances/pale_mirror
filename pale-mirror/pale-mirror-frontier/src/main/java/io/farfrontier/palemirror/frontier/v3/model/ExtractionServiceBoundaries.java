package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

final class ExtractionServiceBoundaries implements ServiceBoundaryProvider {
    private KnownPedestrianRouteKnowledge cachedKnowledge;
    private final Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ServiceAccessPoint> geometry = new HashMap<>();
    @Override public ServicePointId.Kind kind() { return ServicePointId.Kind.EXTRACTIVE_STORAGE; }
    @Override public Object version(FrontierWorldState state) { return state.extractionSites().deposits(); }
    @Override public List<Declaration> declarations(FrontierWorldState state) {
        return state.extractionSites().deposits().values().stream().map(deposit -> new Declaration(
                new ServicePointId(kind(), deposit.site().settlementId(), deposit.site().containerId()),
                new ServiceAccessBoundary(Set.of(deposit.site().layout().storagePort())))).toList();
    }
    @Override public KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, Declaration declaration) {
        declaration.identity().validate(state.inventory());
        return KnownPedestrianRouteKnowledge.forFrontier(state);
    }
    @Override public synchronized ServiceAccessPoint geometry(FrontierWorldState state, Declaration declaration) {
        var knowledge = knowledge(state, declaration);
        if (cachedKnowledge != knowledge) { geometry.clear(); cachedKnowledge = knowledge; }
        var prior = geometry.get(declaration.pointId());
        if (prior != null) return prior;
        var site = state.extractionSites().deposits().values().stream().map(value -> value.site())
                .filter(value -> value.containerId().equals(declaration.pointId())).findFirst().orElseThrow();
        var egress = ServiceClearanceTargets.egressRegion(declaration.boundary(), knowledge);
        var point = new ServiceAccessPoint(site.id(), site.settlementId(), site.layout().storagePort(),
                declaration.boundary(), List.of(), egress);
        geometry.put(declaration.pointId(), point);
        return point;
    }
}
