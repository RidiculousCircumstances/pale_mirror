package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Completes an eaten meal without changing the actor's retained physical body. */
public record ResidentMealHotReturned(SubjectId residentId, long ambientRevision) implements FrontierPayload {
    public ResidentMealHotReturned {
        Objects.requireNonNull(residentId, "meal resident");
        if (ambientRevision < 1) throw new IllegalArgumentException("meal return needs a HOT lease revision");
    }
    @Override public String type() { return "frontier.resident_meal_hot_returned"; }
}
