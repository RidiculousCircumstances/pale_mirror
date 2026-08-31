package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** A deterministic strategic decision, retained until its durable task graph reaches a terminal state. */
public record StrategicObjective(SubjectId id, SubjectId ownerId, StrategicObjectiveKind kind,
                          Optional<InfectionCell> infectionTarget, Optional<SubjectId> resourceSiteTarget,
                          int decisionOrdinal, StrategicObjectiveStatus status) {
    public StrategicObjective {
        Objects.requireNonNull(id, "objective id"); Objects.requireNonNull(ownerId, "objective owner");
        Objects.requireNonNull(kind, "objective kind"); Objects.requireNonNull(infectionTarget, "infection target");
        Objects.requireNonNull(resourceSiteTarget, "objective resource-site target");
        Objects.requireNonNull(status, "objective status");
        if (decisionOrdinal <= 0) throw new IllegalArgumentException("objective decision ordinal must be positive");
        if (kind != StrategicObjectiveKind.HIVE_GROW_ORGANISM && kind != StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION
                && kind != StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT
                && kind != StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD && kind != StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE
                && kind != StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE && kind != StrategicObjectiveKind.SETTLEMENT_CONSTRUCT_ROUTE_BYPASS
                && kind != StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE && infectionTarget.isEmpty()) {
            throw new IllegalArgumentException("infection strategic objective requires an infection target");
        }
        if ((kind == StrategicObjectiveKind.HIVE_GROW_ORGANISM || kind == StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION
                || kind == StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT
                || kind == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD || kind == StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE
                || kind == StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE || kind == StrategicObjectiveKind.SETTLEMENT_CONSTRUCT_ROUTE_BYPASS
                || kind == StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE) && infectionTarget.isPresent()) {
            throw new IllegalArgumentException("hive growth objective cannot carry an infection target");
        }
        if (kind == StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE != resourceSiteTarget.isPresent()) {
            throw new IllegalArgumentException("only a resource-harvest objective may retain its exact field target");
        }
    }
    StrategicObjective(SubjectId id, SubjectId ownerId, StrategicObjectiveKind kind, Optional<InfectionCell> infectionTarget,
                       int decisionOrdinal, StrategicObjectiveStatus status) {
        this(id, ownerId, kind, infectionTarget, Optional.empty(), decisionOrdinal, status);
    }
    StrategicObjectiveLane lane() { return StrategicObjectiveLane.forKind(kind); }
    StrategicObjective withStatus(StrategicObjectiveStatus nextStatus) {
        return new StrategicObjective(id, ownerId, kind, infectionTarget, resourceSiteTarget, decisionOrdinal, nextStatus);
    }
}
