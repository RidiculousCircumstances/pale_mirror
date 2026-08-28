package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact physical receipt that one named canonical stack was removed from its active container slot. */
public record ExactItemConsumedObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId itemId,
                                           int countBefore, int countAfter) implements PhysicalEffectObservation {
    public ExactItemConsumedObservation {
        Objects.requireNonNull(id, "observation id"); Objects.requireNonNull(intentId, "intent id"); Objects.requireNonNull(itemId, "item id");
        if (countBefore < 1 || countBefore > 64 || countAfter != 0) {
            throw new IllegalArgumentException("exact consumption receipt must remove one complete canonical stack");
        }
    }
}
