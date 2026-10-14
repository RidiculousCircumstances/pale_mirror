package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Requested detail horizon, not a different destination or permission to award arrival. */
public record PedestrianRouteExtent(int maximumSurfaces) {
    public static final PedestrianRouteExtent COMPLETE = new PedestrianRouteExtent(HierarchicalPedestrianSearch.MAX_ROUTE_SURFACES);
    public static final PedestrianRouteExtent MOVEMENT_SEGMENT = new PedestrianRouteExtent(TimedKnownRoute.MAX_SURFACES);

    public PedestrianRouteExtent {
        if (maximumSurfaces < 2 || maximumSurfaces > HierarchicalPedestrianSearch.MAX_ROUTE_SURFACES)
            throw new IllegalArgumentException("pedestrian detail horizon exceeds route bounds");
    }
    public PedestrianRouteResult limit(PedestrianRouteResult result) {
        if (result.status() != PedestrianRouteResult.Status.FOUND || result.route().size() <= maximumSurfaces) return result;
        return new PedestrianRouteResult(PedestrianRouteResult.Status.SEGMENT_READY,
                result.route().subList(0, maximumSurfaces), result.workUnits(), result.regions(), "KNOWN_ROUTE_SEGMENT");
    }
}
