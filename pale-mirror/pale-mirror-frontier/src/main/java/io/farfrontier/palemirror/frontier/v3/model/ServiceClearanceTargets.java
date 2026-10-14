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

    public record Selection(SurfaceAnchor surface,
            io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt access) {
        public Selection {
            java.util.Objects.requireNonNull(surface);
            java.util.Objects.requireNonNull(access);
            if (!access.route().getLast().equals(surface))
                throw new IllegalArgumentException("clearance selection has a foreign route endpoint");
        }
    }

    /** Bounded exit region, independent of any preferred parking destination or activity. */
    public static List<SurfaceAnchor> exits(ServiceAccessBoundary boundary,
            SurfaceAnchor start, KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> excluded) {
        // A centre/feet-derived HOT position can already be outside the boundary while
        // the body still straddles its raised sill. It is not a standable destination.
        // Keep pursuing the shared region until real supported exit observation wins.
        if (boundary.cleared(start.standingBody())
                && !excluded.contains(start)
                && knowledge.geometry().knowsSupport(start)
                && knowledge.traversable(List.of(start))) return List.of(start);
        return egressRegion(boundary, knowledge).stream().filter(candidate -> !excluded.contains(candidate))
                .sorted(java.util.Comparator
                .comparingInt((SurfaceAnchor surface) -> Math.abs(surface.x() - start.x())
                        + Math.abs(surface.z() - start.z()) + Math.abs(surface.y() - start.y()))
                .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z)
                .thenComparingInt(SurfaceAnchor::y)).toList();
    }

    /** Immediate escape capacity is passage space, never a waiting/parking reservation. */
    public static Set<SurfaceAnchor> egressRegion(ServiceAccessBoundary boundary,
            KnownPedestrianRouteKnowledge knowledge) {
        java.util.Set<SurfaceAnchor> candidates = new java.util.HashSet<>();
        for (SurfaceAnchor occupied : boundary.occupiedSurfaces()) {
            for (int[] step : List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1})) {
                for (SurfaceAnchor candidate : knowledge.surfacesAt(occupied.x() + step[0], occupied.z() + step[1])) {
                    if (knowledge.geometry().canTraverse(occupied, candidate) && boundary.cleared(candidate.standingBody()) && knowledge.traversable(List.of(candidate)))
                        candidates.add(candidate);
                }
            }
        }
        return Set.copyOf(candidates);
    }

    /** Exclusions reserve final destinations; they do not make walking actors terrain obstacles. */
    public static Optional<SurfaceAnchor> select(ServiceAccessPoint point, SubjectId actorId,
            KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> excluded) {
        return plannedSelection(point, actorId, knowledge, excluded).map(Selection::surface);
    }

    /** Queue readiness inspects retained evidence only. Missing evidence admits planning, not walking. */
    public static boolean mayPlan(ServiceAccessPoint point, KnownPedestrianRouteKnowledge knowledge,
                                  Set<SurfaceAnchor> excluded) {
        if (knownBlockedStation(point, knowledge)) return false;
        return point.waitingSurfaces().stream().filter(candidate -> admissible(point, candidate, excluded))
                .anyMatch(candidate -> knowledge.planningEvidence(point.station(), candidate)
                        .map(result -> result.status()
                            != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                        .orElse(true));
    }

    public static void requireSelected(ServiceAccessPoint point, KnownPedestrianRouteKnowledge knowledge,
            Set<SurfaceAnchor> excluded, Selection selected) {
        if (!admissible(point, selected.surface(), excluded)
                || !selected.access().route().getFirst().equals(point.station()))
            throw new IllegalArgumentException("clearance selection lost its declared service endpoint");
        knowledge.requireRoute(selected.access().route());
    }

    private static boolean admissible(ServiceAccessPoint point, SurfaceAnchor candidate, Set<SurfaceAnchor> excluded) {
        return point.waitingSurfaces().contains(candidate) && point.boundary().cleared(candidate.standingBody())
                && !excluded.contains(candidate);
    }

    /** Retained occupants wait beyond immediate egress, so later visitors can still approach. */
    public static List<SurfaceAnchor> waitingTargets(ServiceAccessPoint point, SurfaceAnchor origin, Set<SurfaceAnchor> excluded) {
        return point.waitingSurfaces().stream().filter(candidate -> admissible(point, candidate, excluded))
                .filter(candidate -> !point.egressSurfaces().contains(candidate))
                .sorted(java.util.Comparator.comparingInt((SurfaceAnchor candidate) -> Math.abs(candidate.x() - origin.x())
                        + Math.abs(candidate.y() - origin.y()) + Math.abs(candidate.z() - origin.z()))
                        .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
    }

    /** The producer serializes bounded candidate searches; unfinished work is never treated as NO_PATH. */
    public static Optional<Selection> plannedSelection(ServiceAccessPoint point, SubjectId actorId,
            KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> excluded) {
        if (knownBlockedStation(point, knowledge)) return Optional.empty();
        List<SurfaceAnchor> candidates = point.waitingSurfaces();
        if (candidates.isEmpty()) return Optional.empty();
        int first = Math.floorMod(actorId.value().hashCode(), candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            SurfaceAnchor candidate = candidates.get((first + offset) % candidates.size());
            if (!admissible(point, candidate, excluded)) continue;
            MovementOrder order = new MovementOrder(actorId, actorId, 0, 1L, List.of(candidate),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            try {
                var route = knowledge.plannedPath(point.station(), order);
                return Optional.of(new Selection(candidate,
                        new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt(route)));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                    throw unavailable;
                // Try another declared target; never invent support or force admission.
            }
        }
        return Optional.empty();
    }

    /** A known closed service origin cannot feed an executable exit. Unknown geometry
     * still enters ordinary planning; this check neither searches nor grants access. */
    private static boolean knownBlockedStation(ServiceAccessPoint point, KnownPedestrianRouteKnowledge knowledge) {
        return knowledge.geometry().knowsSupport(point.station()) && !knowledge.traversable(List.of(point.station()));
    }
}
