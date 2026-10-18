package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Objects;

/** Exact COLD spatial settlement; preserves the current order and grants no semantic arrival. */
public record ActorMovementGrounded(ActorExecutionId execution, long goalRevision, long atTick,
                                    BodyPosition from, SurfaceAnchor landing) implements FrontierPayload {
    public ActorMovementGrounded {
        Objects.requireNonNull(execution); Objects.requireNonNull(from); Objects.requireNonNull(landing);
        if (goalRevision < 1 || atTick < 0 || from.x() != landing.x() || from.z() != landing.z()
                || from.y() <= landing.y() + 1L) throw new IllegalArgumentException("invalid vertical movement settlement");
    }
    @Override public String type() { return "frontier.actor_movement_grounded"; }
}
