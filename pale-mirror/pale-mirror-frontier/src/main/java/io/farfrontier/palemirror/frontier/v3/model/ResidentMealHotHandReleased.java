package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;

/** Unbinds the observed HOT hand before the same resident returns to COLD ownership. */
public record ResidentMealHotHandReleased(SubjectId residentId, long ambientRevision,
                                          FungiblePhysicalObservation.Stack observedHand, ActorExecutionId executionId) implements FrontierPayload {
    public ResidentMealHotHandReleased {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(executionId, "meal execution authority");
        if (!residentId.equals(executionId.actorId())) throw new IllegalArgumentException("meal hand has foreign execution actor");
        Objects.requireNonNull(observedHand, "resident meal hand");
        if (ambientRevision < 1) throw new IllegalArgumentException("meal hand needs a current ambient revision");
    }
    @Override public String type() { return "frontier.resident_meal_hot_hand_released"; }
}
