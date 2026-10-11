package io.farfrontier.palemirror.frontier.v3.model;

/** Read-only access admission. It acquires no actor, service slot, movement or resource reservation. */
public final class ResourceStorageNavigation {
    private ResourceStorageNavigation() { }
    public static boolean reachable(FrontierWorldState state, SurfaceAnchor origin, ShipmentEndpoint target) {
        return KnownPedestrianRouteKnowledge.forPublicJourney(state, target.settlementId(),
                ShipmentEndpointComposition.passages(state, target)).reachable(origin, target.station());
    }
}
