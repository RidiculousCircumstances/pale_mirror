package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

import java.util.List;
import java.util.Objects;

/**
 * Retained COLD travel between causal stations, not a per-support work cursor.
 * The legal route and clock reconstruct an as-of supported body without writing
 * one canonical event for every traversed support.
 */
public record TimedKnownRoute(MovementOrder order, List<SurfaceAnchor> route,
                              long departedAtTick, long ticksPerEdge, long authorityEpoch) {
    public static final int MAX_SURFACES = 4_096;

    public TimedKnownRoute {
        Objects.requireNonNull(order, "movement order");
        route = List.copyOf(Objects.requireNonNull(route, "known route"));
        if (route.isEmpty() || route.size() > MAX_SURFACES || route.stream().anyMatch(Objects::isNull)
                || !order.arrivedAt(route.getLast()) || departedAtTick < 0L
                || ticksPerEdge <= 0L || authorityEpoch < 1L)
            throw new IllegalArgumentException("timed route needs a bounded legal goal and clock");
        for (int index = 1; index < route.size(); index++) {
            SurfaceAnchor previous = route.get(index - 1), next = route.get(index);
            int horizontal = Math.abs(next.x() - previous.x()) + Math.abs(next.z() - previous.z());
            if (horizontal != 1 || Math.abs(next.y() - previous.y()) > 1)
                throw new IllegalArgumentException("timed route contains a nonadjacent pedestrian edge");
        }
        Math.addExact(departedAtTick, Math.multiplyExact(ticksPerEdge, route.size() - 1L));
    }

    public long arrivalTick() {
        return Math.addExact(departedAtTick, Math.multiplyExact(ticksPerEdge, route.size() - 1L));
    }

    /** A whole supported cell; the HOT provider owns any sub-cell physical motion. */
    public SurfaceAnchor surfaceAt(long tick) {
        return route.get(indexAt(tick));
    }

    public int indexAt(long tick) {
        if (tick < departedAtTick) throw new IllegalArgumentException("travel cannot be queried before departure");
        long elapsedEdges = (tick - departedAtTick) / ticksPerEdge;
        return (int) Math.min(route.size() - 1L, elapsedEdges);
    }

    public boolean arrivedBy(long tick) { return tick >= arrivalTick(); }
}
