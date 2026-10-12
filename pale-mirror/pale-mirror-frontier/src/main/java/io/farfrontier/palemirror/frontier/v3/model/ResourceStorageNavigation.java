package io.farfrontier.palemirror.frontier.v3.model;

/** Read-only access admission. It acquires no actor, service slot, movement or resource reservation. */
public final class ResourceStorageNavigation {
    private ResourceStorageNavigation() { }
    public static java.util.List<SurfaceAnchor> plannedPath(FrontierWorldState state, SurfaceAnchor origin, ShipmentEndpoint target) {
        return forEndpoint(state, target).plannedBetween(origin, target.station());
    }
    public static void requireAcceptedDestination(FrontierWorldState state, SurfaceAnchor origin, ShipmentEndpoint target,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt> receipt) {
        var route = receipt.map(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt::route)
                .orElseGet(() -> java.util.List.of(origin));
        if (!route.getFirst().equals(origin) || !route.getLast().equals(target.station()))
            throw new IllegalArgumentException("storage admission route has foreign endpoints");
        forEndpoint(state, target).requireRoute(route);
    }
    /** Common public/declared-port geometry, also used for departure from this storage. */
    public static KnownPedestrianRouteKnowledge forEndpoint(FrontierWorldState state, ShipmentEndpoint target) {
        return forJourney(state, target.settlementId(), java.util.List.of(target));
    }
    /** Shared geometry for work connecting declared storage ports and ordinary public passages. */
    public static KnownPedestrianRouteKnowledge forJourney(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId home, java.util.List<ShipmentEndpoint> endpoints) {
        endpoints = java.util.List.copyOf(endpoints);
        if (endpoints.isEmpty() || endpoints.size() > 8 || endpoints.stream().anyMatch(value -> !value.settlementId().equals(home)))
            throw new IllegalArgumentException("local storage journey needs bounded declared same-home ports");
        var passages = endpoints.stream().flatMap(value -> ShipmentEndpointComposition.passages(state, value).stream()).distinct().toList();
        return KnownPedestrianRouteKnowledge.forPublicJourney(state, home, passages);
    }
}
