package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** One COLD-only stage receipt for a retained meal, never evidence of a HOT physical effect. */
public record ResidentMealColdStep(SubjectId residentId, ResidentMeal.Phase expectedPhase,
                                   long atTick, Optional<SurfaceAnchor> nextSurface) implements FrontierPayload {
    public ResidentMealColdStep {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(expectedPhase, "meal predecessor phase");
        nextSurface = Objects.requireNonNull(nextSurface, "optional next meal surface");
        if (atTick < 0) throw new IllegalArgumentException("meal step tick must be non-negative");
        if (expectedPhase != ResidentMeal.Phase.MOVE && expectedPhase != ResidentMeal.Phase.RETURN
                && expectedPhase != ResidentMeal.Phase.CLEAR_ACCESS
                && nextSurface.isPresent())
            throw new IllegalArgumentException("only a movement-stage meal can advance its body");
    }

    public ResidentMealColdStep(SubjectId residentId, ResidentMeal.Phase expectedPhase, long atTick) {
        this(residentId, expectedPhase, atTick, Optional.empty());
    }

    @Override public String type() { return "frontier.resident_meal_cold_step"; }
}
