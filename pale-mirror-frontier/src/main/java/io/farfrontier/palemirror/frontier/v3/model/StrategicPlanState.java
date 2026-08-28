package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical bounded owner of retained utility decisions and their durable task graphs. */
final class StrategicPlanState {
    static final int MAX_OBJECTIVES = 128;
    static final int MAX_TASKS = 512;
    private final Map<SubjectId, StrategicObjective> objectives;
    private final Map<SubjectId, StrategicTask> tasks;

    StrategicPlanState(Map<SubjectId, StrategicObjective> objectives, Map<SubjectId, StrategicTask> tasks) {
        this.objectives = immutable(objectives, "strategic objectives"); this.tasks = immutable(tasks, "strategic tasks");
        if (this.objectives.size() > MAX_OBJECTIVES || this.tasks.size() > MAX_TASKS) throw new IllegalArgumentException("strategic plan retention limit exceeded");
        this.objectives.forEach((id, objective) -> {
            if (!id.equals(objective.id())) throw new IllegalArgumentException("strategic objective key must match identity");
        });
        this.tasks.forEach((id, task) -> {
            if (!id.equals(task.id()) || !this.objectives.containsKey(task.objectiveId())) {
                throw new IllegalArgumentException("strategic task must belong to one retained objective");
            }
            StrategicObjective objective = this.objectives.get(task.objectiveId());
            if (!objective.ownerId().equals(task.ownerId()) || !objective.infectionTarget().equals(task.infectionTarget())) {
                throw new IllegalArgumentException("strategic task must retain its objective owner and target");
            }
            if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION
                    && (task.kind() != StrategicTaskKind.DECONTAMINATE_INFECTION_CELL || !task.requirements().equals(List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT)))) {
                throw new IllegalArgumentException("settlement containment task has an invalid decomposition");
            }
            if (objective.kind() == StrategicObjectiveKind.HIVE_EXPAND_INFECTION
                    && (task.kind() != StrategicTaskKind.SPREAD_INFECTION_CELL || !task.requirements().equals(List.of(StrategicTaskRequirement.OPERATIONAL_HEART)))) {
                throw new IllegalArgumentException("hive expansion task has an invalid decomposition");
            }
            if (task.dependencies().stream().anyMatch(dependency -> !this.tasks.containsKey(dependency) || dependency.equals(task.id()))) {
                throw new IllegalArgumentException("strategic task dependency must name another retained task");
            }
        });
    }

    static StrategicPlanState empty() { return new StrategicPlanState(Map.of(), Map.of()); }

    Map<SubjectId, StrategicObjective> objectives() { return objectives; }
    Map<SubjectId, StrategicTask> tasks() { return tasks; }

    void validate(FrontierBootstrap bootstrap) {
        objectives.values().forEach(objective -> {
            boolean knownOwner = bootstrap.hive().id().equals(objective.ownerId())
                    || bootstrap.settlements().stream().anyMatch(settlement -> settlement.id().equals(objective.ownerId()));
            if (!knownOwner) throw new IllegalArgumentException("strategic objective has a foreign owner");
        });
    }

    boolean hasActiveObjective(SubjectId ownerId) {
        return objectives.values().stream().anyMatch(objective -> objective.ownerId().equals(ownerId) && objective.status() == StrategicObjectiveStatus.ACTIVE);
    }

    StrategicPlanState addObjective(StrategicObjective objective) {
        Objects.requireNonNull(objective, "strategic objective");
        if (objectives.containsKey(objective.id()) || hasActiveObjective(objective.ownerId())) throw new IllegalArgumentException("strategic objective is duplicate or owner is already active");
        Map<SubjectId, StrategicObjective> next = new LinkedHashMap<>(objectives); next.put(objective.id(), objective);
        return new StrategicPlanState(next, tasks);
    }

    StrategicPlanState addTask(StrategicTask task) {
        Objects.requireNonNull(task, "strategic task");
        if (tasks.containsKey(task.id())) throw new IllegalArgumentException("strategic task identity already exists");
        Map<SubjectId, StrategicTask> next = new LinkedHashMap<>(tasks); next.put(task.id(), task);
        return new StrategicPlanState(objectives, next);
    }

    @Override public boolean equals(Object other) {
        return other instanceof StrategicPlanState value && objectives.equals(value.objectives) && tasks.equals(value.tasks);
    }
    @Override public int hashCode() { return Objects.hash(objectives, tasks); }

    private static <T> Map<SubjectId, T> immutable(Map<SubjectId, T> source, String name) {
        Objects.requireNonNull(source, name); LinkedHashMap<SubjectId, T> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> { if (key == null || value == null || copy.put(key, value) != null) throw new IllegalArgumentException("invalid " + name); });
        return Map.copyOf(copy);
    }
}
