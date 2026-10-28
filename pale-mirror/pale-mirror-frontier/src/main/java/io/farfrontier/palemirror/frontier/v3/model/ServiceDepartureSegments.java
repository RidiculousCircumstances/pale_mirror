package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** A service footprint is released at its actual exit, independently of the issuing activity. */
public final class ServiceDepartureSegments {
    private ServiceDepartureSegments() { }

    /** The owner has already validated the whole route; movement times its earliest causal exit. */
    public static List<SurfaceAnchor> bounded(FrontierWorldState state, List<SurfaceAnchor> route) {
        if (route.isEmpty()) throw new IllegalArgumentException("service departure requires a current surface");
        var start = route.getFirst();
        int end = route.size();
        for (var point : ServiceBoundaryComposition.nearbyDeclarations(state, new TerrainColumn(start.x(), start.z()))) {
            int exit = point.boundary().firstExit(route);
            if (exit >= 0) end = Math.min(end, exit + 1);
        }
        return end == route.size() ? route : List.copyOf(route.subList(0, end));
    }
}
