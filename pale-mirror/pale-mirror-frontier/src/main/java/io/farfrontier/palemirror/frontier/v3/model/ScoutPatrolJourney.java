package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.List;
import java.util.Objects;

/** Scout-owned semantic destination. Actual position belongs only to ActorLocation. */
public record ScoutPatrolJourney(ActorExecutionId executionId, long goalRevision, SurfaceAnchor target) {
    public ScoutPatrolJourney {
        new ScoutPatrolStarted(executionId);
        if (goalRevision < 1L) throw new IllegalArgumentException("scout goal revision must be positive");
        Objects.requireNonNull(target, "scout destination");
    }
    public MovementOrder order() {
        return new MovementOrder(executionId.activityOwnerId(), executionId.actorId(), goalRevision - 1L,
                goalRevision, List.of(target), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public ScoutPatrolJourney next(SurfaceAnchor destination) {
        return new ScoutPatrolJourney(executionId, Math.incrementExact(goalRevision), destination);
    }
}
