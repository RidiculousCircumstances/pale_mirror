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
            if (objective.kind() == StrategicObjectiveKind.HIVE_GROW_ORGANISM
                    && (task.kind() != StrategicTaskKind.GROW_HIVE_ORGANISM || !task.requirements().equals(List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS)))) {
                throw new IllegalArgumentException("hive growth task has an invalid decomposition");
            }
            if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD
                    && (task.kind() != StrategicTaskKind.PRODUCE_BREAD || !task.requirements().equals(List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP,
                    StrategicTaskRequirement.EXACT_WHEAT_INPUT, StrategicTaskRequirement.FREE_DEPOT_SLOT)))) {
                throw new IllegalArgumentException("settlement production task has an invalid decomposition");
            }
            if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE
                    && (task.kind() != StrategicTaskKind.DELIVER_BREAD_TO_HIVE || !task.requirements().equals(List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO,
                    StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE, StrategicTaskRequirement.AVAILABLE_HAULER, StrategicTaskRequirement.AVAILABLE_GUARD)))) {
                throw new IllegalArgumentException("settlement delivery task has an invalid decomposition");
            }
            if (task.dependencies().stream().anyMatch(dependency -> !this.tasks.containsKey(dependency) || dependency.equals(task.id()))) {
                throw new IllegalArgumentException("strategic task dependency must name another retained task");
            }
            if (objective.status() != StrategicObjectiveStatus.ACTIVE
                    && (task.status() == StrategicTaskStatus.PENDING || task.status() == StrategicTaskStatus.ACTIVE)) {
                throw new IllegalArgumentException("terminal strategic objective has unfinished task");
            }
            if (objective.status() == StrategicObjectiveStatus.COMPLETED && task.status() == StrategicTaskStatus.BLOCKED) {
                throw new IllegalArgumentException("completed strategic objective has blocked task");
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
        StrategicPlanState retained = compactFor(1, 0);
        if (retained.objectives.containsKey(objective.id()) || retained.hasActiveObjective(objective.ownerId())) throw new IllegalArgumentException("strategic objective is duplicate or owner is already active");
        Map<SubjectId, StrategicObjective> next = new LinkedHashMap<>(retained.objectives); next.put(objective.id(), objective);
        return new StrategicPlanState(next, retained.tasks);
    }

    StrategicPlanState addTask(StrategicTask task) {
        Objects.requireNonNull(task, "strategic task");
        StrategicPlanState retained = compactFor(0, 1);
        StrategicObjective objective = retained.objectives.get(task.objectiveId());
        if (retained.tasks.containsKey(task.id()) || objective == null || objective.status() != StrategicObjectiveStatus.ACTIVE) {
            throw new IllegalArgumentException("strategic task identity or objective is invalid");
        }
        Map<SubjectId, StrategicTask> next = new LinkedHashMap<>(retained.tasks); next.put(task.id(), task);
        return new StrategicPlanState(retained.objectives, next);
    }

    StrategicPlanState transitionTask(SubjectId taskId, StrategicTaskStatus nextStatus) {
        StrategicTask current = tasks.get(Objects.requireNonNull(taskId, "strategic task id"));
        if (current == null || !allowed(current.status(), nextStatus)) throw new IllegalArgumentException("strategic task transition is not allowed");
        Map<SubjectId, StrategicTask> nextTasks = new LinkedHashMap<>(tasks); nextTasks.put(taskId, current.withStatus(nextStatus));
        Map<SubjectId, StrategicObjective> nextObjectives = new LinkedHashMap<>(objectives);
        if (nextStatus == StrategicTaskStatus.BLOCKED || nextStatus == StrategicTaskStatus.COMPLETED) {
            StrategicObjective objective = objectives.get(current.objectiveId());
            boolean terminal = nextTasks.values().stream().filter(task -> task.objectiveId().equals(objective.id()))
                    .allMatch(task -> task.status() == StrategicTaskStatus.BLOCKED || task.status() == StrategicTaskStatus.COMPLETED);
            if (terminal) {
                boolean blocked = nextTasks.values().stream().filter(task -> task.objectiveId().equals(objective.id()))
                        .anyMatch(task -> task.status() == StrategicTaskStatus.BLOCKED);
                nextObjectives.put(objective.id(), objective.withStatus(blocked ? StrategicObjectiveStatus.BLOCKED : StrategicObjectiveStatus.COMPLETED));
            }
        }
        return new StrategicPlanState(nextObjectives, nextTasks);
    }

    @Override public boolean equals(Object other) {
        return other instanceof StrategicPlanState value && objectives.equals(value.objectives) && tasks.equals(value.tasks);
    }
    @Override public int hashCode() { return Objects.hash(objectives, tasks); }

    private StrategicPlanState compactFor(int newObjectives, int newTasks) {
        Map<SubjectId, StrategicObjective> retainedObjectives = new LinkedHashMap<>(objectives);
        Map<SubjectId, StrategicTask> retainedTasks = new LinkedHashMap<>(tasks);
        while (retainedObjectives.size() + newObjectives > MAX_OBJECTIVES || retainedTasks.size() + newTasks > MAX_TASKS) {
            StrategicObjective discard = retainedObjectives.values().stream().filter(value -> value.status() != StrategicObjectiveStatus.ACTIVE)
                    .sorted(java.util.Comparator.comparingInt(StrategicObjective::decisionOrdinal).thenComparing(StrategicObjective::id)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("strategic plan retention capacity exhausted by active work"));
            retainedObjectives.remove(discard.id());
            retainedTasks.values().removeIf(task -> task.objectiveId().equals(discard.id()));
        }
        return retainedObjectives.equals(objectives) && retainedTasks.equals(tasks) ? this : new StrategicPlanState(retainedObjectives, retainedTasks);
    }

    private static boolean allowed(StrategicTaskStatus current, StrategicTaskStatus next) {
        return current == StrategicTaskStatus.PENDING && (next == StrategicTaskStatus.ACTIVE || next == StrategicTaskStatus.BLOCKED)
                || current == StrategicTaskStatus.ACTIVE && (next == StrategicTaskStatus.COMPLETED || next == StrategicTaskStatus.BLOCKED);
    }

    private static <T> Map<SubjectId, T> immutable(Map<SubjectId, T> source, String name) {
        Objects.requireNonNull(source, name); LinkedHashMap<SubjectId, T> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> { if (key == null || value == null || copy.put(key, value) != null) throw new IllegalArgumentException("invalid " + name); });
        return Map.copyOf(copy);
    }
}
