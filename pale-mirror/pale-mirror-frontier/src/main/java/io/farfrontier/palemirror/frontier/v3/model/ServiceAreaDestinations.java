package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Geometry policy for resting outside all temporary service buffers, independent of the service's purpose. */
public final class ServiceAreaDestinations {
    private static final int MAX_SEARCH_RADIUS = 32; // Bounded search, never an expanding whole-world scan.
    private ServiceAreaDestinations() { }

    public static boolean temporary(ServiceAccessPoint point, SurfaceAnchor surface) {
        return point.boundary().occupied(surface.standingBody()) || point.waitingSurfaces().contains(surface)
                || point.egressSurfaces().contains(surface);
    }

    public static Optional<SurfaceAnchor> select(List<ServiceAccessPoint> points, SubjectId actorId,
            SurfaceAnchor start, KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> excluded) {
        return select(points, actorId, start, knowledge, excluded, surface -> true);
    }

    public static Optional<SurfaceAnchor> select(List<ServiceAccessPoint> points, SubjectId actorId,
            SurfaceAnchor start, KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> excluded,
            java.util.function.Predicate<SurfaceAnchor> available) {
        for (int distance = 1; distance <= MAX_SEARCH_RADIUS; distance++) {
            // Manhattan rings choose a nearest reachable free column, including non-flat support.
            for (int dx = -distance; dx <= distance; dx++) {
                int dz = distance - Math.abs(dx);
                for (int sign : dz == 0 ? new int[] {1} : new int[] {1, -1}) {
                    SurfaceAnchor candidate = knowledge.supportAt(start.x() + dx, start.z() + sign * dz);
                    if (excluded.contains(candidate) || points.stream().anyMatch(point -> temporary(point, candidate))
                            || !available.test(candidate)) continue;
                    MovementOrder order = new MovementOrder(actorId, actorId, 0, 1L, List.of(candidate),
                            TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
                    try {
                        List<SurfaceAnchor> route = knowledge.path(start, order);
                        if (points.stream().allMatch(point -> point.boundary().allowsWaitingRoute(route)))
                            return Optional.of(candidate);
                    } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                        // Unsupported/blocked/unknown columns cannot become an idle destination.
                    }
                }
            }
        }
        return Optional.empty();
    }
}
