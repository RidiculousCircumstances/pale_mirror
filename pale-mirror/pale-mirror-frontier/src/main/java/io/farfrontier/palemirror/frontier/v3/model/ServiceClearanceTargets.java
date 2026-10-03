package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Geometry-owned selection of a reachable free target outside a declared service passage. */
public final class ServiceClearanceTargets {
    private ServiceClearanceTargets() { }

    /** Bounded exit region, independent of any preferred parking destination or activity. */
    public static List<SurfaceAnchor> exits(ServiceAccessBoundary boundary,
            SurfaceAnchor start, KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> excluded) {
        if (boundary.cleared(start.standingBody())) return List.of(start);
        java.util.Set<SurfaceAnchor> candidates = new java.util.HashSet<>();
        for (SurfaceAnchor occupied : boundary.occupiedSurfaces()) {
            for (int[] step : List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1})) {
                SurfaceAnchor candidate = knowledge.supportAt(occupied.x() + step[0], occupied.z() + step[1]);
                if (boundary.cleared(candidate.standingBody()) && !excluded.contains(candidate)) candidates.add(candidate);
            }
        }
        return candidates.stream().sorted(java.util.Comparator
                .comparingInt((SurfaceAnchor surface) -> Math.abs(surface.x() - start.x())
                        + Math.abs(surface.z() - start.z()) + Math.abs(surface.y() - start.y()))
                .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z)
                .thenComparingInt(SurfaceAnchor::y)).toList();
    }

    /** Exclusions reserve final destinations; they do not make walking actors terrain obstacles. */
    public static Optional<SurfaceAnchor> select(ServiceAccessPoint point, SubjectId actorId,
            KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> excluded) {
        List<SurfaceAnchor> candidates = point.waitingSurfaces();
        if (candidates.isEmpty()) return Optional.empty();
        int first = Math.floorMod(actorId.value().hashCode(), candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            SurfaceAnchor candidate = candidates.get((first + offset) % candidates.size());
            if (!point.boundary().cleared(candidate.standingBody()) || excluded.contains(candidate)) continue;
            MovementOrder order = new MovementOrder(actorId, actorId, 0, 1L, List.of(candidate),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            try {
                knowledge.path(point.station(), order);
                return Optional.of(candidate);
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                // Try another declared target; never invent support or force admission.
            }
        }
        return Optional.empty();
    }
}
