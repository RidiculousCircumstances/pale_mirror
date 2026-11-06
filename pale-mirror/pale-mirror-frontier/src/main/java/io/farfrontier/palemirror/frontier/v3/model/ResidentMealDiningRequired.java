package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.Objects;

/** A displaced eater retains its portion and must return to its declared dining area. */
public record ResidentMealDiningRequired(ActorExecutionId execution, BodyPosition body) implements FrontierPayload {
    public ResidentMealDiningRequired {
        Objects.requireNonNull(execution); Objects.requireNonNull(body);
        if (execution.activityKind() != ActorActivityKind.MEAL)
            throw new IllegalArgumentException("dining return requires exact meal execution");
    }
    @Override public String type() { return "frontier.resident_meal_dining_required"; }
}
