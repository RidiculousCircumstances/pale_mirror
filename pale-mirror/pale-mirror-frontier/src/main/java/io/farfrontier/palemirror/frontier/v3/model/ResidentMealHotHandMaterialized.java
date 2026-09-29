package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Binds one COLD-held bread portion to a newly admitted exact HOT offhand. */
public record ResidentMealHotHandMaterialized(SubjectId residentId, long ambientRevision,
                                              FungiblePhysicalObservation.Stack observedHand) implements FrontierPayload {
    public ResidentMealHotHandMaterialized {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(observedHand, "resident meal hand");
        if (ambientRevision < 1) throw new IllegalArgumentException("meal hand needs a current ambient revision");
    }
    @Override public String type() { return "frontier.resident_meal_hot_hand_materialized"; }
}
