package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Objects;

/** Exact volatile calculation address; geometry/version are knowledge, never execution authority. */
public record PedestrianRouteRequest(PedestrianRouteGeometry geometry, Object version,
                                     SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) {
    public PedestrianRouteRequest {
        Objects.requireNonNull(geometry); Objects.requireNonNull(version);
        Objects.requireNonNull(start); Objects.requireNonNull(target);
        Objects.requireNonNull(extent);
    }
    public PedestrianRouteRequest(PedestrianRouteGeometry geometry, Object version, SurfaceAnchor start, SurfaceAnchor target) {
        this(geometry, version, start, target, PedestrianRouteExtent.COMPLETE);
    }
    public static PedestrianRouteRequest of(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return new PedestrianRouteRequest(geometry, geometry.version(), start, target);
    }
    public static PedestrianRouteRequest of(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                            PedestrianRouteExtent extent) {
        return new PedestrianRouteRequest(geometry, geometry.version(), start, target, extent);
    }
    /** Same demand across immutable geometry refreshes, never across task/profile scopes. */
    public boolean sameDemand(PedestrianRouteRequest other) {
        if (!start.equals(other.start) || !target.equals(other.target) || !extent.equals(other.extent)) return false;
        if (geometry == other.geometry) return true;
        var left = geometry.regionalRevision(); var right = other.geometry.regionalRevision();
        return left.isPresent() && right.isPresent() && geometry.bounds().equals(other.geometry.bounds())
                && left.orElseThrow().scope().equals(right.orElseThrow().scope())
                && left.orElseThrow().geometryScope().equals(right.orElseThrow().geometryScope());
    }
}
