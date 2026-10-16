package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.Objects;

/** The meal owner reacts to an already observed re-entry; this is not a position writer or a food receipt. */
public record ResidentMealClearanceRequired(ActorExecutionId execution, BodyPosition body) implements FrontierPayload {
    public ResidentMealClearanceRequired {
        Objects.requireNonNull(execution); Objects.requireNonNull(body);
        if (execution.activityKind() != ActorActivityKind.MEAL)
            throw new IllegalArgumentException("meal clearance requires exact meal execution");
    }
    @Override public String type() { return "frontier.resident_meal_clearance_required"; }
}
