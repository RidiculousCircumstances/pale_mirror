package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Calculation port. Its readiness/result does not transfer execution or award arrival. */
@FunctionalInterface
public interface PedestrianRoutePlanner {
    /** Detached read capability. Live queues override this with an immutable retained-result view. */
    default PedestrianRoutePlanner readView() { return this; }
    /** Retire only a calculation acquired and abandoned by its current selection scope. */
    default void discardUnobserved(PedestrianRouteRequest request) { }
    /** Conservative ownership inquiry, independent of whether a worker result is currently reusable. */
    default boolean hasCalculation(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                   PedestrianRouteExtent extent) { return true; }
    PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target);
    default PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                        PedestrianRouteExtent extent, PedestrianPlanningService service) {
        java.util.Objects.requireNonNull(service);
        return query(geometry, start, target, extent);
    }
    default PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                        PedestrianRouteExtent extent) {
        return extent.limit(query(geometry, start, target));
    }
    /** Read-only retained evidence; diagnostics must never enqueue calculation. */
    default java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return java.util.Optional.empty();
    }
    default java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                                          PedestrianRouteExtent extent) {
        return peek(geometry, start, target).map(extent::limit);
    }
}
