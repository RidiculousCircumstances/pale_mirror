package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Owner-local replacement of an unbegun output handoff, never a transfer of goods. */
public record BakeryStorageRetargeted(SubjectId jobId, BakeryWorkState.Phase phase,
                                      SubjectId previousContainer, SubjectId destinationContainer) implements FrontierPayload {
    public BakeryStorageRetargeted {
        Objects.requireNonNull(jobId); Objects.requireNonNull(phase);
        Objects.requireNonNull(previousContainer); Objects.requireNonNull(destinationContainer);
        if (phase == BakeryWorkState.Phase.DELIVERED || previousContainer.equals(destinationContainer))
            throw new IllegalArgumentException("bakery storage replacement needs a different uncompleted destination");
    }
    @Override public String type() { return "frontier.bakery_storage_retargeted"; }
}
