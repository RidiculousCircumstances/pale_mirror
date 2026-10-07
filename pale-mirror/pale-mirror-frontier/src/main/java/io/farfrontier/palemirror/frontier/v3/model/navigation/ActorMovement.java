package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;

/** One canonical actor goal owned by its caller; the route is a bounded execution checkpoint. */
public record ActorMovement(MovementOrder order, long issuedAtTick,
                            Optional<TimedKnownRoute> coldTravel,
                            ActorMovementContext context, ActorExecutionId executionId) {
    public ActorMovement {
        Objects.requireNonNull(order, "actor movement order");
        coldTravel = Objects.requireNonNull(coldTravel, "actor movement COLD travel");
        Objects.requireNonNull(context, "actor movement provider context");
        Objects.requireNonNull(executionId, "actor movement execution authority");
        // The context declares delegation; the registered provider and closure barrier validate it.
        // This common value knows neither a trade workflow nor any concrete group's business rules.
        var goalOwner = context.delegatedGoalOwner().orElse(executionId.activityOwnerId());
        if (!executionId.actorId().equals(order.actorId()) || !goalOwner.equals(order.ownerId()))
            throw new IllegalArgumentException("movement has foreign execution actor or owner");
        if (issuedAtTick < 0L || coldTravel.isPresent() && (
                !sameGoalIdentity(coldTravel.orElseThrow().order(), order)
                        || coldTravel.orElseThrow().departedAtTick() < issuedAtTick))
            throw new IllegalArgumentException("actor movement lacks its exact order and clock");
    }

    public ActorMovement(MovementOrder order, long issuedAtTick, ActorMovementContext context, ActorExecutionId executionId) {
        this(order, issuedAtTick, Optional.empty(), context, executionId);
    }

    public ActorMovement withColdTravel(TimedKnownRoute travel) {
        if (coldTravel.isPresent()) throw new IllegalStateException("actor movement already has COLD travel");
        return new ActorMovement(order, issuedAtTick, Optional.of(travel), context, executionId);
    }

    public ActorMovement withoutColdTravel() {
        return coldTravel.isEmpty() ? this : new ActorMovement(order, issuedAtTick, Optional.empty(), context, executionId);
    }

    public static MovementOrder segmentOrder(MovementOrder goal, io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor station) {
        return new MovementOrder(goal.ownerId(), goal.actorId(), goal.goalOrdinal(), goal.goalRevision(),
                java.util.List.of(station), goal.capability(), MovementOrder.ArrivalPolicy.EXACT_STATION);
    }

    private static boolean sameGoalIdentity(MovementOrder segment, MovementOrder goal) {
        return segment.ownerId().equals(goal.ownerId()) && segment.actorId().equals(goal.actorId())
                && segment.goalOrdinal() == goal.goalOrdinal()
                && segment.goalRevision() == goal.goalRevision()
                && segment.capability() == goal.capability();
    }
}
