package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Exactly one selected activity; the work assignment remains owned by its original job. */
public record ResidentActivityChoice(SubjectId residentId, Kind kind,
                                     Optional<SubjectId> retainedWorkOwner,
                                     Optional<Wait> pending) {
    public enum Kind { WORK, EAT, IDLE }
    public enum Wait { SAFE_CHECKPOINT, SOURCE_UNAVAILABLE, ROUTE_BLOCKED, HAND_OCCUPIED }

    public ResidentActivityChoice {
        Objects.requireNonNull(residentId, "activity resident");
        Objects.requireNonNull(kind, "activity kind");
        retainedWorkOwner = Objects.requireNonNull(retainedWorkOwner, "retained work owner");
        pending = Objects.requireNonNull(pending, "activity wait reason");
        if (kind == Kind.WORK && retainedWorkOwner.isEmpty()) {
            throw new IllegalArgumentException("work activity requires its exact retained assignment");
        }
        if (pending.isPresent() && kind != Kind.WORK) {
            throw new IllegalArgumentException("pending yield must retain its current work");
        }
    }
}
