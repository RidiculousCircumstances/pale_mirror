package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** WAL fence written before a HOT chest/hand meal effect is attempted. */
public record ResidentMealHotEffectPrepared(SubjectId residentId,
                                            ResidentMealPhysicalStep step) implements FrontierPayload {
    public ResidentMealHotEffectPrepared {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(step, "meal physical step");
    }
    @Override public String type() { return "frontier.resident_meal_hot_effect_prepared"; }
}
