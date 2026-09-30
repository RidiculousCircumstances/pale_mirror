package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.Objects;
import java.util.Optional;

/** One canonical actor goal owned by its caller; the route is a bounded execution checkpoint. */
public record ActorMovement(MovementOrder order, long issuedAtTick,
                            Optional<TimedKnownRoute> coldTravel,
                            ActorMovementContext context) {
    public ActorMovement {
        Objects.requireNonNull(order, "actor movement order");
        coldTravel = Objects.requireNonNull(coldTravel, "actor movement COLD travel");
        Objects.requireNonNull(context, "actor movement provider context");
        if (issuedAtTick < 0L || coldTravel.isPresent() && (
                !sameGoalIdentity(coldTravel.orElseThrow().order(), order)
                        || coldTravel.orElseThrow().departedAtTick() < issuedAtTick))
            throw new IllegalArgumentException("actor movement lacks its exact order and clock");
    }

    public ActorMovement(MovementOrder order, long issuedAtTick, ActorMovementContext context) {
        this(order, issuedAtTick, Optional.empty(), context);
    }

    public ActorMovement withColdTravel(TimedKnownRoute travel) {
        if (coldTravel.isPresent()) throw new IllegalStateException("actor movement already has COLD travel");
        return new ActorMovement(order, issuedAtTick, Optional.of(travel), context);
    }

    public ActorMovement withoutColdTravel() {
        return coldTravel.isEmpty() ? this : new ActorMovement(order, issuedAtTick, Optional.empty(), context);
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
