package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One exact durable task with explicit requirements and predecessor identities. */
public record StrategicTask(SubjectId id, SubjectId objectiveId, SubjectId ownerId, StrategicTaskKind kind,
                     Optional<InfectionCell> infectionTarget, Optional<SubjectId> resourceSiteTarget,
                     List<StrategicTaskRequirement> requirements,
                     List<SubjectId> dependencies, StrategicTaskStatus status,
                     SubjectId authorityId, long authorityEpoch) {
    public StrategicTask {
        Objects.requireNonNull(id, "task id"); Objects.requireNonNull(objectiveId, "task objective");
        Objects.requireNonNull(ownerId, "task owner"); Objects.requireNonNull(kind, "task kind");
        Objects.requireNonNull(infectionTarget, "task infection target");
        Objects.requireNonNull(resourceSiteTarget, "task resource-site target"); Objects.requireNonNull(status, "task status");
        Objects.requireNonNull(authorityId, "task decision authority");
        if (authorityEpoch < 0L) throw new IllegalArgumentException("task decision epoch must be non-negative");
        requirements = List.copyOf(requirements); dependencies = List.copyOf(dependencies);
        if (kind != StrategicTaskKind.GROW_HIVE_ORGANISM
                && kind != StrategicTaskKind.ASSAULT_SETTLEMENT
                && kind != StrategicTaskKind.PRODUCE_BREAD
                && kind != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE
                && kind != StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS && kind != StrategicTaskKind.HARVEST_RESOURCE_SITE && infectionTarget.isEmpty()) {
            throw new IllegalArgumentException("infection strategic task requires an infection target");
        }
        if ((kind == StrategicTaskKind.GROW_HIVE_ORGANISM
                || kind == StrategicTaskKind.ASSAULT_SETTLEMENT
                || kind == StrategicTaskKind.PRODUCE_BREAD
                || kind == StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE
                || kind == StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS || kind == StrategicTaskKind.HARVEST_RESOURCE_SITE) && infectionTarget.isPresent()) {
            throw new IllegalArgumentException("hive growth task cannot carry an infection target");
        }
        if (requirements.isEmpty()) throw new IllegalArgumentException("strategic task requires an explicit precondition");
        if (requirements.stream().distinct().count() != requirements.size() || dependencies.stream().distinct().count() != dependencies.size()) {
            throw new IllegalArgumentException("strategic task requirements and dependencies must be unique");
        }
        if (kind == StrategicTaskKind.HARVEST_RESOURCE_SITE != resourceSiteTarget.isPresent()) {
            throw new IllegalArgumentException("only resource-harvest task may retain its exact field target");
        }
    }
    public StrategicTask(SubjectId id, SubjectId objectiveId, SubjectId ownerId, StrategicTaskKind kind,
                         Optional<InfectionCell> infectionTarget, List<StrategicTaskRequirement> requirements,
                         List<SubjectId> dependencies, StrategicTaskStatus status) {
        this(id, objectiveId, ownerId, kind, infectionTarget, Optional.empty(), requirements, dependencies, status, ownerId, 0L);
    }
    public StrategicTask(SubjectId id, SubjectId objectiveId, SubjectId ownerId, StrategicTaskKind kind,
                         Optional<InfectionCell> infectionTarget, Optional<SubjectId> resourceSiteTarget,
                         List<StrategicTaskRequirement> requirements, List<SubjectId> dependencies, StrategicTaskStatus status) {
        this(id, objectiveId, ownerId, kind, infectionTarget, resourceSiteTarget, requirements, dependencies, status, ownerId, 0L);
    }
    public StrategicTask withStatus(StrategicTaskStatus nextStatus) {
        return new StrategicTask(id, objectiveId, ownerId, kind, infectionTarget, resourceSiteTarget, requirements, dependencies,
                nextStatus, authorityId, authorityEpoch);
    }
}
