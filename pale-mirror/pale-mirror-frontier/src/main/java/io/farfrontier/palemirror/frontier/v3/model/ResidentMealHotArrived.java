package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Physical observation of the retained resident at this meal's depot service station. */
public record ResidentMealHotArrived(SubjectId residentId, long ambientRevision,
                                     BodyPosition observedBody) implements FrontierPayload {
    public ResidentMealHotArrived {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(observedBody, "observed meal body");
        if (ambientRevision < 1) throw new IllegalArgumentException("meal arrival has no HOT lease revision");
    }

    @Override public String type() { return "frontier.resident_meal_hot_arrived"; }
}
