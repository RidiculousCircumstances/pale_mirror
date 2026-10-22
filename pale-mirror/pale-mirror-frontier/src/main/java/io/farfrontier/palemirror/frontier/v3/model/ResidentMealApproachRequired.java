package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.Objects;

/** A displaced, unbegun pickup needs a new approach, not a resource transfer. */
public record ResidentMealApproachRequired(ActorExecutionId execution, BodyPosition body) implements FrontierPayload {
    public ResidentMealApproachRequired {
        Objects.requireNonNull(execution); Objects.requireNonNull(body);
        if (execution.activityKind() != ActorActivityKind.MEAL)
            throw new IllegalArgumentException("meal approach requires exact meal execution");
    }
    @Override public String type() { return "frontier.resident_meal_approach_required"; }
}
