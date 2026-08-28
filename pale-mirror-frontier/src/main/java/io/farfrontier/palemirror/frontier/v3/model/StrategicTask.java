package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One exact durable task with explicit requirements and predecessor identities. */
record StrategicTask(SubjectId id, SubjectId objectiveId, SubjectId ownerId, StrategicTaskKind kind,
                     Optional<InfectionCell> infectionTarget, List<StrategicTaskRequirement> requirements,
                     List<SubjectId> dependencies, StrategicTaskStatus status) {
    StrategicTask {
        Objects.requireNonNull(id, "task id"); Objects.requireNonNull(objectiveId, "task objective");
        Objects.requireNonNull(ownerId, "task owner"); Objects.requireNonNull(kind, "task kind");
        Objects.requireNonNull(infectionTarget, "task infection target"); Objects.requireNonNull(status, "task status");
        requirements = List.copyOf(requirements); dependencies = List.copyOf(dependencies);
        if (kind != StrategicTaskKind.GROW_HIVE_ORGANISM && kind != StrategicTaskKind.PRODUCE_BREAD && infectionTarget.isEmpty()) {
            throw new IllegalArgumentException("infection strategic task requires an infection target");
        }
        if ((kind == StrategicTaskKind.GROW_HIVE_ORGANISM || kind == StrategicTaskKind.PRODUCE_BREAD) && infectionTarget.isPresent()) {
            throw new IllegalArgumentException("hive growth task cannot carry an infection target");
        }
        if (requirements.isEmpty()) throw new IllegalArgumentException("strategic task requires an explicit precondition");
        if (requirements.stream().distinct().count() != requirements.size() || dependencies.stream().distinct().count() != dependencies.size()) {
            throw new IllegalArgumentException("strategic task requirements and dependencies must be unique");
        }
    }
    StrategicTask withStatus(StrategicTaskStatus nextStatus) {
        return new StrategicTask(id, objectiveId, ownerId, kind, infectionTarget, requirements, dependencies, nextStatus);
    }
}
