package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed first exit from a shared service boundary; the resident's meal return continues. */
public record ResidentMealHotAccessCleared(SubjectId residentId, long ambientRevision,
                                           BodyPosition observedBody) implements FrontierPayload {
    public ResidentMealHotAccessCleared {
        Objects.requireNonNull(residentId, "service resident");
        Objects.requireNonNull(observedBody, "service exit body");
        if (ambientRevision < 1) throw new IllegalArgumentException("service exit needs a HOT lease revision");
    }

    @Override public String type() { return "frontier.resident_meal_hot_access_cleared"; }
}
