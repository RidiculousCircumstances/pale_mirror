package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.Optional;

/** Shared approach calculation and receipt validation; callers alone declare the unfinished station. */
public final class StationApproachNavigation {
    private StationApproachNavigation() { }

    public static Optional<TraversalRejoin> plan(KnownPedestrianRouteKnowledge knowledge,
            StationApproachState retained, MovementOrder order) {
        requireOrder(retained, order);
        return retained.knownApproach(origin -> Optional.of(new TraversalRejoin(knowledge.plannedPath(origin, order), 0)));
    }

    /** Replay validates the selected remaining route, never its ranking or calculation readiness. */
    public static void requireSelected(KnownPedestrianRouteKnowledge knowledge, StationApproachState retained,
            MovementOrder order, TraversalRejoin selected) {
        requireOrder(retained, order);
        if (!order.arrivedAt(selected.target())
                || retained.approach().map(previous -> !previous.equals(selected)).orElseGet(() ->
                    retained.waitingOrigin().isEmpty() || selected.cursor() != 0
                        || !retained.waitingOrigin().orElseThrow().equals(selected.current())))
            throw new IllegalArgumentException("approach receipt differs from its retained origin, route or semantic target");
        knowledge.requireRoute(selected.path().subList(selected.cursor(), selected.path().size()));
    }

    private static void requireOrder(StationApproachState retained, MovementOrder order) {
        if (retained.revision() != order.goalRevision() || order.capability() != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("approach requires its declared current pedestrian goal revision");
    }
}
