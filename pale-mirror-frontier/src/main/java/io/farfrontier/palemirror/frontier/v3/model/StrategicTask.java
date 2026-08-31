package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One exact durable task with explicit requirements and predecessor identities. */
public record StrategicTask(SubjectId id, SubjectId objectiveId, SubjectId ownerId, StrategicTaskKind kind,
                     Optional<InfectionCell> infectionTarget, Optional<SubjectId> operationTarget, Optional<SubjectId> resourceSiteTarget,
                     List<StrategicTaskRequirement> requirements,
                     List<SubjectId> dependencies, StrategicTaskStatus status, Optional<BlockPosition> operationObservationPosition) {
    public StrategicTask {
        Objects.requireNonNull(id, "task id"); Objects.requireNonNull(objectiveId, "task objective");
        Objects.requireNonNull(ownerId, "task owner"); Objects.requireNonNull(kind, "task kind");
        Objects.requireNonNull(infectionTarget, "task infection target"); Objects.requireNonNull(operationTarget, "task operation target");
        Objects.requireNonNull(resourceSiteTarget, "task resource-site target"); Objects.requireNonNull(status, "task status");
        Objects.requireNonNull(operationObservationPosition, "task operation observation position");
        requirements = List.copyOf(requirements); dependencies = List.copyOf(dependencies);
        if (kind != StrategicTaskKind.GROW_HIVE_ORGANISM && kind != StrategicTaskKind.INTERCEPT_ROUTE_OPERATION
                && kind != StrategicTaskKind.ASSAULT_SETTLEMENT
                && kind != StrategicTaskKind.PRODUCE_BREAD && kind != StrategicTaskKind.PREPARE_BREAD_CARGO
                && kind != StrategicTaskKind.DELIVER_BREAD_TO_HIVE && kind != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE
                && kind != StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS && kind != StrategicTaskKind.HARVEST_RESOURCE_SITE && infectionTarget.isEmpty()) {
            throw new IllegalArgumentException("infection strategic task requires an infection target");
        }
        if ((kind == StrategicTaskKind.GROW_HIVE_ORGANISM || kind == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION
                || kind == StrategicTaskKind.ASSAULT_SETTLEMENT
                || kind == StrategicTaskKind.PRODUCE_BREAD || kind == StrategicTaskKind.PREPARE_BREAD_CARGO
                || kind == StrategicTaskKind.DELIVER_BREAD_TO_HIVE || kind == StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE
                || kind == StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS || kind == StrategicTaskKind.HARVEST_RESOURCE_SITE) && infectionTarget.isPresent()) {
            throw new IllegalArgumentException("hive growth task cannot carry an infection target");
        }
        if (requirements.isEmpty()) throw new IllegalArgumentException("strategic task requires an explicit precondition");
        if (requirements.stream().distinct().count() != requirements.size() || dependencies.stream().distinct().count() != dependencies.size()) {
            throw new IllegalArgumentException("strategic task requirements and dependencies must be unique");
        }
        if (kind == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION != operationTarget.isPresent()) {
            throw new IllegalArgumentException("only route-intercept task may retain its exact operation target");
        }
        if (kind != StrategicTaskKind.INTERCEPT_ROUTE_OPERATION && operationObservationPosition.isPresent()) {
            throw new IllegalArgumentException("only route-intercept task may retain an observed operation position");
        }
        if (kind == StrategicTaskKind.HARVEST_RESOURCE_SITE != resourceSiteTarget.isPresent()) {
            throw new IllegalArgumentException("only resource-harvest task may retain its exact field target");
        }
    }
    public StrategicTask(SubjectId id, SubjectId objectiveId, SubjectId ownerId, StrategicTaskKind kind,
                  Optional<InfectionCell> infectionTarget, List<StrategicTaskRequirement> requirements,
                  List<SubjectId> dependencies, StrategicTaskStatus status) {
        this(id, objectiveId, ownerId, kind, infectionTarget, Optional.empty(), Optional.empty(), requirements, dependencies, status, Optional.empty());
    }
    public StrategicTask(SubjectId id, SubjectId objectiveId, SubjectId ownerId, StrategicTaskKind kind,
                  Optional<InfectionCell> infectionTarget, Optional<SubjectId> operationTarget,
                  List<StrategicTaskRequirement> requirements, List<SubjectId> dependencies, StrategicTaskStatus status) {
        this(id, objectiveId, ownerId, kind, infectionTarget, operationTarget, Optional.empty(), requirements, dependencies, status, Optional.empty());
    }
    public StrategicTask(SubjectId id, SubjectId objectiveId, SubjectId ownerId, StrategicTaskKind kind,
                  Optional<InfectionCell> infectionTarget, Optional<SubjectId> operationTarget, Optional<SubjectId> resourceSiteTarget,
                  List<StrategicTaskRequirement> requirements, List<SubjectId> dependencies, StrategicTaskStatus status) {
        this(id, objectiveId, ownerId, kind, infectionTarget, operationTarget, resourceSiteTarget, requirements, dependencies, status, Optional.empty());
    }
    public StrategicTask withStatus(StrategicTaskStatus nextStatus) {
        return new StrategicTask(id, objectiveId, ownerId, kind, infectionTarget, operationTarget, resourceSiteTarget, requirements, dependencies, nextStatus, operationObservationPosition);
    }
}
