package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Optional bounded local proof reuse. Current geometry validates every reverse
 * edge; a forward proof grants no reverse movement, access or actor authority. */
final class PedestrianLocalRouteReuse {
    private static final int MAX_SURFACES = 128;
    private PedestrianLocalRouteReuse() { }
    static Optional<PedestrianRouteResult> reverse(PedestrianRouteRequest source, PedestrianRouteResult result,
            PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) {
        if (!extent.equals(PedestrianRouteExtent.COMPLETE) || !source.extent().equals(PedestrianRouteExtent.COMPLETE)
                || result.status() != PedestrianRouteResult.Status.FOUND || result.route().size() > MAX_SURFACES
                || !source.start().equals(target) || !source.target().equals(start)
                || !source.geometry().bounds().equals(geometry.bounds())) return Optional.empty();
        if (source.geometry() != geometry) {
            var old = source.geometry().regionalRevision(); var current = geometry.regionalRevision();
            if (old.isEmpty() || current.isEmpty() || old.orElseThrow().baseline() != current.orElseThrow().baseline()
                    || !old.orElseThrow().geometryScope().equals(current.orElseThrow().geometryScope())) return Optional.empty();
        }
        var reversed = new ArrayList<>(result.route()); Collections.reverse(reversed);
        for (var surface : reversed) if (!geometry.clearanceKnown(surface)) return Optional.empty();
        if (!KnownPedestrianNavigation.supportsRoute(geometry, reversed)) return Optional.empty();
        return Optional.of(new PedestrianRouteResult(PedestrianRouteResult.Status.FOUND, reversed,
                2L * reversed.size(), result.regions(), "CURRENT_GEOMETRY_VALIDATED_REVERSE"));
    }
}
