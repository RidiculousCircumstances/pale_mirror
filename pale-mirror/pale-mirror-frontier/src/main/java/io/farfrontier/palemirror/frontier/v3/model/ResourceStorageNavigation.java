package io.farfrontier.palemirror.frontier.v3.model;

/** Read-only access admission. It acquires no actor, service slot, movement or resource reservation. */
public final class ResourceStorageNavigation {
    private ResourceStorageNavigation() { }
    /** The stored station is a preferred contact, not a requirement to climb onto another container. */
    public static java.util.Optional<ShipmentEndpoint> selectApproach(FrontierWorldState state, SurfaceAnchor origin,
            io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile profile, ShipmentEndpoint target) {
        return ActorStorageApproach.selectForProfile(state, target, origin,
                forDeparture(state, origin, target).forProfile(profile));
    }
    public static java.util.List<SurfaceAnchor> plannedPath(FrontierWorldState state, SurfaceAnchor origin, io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile profile, ShipmentEndpoint target) {
        return forDeparture(state, origin, target).forProfile(profile).plannedBetween(origin, target.station());
    }
    public static void requireAcceptedDestination(FrontierWorldState state, SurfaceAnchor origin, io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile profile, ShipmentEndpoint target,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt> receipt) {
        var route = receipt.map(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt::route)
                .orElseGet(() -> java.util.List.of(origin));
        if (!route.getFirst().equals(origin) || !route.getLast().equals(target.station()))
            throw new IllegalArgumentException("storage admission route has foreign endpoints");
        forDeparture(state, origin, target).forProfile(profile).requireRoute(route);
    }
    private static KnownPedestrianRouteKnowledge forDeparture(FrontierWorldState state, SurfaceAnchor origin, ShipmentEndpoint target) {
        var passages = new java.util.ArrayList<>(ShipmentEndpointComposition.passages(state, target));
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), target.settlementId());
        settlement.structures().forEach(facility -> passages.add(new KnownPedestrianRouteKnowledge.SettlementPassage(
                settlement.id(), new KnownPedestrianRouteKnowledge.Passage(facility,
                        KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS))));
        return PedestrianLocalDeparture.departure(state, origin, passages);
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
