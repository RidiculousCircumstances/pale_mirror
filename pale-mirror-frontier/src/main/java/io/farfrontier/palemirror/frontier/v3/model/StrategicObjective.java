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
        if (kind != StrategicObjectiveKind.HIVE_GROW_ORGANISM && kind != StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD && kind != StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE && infectionTarget.isEmpty()) {
            throw new IllegalArgumentException("infection strategic objective requires an infection target");
        }
        if ((kind == StrategicObjectiveKind.HIVE_GROW_ORGANISM || kind == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD || kind == StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE) && infectionTarget.isPresent()) {
            throw new IllegalArgumentException("hive growth objective cannot carry an infection target");
        }
    }
    StrategicObjective withStatus(StrategicObjectiveStatus nextStatus) {
        return new StrategicObjective(id, ownerId, kind, infectionTarget, decisionOrdinal, nextStatus);
    }
}
