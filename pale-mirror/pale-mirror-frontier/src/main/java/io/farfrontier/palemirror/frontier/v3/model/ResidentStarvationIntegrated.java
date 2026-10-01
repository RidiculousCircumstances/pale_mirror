package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Explicit health fact before its owning nutrition interval is retired. */
public record ResidentStarvationIntegrated(SubjectId residentId, long previousNutritionTick, long atTick,
        ResidentStarvation previous, ResidentStarvation next) implements FrontierPayload {
    public ResidentStarvationIntegrated {
        Objects.requireNonNull(residentId, "starvation resident");
        Objects.requireNonNull(previous, "previous starvation condition");
        Objects.requireNonNull(next, "next starvation condition");
        if (previousNutritionTick < 0 || atTick < previousNutritionTick || previous.equals(next))
            throw new IllegalArgumentException("starvation fact requires a changed exact interval");
    }
    @Override public String type() { return "frontier.resident_starvation_integrated"; }
}
