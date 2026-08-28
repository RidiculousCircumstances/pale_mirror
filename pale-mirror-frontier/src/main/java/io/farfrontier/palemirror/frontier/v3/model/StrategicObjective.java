package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** A deterministic strategic decision, retained until its durable task graph reaches a terminal state. */
record StrategicObjective(SubjectId id, SubjectId ownerId, StrategicObjectiveKind kind,
                          Optional<InfectionCell> infectionTarget, int decisionOrdinal, StrategicObjectiveStatus status) {
    StrategicObjective {
        Objects.requireNonNull(id, "objective id"); Objects.requireNonNull(ownerId, "objective owner");
        Objects.requireNonNull(kind, "objective kind"); Objects.requireNonNull(infectionTarget, "infection target");
        Objects.requireNonNull(status, "objective status");
        if (decisionOrdinal <= 0) throw new IllegalArgumentException("objective decision ordinal must be positive");
        if (infectionTarget.isEmpty()) throw new IllegalArgumentException("initial strategic objective requires an infection target");
    }
    StrategicObjective withStatus(StrategicObjectiveStatus nextStatus) {
        return new StrategicObjective(id, ownerId, kind, infectionTarget, decisionOrdinal, nextStatus);
    }
}
