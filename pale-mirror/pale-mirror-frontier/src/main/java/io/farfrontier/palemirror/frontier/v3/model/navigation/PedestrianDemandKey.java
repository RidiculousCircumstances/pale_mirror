package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.Objects;

/** Derived calculation demand across compatible immutable views, never actor/arrival authority. */
public final class PedestrianDemandKey {
    private record Regional(WorldBounds bounds, Object scope, Object geometryScope) { }
    private record Exact(PedestrianRouteGeometry value) {
        @Override public int hashCode() { return System.identityHashCode(value); }
        @Override public boolean equals(Object other) { return other instanceof Exact exact && value == exact.value; }
    }
    private final Object scope;
    private final SurfaceAnchor start, target;
    private final PedestrianRouteExtent extent;
    private final int hash;
    private PedestrianDemandKey(PedestrianRouteRequest request) {
        scope = request.geometry().regionalRevision().<Object>map(revision ->
                new Regional(request.geometry().bounds(), revision.scope(), revision.geometryScope()))
                .orElseGet(() -> new Exact(request.geometry()));
        start = request.start(); target = request.target(); extent = request.extent();
        hash = Objects.hash(scope, start, target, extent);
    }
    public static PedestrianDemandKey of(PedestrianRouteRequest request) { return new PedestrianDemandKey(Objects.requireNonNull(request)); }
    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof PedestrianDemandKey key && hash == key.hash
                && scope.equals(key.scope) && start.equals(key.start) && target.equals(key.target) && extent.equals(key.extent);
    }
}
