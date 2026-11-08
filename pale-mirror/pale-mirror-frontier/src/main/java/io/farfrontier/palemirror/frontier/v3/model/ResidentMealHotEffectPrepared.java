package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** WAL fence written before a HOT chest/hand meal effect is attempted. */
public record ResidentMealHotEffectPrepared(SubjectId residentId,
                                            ResidentMealPhysicalStep step,
                                            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId> seatBody) implements FrontierPayload {
    public ResidentMealHotEffectPrepared(SubjectId residentId, ResidentMealPhysicalStep step) {
        this(residentId, step, java.util.Optional.empty());
    }
    public ResidentMealHotEffectPrepared {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(step, "meal physical step");
        Objects.requireNonNull(seatBody, "declared joint seat exit body");
        if (seatBody.filter(body -> !body.execution().equals(step.executionId()) || step.phase() != ResidentMeal.Phase.CONSUME).isPresent())
            throw new IllegalArgumentException("meal preparation has a foreign seat interaction body");
    }
    @Override public String type() { return "frontier.resident_meal_hot_effect_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
