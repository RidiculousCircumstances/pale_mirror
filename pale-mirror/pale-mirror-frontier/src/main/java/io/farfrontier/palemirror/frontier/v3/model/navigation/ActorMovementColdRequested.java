package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Objects;

/** Trusted observation ingress for a COLD member whose constraints depend on live bodies. */
public record ActorMovementColdRequested(ActorExecutionId execution, long goalRevision,
                                         MovementPositionSnapshot positions) implements FrontierPayload {
    public ActorMovementColdRequested {
        Objects.requireNonNull(execution); Objects.requireNonNull(positions);
        if (goalRevision < 1) throw new IllegalArgumentException("invalid movement observation goal");
    }
    @Override public String type() { return "frontier.actor_movement_cold_requested"; }
}
