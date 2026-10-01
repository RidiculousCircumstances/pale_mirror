package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Ephemeral owner evidence, bound to one immutable state; never a second durable activity. */
public record ActivityExecutionCheckpoint(FrontierWorldState basis, HumanAssignment assignment,
                                          ResidentWorkYield.Status status) {
    public ActivityExecutionCheckpoint {
        Objects.requireNonNull(basis, "checkpoint state");
        Objects.requireNonNull(assignment, "checkpoint assignment");
        Objects.requireNonNull(status, "checkpoint status");
    }

    public ResidentWorkYield validate(FrontierWorldState current, HumanAssignment expected) {
        if (basis != current || !assignment.equals(expected))
            throw new IllegalArgumentException("checkpoint evidence has a stale state or foreign assignment");
        return new ResidentWorkYield(expected.residentId(), expected, status);
    }
}
