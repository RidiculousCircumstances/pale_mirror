package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Calculation port. Its readiness/result does not transfer execution or award arrival. */
@FunctionalInterface
public interface PedestrianRoutePlanner {
    PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target);
}
