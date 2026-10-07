package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Objects;

/** Exact volatile calculation address; geometry/version are knowledge, never execution authority. */
public record PedestrianRouteRequest(PedestrianRouteGeometry geometry, Object version,
                                     SurfaceAnchor start, SurfaceAnchor target) {
    public PedestrianRouteRequest {
        Objects.requireNonNull(geometry); Objects.requireNonNull(version);
        Objects.requireNonNull(start); Objects.requireNonNull(target);
    }
    public static PedestrianRouteRequest of(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return new PedestrianRouteRequest(geometry, geometry.version(), start, target);
    }
}
