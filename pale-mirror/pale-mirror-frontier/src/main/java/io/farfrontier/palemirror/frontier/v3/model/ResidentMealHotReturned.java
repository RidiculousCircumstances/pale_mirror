package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Witnesses that the eaten meal's exact HOT actor reached its retained return target. */
public record ResidentMealHotReturned(SubjectId residentId, long ambientRevision,
                                      BodyPosition observedBody) implements FrontierPayload {
    public ResidentMealHotReturned {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(observedBody, "meal clearing body");
        if (ambientRevision < 1) throw new IllegalArgumentException("meal return needs a HOT lease revision");
    }
    @Override public String type() { return "frontier.resident_meal_hot_returned"; }
}
