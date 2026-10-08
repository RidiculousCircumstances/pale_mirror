package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Retains just the causal HOT facts of an accepted COLD route start for deterministic replay. */
public record ActorMovementColdRouteStarted(ActorMovementColdAdvanced advance,
                                           MovementPositionSnapshot positions) implements FrontierPayload {
    public ActorMovementColdRouteStarted {
        Objects.requireNonNull(advance); Objects.requireNonNull(positions);
        if (advance.plannedRoute().isEmpty() || advance.atTick() != positions.atTick())
            throw new IllegalArgumentException("observed route start requires one route and its exact instant");
    }
    @Override public String type() { return "frontier.actor_movement_cold_route_started"; }
}
