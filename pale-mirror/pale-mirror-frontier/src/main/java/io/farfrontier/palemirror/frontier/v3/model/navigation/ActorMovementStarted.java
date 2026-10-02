package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Explicitly declared movement order; no activity, route, or completion inferred from actor identity. */
public record ActorMovementStarted(ActorMovement movement) implements FrontierPayload {
    public ActorMovementStarted {
        Objects.requireNonNull(movement, "started movement");
        if (movement.coldTravel().isPresent()) throw new IllegalArgumentException("new movement cannot already have travelled");
    }
    @Override public String type() { return "frontier.actor_movement_started"; }
}
