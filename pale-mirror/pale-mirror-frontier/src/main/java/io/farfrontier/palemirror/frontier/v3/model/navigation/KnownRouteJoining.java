package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.KnownPedestrianRouteKnowledge;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Connect an off-itinerary body through the common navigator before following a retained shared route. */
public final class KnownRouteJoining {
    private KnownRouteJoining() { }

    public static List<SurfaceAnchor> path(KnownPedestrianRouteKnowledge knowledge, SurfaceAnchor start,
                                           MovementOrder order, List<SurfaceAnchor> itinerary, boolean segment) {
        if (order.legalStations().size() != 1)
            throw new IllegalArgumentException("shared itinerary joining requires one declared final station");
        int goal = itinerary.indexOf(order.legalStations().getFirst());
        if (goal < 0 || itinerary.subList(0, goal + 1).contains(start))
            return segment ? knowledge.plannedSegment(start, order, itinerary) : knowledge.plannedPath(start, order, itinerary);
        var prefix = itinerary.subList(0, goal + 1);
        // This is a bounded set of joining targets, not a second pathfinder or a new movement order.
        var stations = java.util.stream.IntStream.range(0, prefix.size()).boxed().sorted(Comparator
                .comparingLong((Integer index) -> distanceSquared(start, prefix.get(index)))
                .thenComparingInt(Integer::intValue)).map(prefix::get).distinct()
                .limit(MovementOrder.MAX_LEGAL_STATIONS).toList();
        var joining = new MovementOrder(order.ownerId(), order.actorId(), order.goalOrdinal(), order.goalRevision(),
                stations, order.capability(), MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
        var approach = knowledge.plannedPath(start, joining);
        int join = prefix.indexOf(approach.getLast());
        if (join < 0) throw new IllegalArgumentException("route joining did not reach a declared itinerary station");
        // First contact suffices: do not walk past the itinerary and then double back along it.
        var routeStations = Set.copyOf(prefix);
        for (int index = 1; index < approach.size(); index++) {
            if (!routeStations.contains(approach.get(index))) continue;
            approach = List.copyOf(approach.subList(0, index + 1));
            break;
        }
        // The common navigator validates the retained tail against current geometry and
        // plans around changed blocks. A stale hint must never become an invariant failure.
        var tail = segment ? knowledge.plannedSegment(approach.getLast(), order, itinerary)
                : knowledge.plannedPath(approach.getLast(), order, itinerary);
        var path = new ArrayList<>(approach);
        path.addAll(tail.subList(1, tail.size()));
        var bounded = List.copyOf(path.subList(0, Math.min(path.size(), TimedKnownRoute.MAX_SURFACES)));
        knowledge.requireRoute(bounded);
        return bounded;
    }

    private static long distanceSquared(SurfaceAnchor from, SurfaceAnchor to) {
        long x = (long) from.x() - to.x(), y = (long) from.y() - to.y(), z = (long) from.z() - to.z();
        return x * x + y * y + z * z;
    }
}
