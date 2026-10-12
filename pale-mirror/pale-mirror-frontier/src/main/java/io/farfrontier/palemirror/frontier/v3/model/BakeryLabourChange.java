package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Optional;

/** Retained predecessor and successor of labour, never permission to transform station stock. */
public record BakeryLabourChange(Optional<WorkProgress> previous, WorkProgress next) {
    public BakeryLabourChange {
        Objects.requireNonNull(previous); Objects.requireNonNull(next);
        if (previous.equals(Optional.of(next))) throw new IllegalArgumentException("unchanged bakery labour");
    }
    public long atTick() { return next.evaluatedAtTick(); }
}
