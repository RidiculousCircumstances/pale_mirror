package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Objects;

/** A COLD gravity checkpoint, not route arrival, service clearance or consumption. */
public record ResidentMealGrounded(ActorExecutionId execution, long atTick, BodyPosition from,
                                   SurfaceAnchor landing) implements FrontierPayload {
    public ResidentMealGrounded {
        Objects.requireNonNull(execution); Objects.requireNonNull(from); Objects.requireNonNull(landing);
        if (atTick < 0 || from.x() != landing.x() || from.z() != landing.z()
                || landing.standingBody().y() >= from.y())
            throw new IllegalArgumentException("meal grounding requires a vertical descent");
    }
    @Override public String type() { return "frontier.resident_meal_grounded"; }
}
