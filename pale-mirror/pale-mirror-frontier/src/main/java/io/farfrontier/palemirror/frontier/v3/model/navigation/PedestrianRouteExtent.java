package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Requested detail horizon, not a different destination or permission to award arrival. */
public record PedestrianRouteExtent(int maximumSurfaces, Detail detail) {
    public enum Detail { CELLS, REGIONAL_ESTIMATE }
    public static final PedestrianRouteExtent ESTIMATE = new PedestrianRouteExtent(2, Detail.REGIONAL_ESTIMATE);
    public static final PedestrianRouteExtent COMPLETE = new PedestrianRouteExtent(HierarchicalPedestrianSearch.MAX_ROUTE_SURFACES);
    public static final PedestrianRouteExtent MOVEMENT_SEGMENT = new PedestrianRouteExtent(TimedKnownRoute.MAX_SURFACES);

    public PedestrianRouteExtent {
        java.util.Objects.requireNonNull(detail);
        if (maximumSurfaces < 2 || maximumSurfaces > HierarchicalPedestrianSearch.MAX_ROUTE_SURFACES)
            throw new IllegalArgumentException("pedestrian detail horizon exceeds route bounds");
    }
    public PedestrianRouteExtent(int maximumSurfaces) { this(maximumSurfaces, Detail.CELLS); }
    public boolean estimateOnly() { return detail == Detail.REGIONAL_ESTIMATE; }
    public PedestrianRouteResult limit(PedestrianRouteResult result) {
        if (estimateOnly() && result.status() == PedestrianRouteResult.Status.FOUND) {
            var route = result.route();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.ESTIMATED, java.util.List.of(),
                    result.workUnits(), result.regions(), "ESTIMATE_FROM_KNOWN_ROUTE", java.util.Optional.of(
                    new PedestrianRouteEstimate(route.getFirst(), route.getLast(), route.size() - 1L)));
        }
        if (result.status() != PedestrianRouteResult.Status.FOUND || result.route().size() <= maximumSurfaces) return result;
        return new PedestrianRouteResult(PedestrianRouteResult.Status.SEGMENT_READY,
                result.route().subList(0, maximumSurfaces), result.workUnits(), result.regions(), "KNOWN_ROUTE_SEGMENT");
    }
}
