package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Canonical bounded owner of retained utility decisions and their durable task graphs. */
final class StrategicPlanState {
    static final int MAX_OBJECTIVES = 128;
    static final int MAX_TASKS = 512;
    static final int MAX_ROUTE_PATROLS = 128;
    static final int MAX_ROUTE_ENGAGEMENTS = 128;
    private final Map<SubjectId, StrategicObjective> objectives;
    private final Map<SubjectId, StrategicTask> tasks;
    private final Map<SubjectId, RoutePatrol> routePatrols;
    private final Map<SubjectId, RouteEngagement> routeEngagements;
    private final SettlementInfectionKnowledge infectionKnowledge;
    private final HiveOperationKnowledge hiveOperationKnowledge;
    private final HiveTerritoryKnowledge hiveTerritoryKnowledge;
    private final HiveSettlementKnowledge hiveSettlementKnowledge;
    private final HiveDoctrineState hiveDoctrine;

    StrategicPlanState(Map<SubjectId, StrategicObjective> objectives, Map<SubjectId, StrategicTask> tasks, Map<SubjectId, RoutePatrol> routePatrols,
                       Map<SubjectId, RouteEngagement> routeEngagements) {
        this(objectives, tasks, routePatrols, routeEngagements, SettlementInfectionKnowledge.empty());
    }

    StrategicPlanState(Map<SubjectId, StrategicObjective> objectives, Map<SubjectId, StrategicTask> tasks, Map<SubjectId, RoutePatrol> routePatrols,
                       Map<SubjectId, RouteEngagement> routeEngagements, SettlementInfectionKnowledge infectionKnowledge) {
        this(objectives, tasks, routePatrols, routeEngagements, infectionKnowledge, HiveOperationKnowledge.empty(), HiveTerritoryKnowledge.empty(),
                HiveSettlementKnowledge.empty(), HiveDoctrineState.initial());
    }
    StrategicPlanState(Map<SubjectId, StrategicObjective> objectives, Map<SubjectId, StrategicTask> tasks, Map<SubjectId, RoutePatrol> routePatrols,
                       Map<SubjectId, RouteEngagement> routeEngagements, SettlementInfectionKnowledge infectionKnowledge, HiveOperationKnowledge hiveOperationKnowledge) {
        this(objectives, tasks, routePatrols, routeEngagements, infectionKnowledge, hiveOperationKnowledge, HiveTerritoryKnowledge.empty(),
                HiveSettlementKnowledge.empty(), HiveDoctrineState.initial());
    }
    StrategicPlanState(Map<SubjectId, StrategicObjective> objectives, Map<SubjectId, StrategicTask> tasks, Map<SubjectId, RoutePatrol> routePatrols,
                       Map<SubjectId, RouteEngagement> routeEngagements, SettlementInfectionKnowledge infectionKnowledge, HiveOperationKnowledge hiveOperationKnowledge,
                       HiveTerritoryKnowledge hiveTerritoryKnowledge) {
        this(objectives, tasks, routePatrols, routeEngagements, infectionKnowledge, hiveOperationKnowledge, hiveTerritoryKnowledge,
                HiveSettlementKnowledge.empty(), HiveDoctrineState.initial());
    }
    StrategicPlanState(Map<SubjectId, StrategicObjective> objectives, Map<SubjectId, StrategicTask> tasks, Map<SubjectId, RoutePatrol> routePatrols,
                       Map<SubjectId, RouteEngagement> routeEngagements, SettlementInfectionKnowledge infectionKnowledge, HiveOperationKnowledge hiveOperationKnowledge,
                       HiveTerritoryKnowledge hiveTerritoryKnowledge, HiveDoctrineState hiveDoctrine) {
        this(objectives, tasks, routePatrols, routeEngagements, infectionKnowledge, hiveOperationKnowledge, hiveTerritoryKnowledge,
                HiveSettlementKnowledge.empty(), hiveDoctrine);
    }
    StrategicPlanState(Map<SubjectId, StrategicObjective> objectives, Map<SubjectId, StrategicTask> tasks, Map<SubjectId, RoutePatrol> routePatrols,
                       Map<SubjectId, RouteEngagement> routeEngagements, SettlementInfectionKnowledge infectionKnowledge, HiveOperationKnowledge hiveOperationKnowledge,
                       HiveTerritoryKnowledge hiveTerritoryKnowledge, HiveSettlementKnowledge hiveSettlementKnowledge, HiveDoctrineState hiveDoctrine) {
        this.objectives = immutable(objectives, "strategic objectives"); this.tasks = immutable(tasks, "strategic tasks");
        this.routePatrols = immutable(routePatrols, "route patrols");
        this.routeEngagements = immutable(routeEngagements, "route engagements");
        this.infectionKnowledge = Objects.requireNonNull(infectionKnowledge, "settlement infection knowledge");
        this.hiveOperationKnowledge = Objects.requireNonNull(hiveOperationKnowledge, "hive operation knowledge");
        this.hiveTerritoryKnowledge = Objects.requireNonNull(hiveTerritoryKnowledge, "hive territory knowledge");
        this.hiveSettlementKnowledge = Objects.requireNonNull(hiveSettlementKnowledge, "hive settlement knowledge");
        this.hiveDoctrine = Objects.requireNonNull(hiveDoctrine, "hive doctrine");
        if (this.objectives.size() > MAX_OBJECTIVES || this.tasks.size() > MAX_TASKS
                || this.routePatrols.size() > MAX_ROUTE_PATROLS || this.routeEngagements.size() > MAX_ROUTE_ENGAGEMENTS) {
            throw new IllegalArgumentException("strategic plan retention limit exceeded");
        }
        this.objectives.forEach((id, objective) -> {
            if (!id.equals(objective.id())) throw new IllegalArgumentException("strategic objective key must match identity");
        });
        objectives.values().stream().filter(objective -> objective.status() == StrategicObjectiveStatus.ACTIVE)
                .collect(java.util.stream.Collectors.groupingBy(objective -> java.util.Map.entry(objective.ownerId(), objective.lane())))
                .values().forEach(values -> {
                    if (values.size() > 1) throw new IllegalArgumentException("objective owner has more than one active work lane entry");
                });
        this.tasks.forEach((id, task) -> {
            if (!id.equals(task.id()) || !this.objectives.containsKey(task.objectiveId())) {
                throw new IllegalArgumentException("strategic task must belong to one retained objective");
            }
            StrategicObjective objective = this.objectives.get(task.objectiveId());
            if (!objective.ownerId().equals(task.ownerId()) || !objective.infectionTarget().equals(task.infectionTarget())
                    || !objective.resourceSiteTarget().equals(task.resourceSiteTarget())) {
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
                    StrategicTaskRequirement.EXACT_WHEAT_INPUT)))) {
                throw new IllegalArgumentException("settlement production task has an invalid decomposition");
            }
            if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE && task.kind() == StrategicTaskKind.PREPARE_BREAD_CARGO
                    && !task.requirements().equals(List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO))) {
                throw new IllegalArgumentException("settlement cargo preparation has an invalid decomposition");
            }
            if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE && task.kind() == StrategicTaskKind.DELIVER_BREAD_TO_HIVE
                    && !task.requirements().equals(List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE, StrategicTaskRequirement.AVAILABLE_HAULER,
                    StrategicTaskRequirement.AVAILABLE_GUARD))) {
                throw new IllegalArgumentException("settlement delivery task has an invalid decomposition");
            }
            if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE
                    && (task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE || !task.requirements().equals(List.of(StrategicTaskRequirement.AVAILABLE_GUARD)))) {
                throw new IllegalArgumentException("settlement patrol task has an invalid decomposition");
            }
            if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_CONSTRUCT_ROUTE_BYPASS
                    && (task.kind() != StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS || !task.requirements().equals(List.of(StrategicTaskRequirement.CONFIRMED_ROUTE_OBSTRUCTION,
                    StrategicTaskRequirement.EXACT_ROUTE_CONSTRUCTION_MATERIAL)) || task.dependencies().size() != 1)) {
                throw new IllegalArgumentException("settlement route construction task has an invalid decomposition");
            }
            if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE
                    && (task.kind() != StrategicTaskKind.HARVEST_RESOURCE_SITE || !task.requirements().equals(List.of(StrategicTaskRequirement.ACTIVE_FARM,
                    StrategicTaskRequirement.AVAILABLE_FARMER, StrategicTaskRequirement.FREE_DEPOT_SLOT)))) {
                throw new IllegalArgumentException("settlement harvest task has an invalid decomposition");
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
        tasks.values().forEach(task -> task.dependencies().forEach(dependency -> {
            StrategicTask predecessor = tasks.get(dependency);
            if (task.kind() == StrategicTaskKind.PREPARE_BREAD_CARGO
                    && (predecessor.kind() != StrategicTaskKind.PRODUCE_BREAD || !predecessor.ownerId().equals(task.ownerId())
                    || predecessor.status() != StrategicTaskStatus.COMPLETED)) {
                throw new IllegalArgumentException("settlement cargo preparation dependency must be one completed local production task");
            }
            if (task.kind() == StrategicTaskKind.DELIVER_BREAD_TO_HIVE
                    && (predecessor.kind() != StrategicTaskKind.PREPARE_BREAD_CARGO || !predecessor.objectiveId().equals(task.objectiveId()))) {
                throw new IllegalArgumentException("settlement delivery dependency must be its cargo preparation task");
            }
            if (task.kind() == StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS
                    && (predecessor.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE || !predecessor.ownerId().equals(task.ownerId())
                    || predecessor.status() != StrategicTaskStatus.COMPLETED)) {
                throw new IllegalArgumentException("settlement route construction dependency must be its confirmed patrol");
            }
        }));
        objectives.values().stream().filter(objective -> objective.kind() == StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE).forEach(objective ->
                validateDeliveryDecomposition(objective, tasks));
        this.routePatrols.forEach((taskId, patrol) -> validatePatrol(taskId, patrol));
        this.routeEngagements.forEach((id, engagement) -> {
            if (!id.equals(engagement.id()) || !tasks.containsKey(engagement.taskId())) throw new IllegalArgumentException("route engagement must retain its task");
        });
        tasks.keySet().forEach(id -> requireAcyclic(id, new java.util.HashSet<>(), new java.util.HashSet<>()));
    }

    static StrategicPlanState empty() { return new StrategicPlanState(Map.of(), Map.of(), Map.of(), Map.of()); }

    Map<SubjectId, StrategicObjective> objectives() { return objectives; }
    Map<SubjectId, StrategicTask> tasks() { return tasks; }
    Map<SubjectId, RoutePatrol> routePatrols() { return routePatrols; }
    Map<SubjectId, RouteEngagement> routeEngagements() { return routeEngagements; }
    SettlementInfectionKnowledge infectionKnowledge() { return infectionKnowledge; }
    HiveOperationKnowledge hiveOperationKnowledge() { return hiveOperationKnowledge; }
    HiveTerritoryKnowledge hiveTerritoryKnowledge() { return hiveTerritoryKnowledge; }
    HiveSettlementKnowledge hiveSettlementKnowledge() { return hiveSettlementKnowledge; }
    HiveDoctrineState hiveDoctrine() { return hiveDoctrine; }

    StrategicPlanState withInfectionKnowledge(SettlementInfectionKnowledge next) {
        return infectionKnowledge.equals(next) ? this : new StrategicPlanState(objectives, tasks, routePatrols, routeEngagements, next, hiveOperationKnowledge, hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }
    StrategicPlanState withHiveOperationKnowledge(HiveOperationKnowledge next) {
        return hiveOperationKnowledge.equals(next) ? this : new StrategicPlanState(objectives, tasks, routePatrols, routeEngagements, infectionKnowledge, next, hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }
    StrategicPlanState withHiveTerritoryKnowledge(HiveTerritoryKnowledge next) {
        return hiveTerritoryKnowledge.equals(next) ? this : new StrategicPlanState(objectives, tasks, routePatrols, routeEngagements,
                infectionKnowledge, hiveOperationKnowledge, next, hiveSettlementKnowledge, hiveDoctrine);
    }
    StrategicPlanState withHiveSettlementKnowledge(HiveSettlementKnowledge next) {
        return hiveSettlementKnowledge.equals(next) ? this : new StrategicPlanState(objectives, tasks, routePatrols, routeEngagements,
                infectionKnowledge, hiveOperationKnowledge, hiveTerritoryKnowledge, next, hiveDoctrine);
    }
    StrategicPlanState withHiveDoctrine(HiveDoctrineState next) {
        return hiveDoctrine.equals(next) ? this : new StrategicPlanState(objectives, tasks, routePatrols, routeEngagements,
                infectionKnowledge, hiveOperationKnowledge, hiveTerritoryKnowledge, hiveSettlementKnowledge, next);
    }

    void validate(FrontierBootstrap bootstrap, HumanPopulation humanPopulation) {
        infectionKnowledge.validate(bootstrap);
        // Resource-site geometry is immutable for one bootstrap.  Validation may visit many
        // retained terminal objectives after an unrelated state transition, so compiling the
        // same twelve-site catalogue per objective is needless allocation rather than safety.
        Map<SubjectId, ResourceSite> resourceSites = FrontierResourceSitePlan.compile(bootstrap);
        objectives.values().forEach(objective -> {
            boolean knownOwner = bootstrap.hive().id().equals(objective.ownerId())
                    || bootstrap.settlements().stream().anyMatch(settlement -> settlement.id().equals(objective.ownerId()));
            if (!knownOwner) throw new IllegalArgumentException("strategic objective has a foreign owner");
            objective.resourceSiteTarget().ifPresent(siteId -> {
                ResourceSite site = resourceSites.get(siteId);
                if (site == null || !site.settlementId().equals(objective.ownerId())) {
                    throw new IllegalArgumentException("strategic harvest objective has a foreign resource site");
                }
            });
        });
        routePatrols.values().forEach(patrol -> {
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, patrol.settlementId());
            ResidentProfile guard = humanPopulation.resident(patrol.guardId());
            if (guard == null || !guard.settlementId().equals(settlement.id())) throw new IllegalArgumentException("route patrol guard is foreign");
            if (guard.role() != ResidentRole.GUARD || !patrol.route().equals(FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement.id()))) {
                throw new IllegalArgumentException("route patrol does not retain its guard or canonical route");
            }
        });
    }

    boolean hasActiveObjective(SubjectId ownerId, StrategicObjectiveLane lane) {
        return objectives.values().stream().anyMatch(objective -> objective.ownerId().equals(ownerId) && objective.lane() == lane
                && objective.status() == StrategicObjectiveStatus.ACTIVE);
    }

    StrategicPlanState addObjective(StrategicObjective objective) {
        Objects.requireNonNull(objective, "strategic objective");
        StrategicPlanState retained = compactFor(1, 0);
        if (retained.objectives.containsKey(objective.id()) || retained.hasActiveObjective(objective.ownerId(), objective.lane())) {
            throw new IllegalArgumentException("strategic objective is duplicate or owner work lane is already active");
        }
        Map<SubjectId, StrategicObjective> next = new LinkedHashMap<>(retained.objectives); next.put(objective.id(), objective);
        return new StrategicPlanState(next, retained.tasks, retained.routePatrols, retained.routeEngagements, retained.infectionKnowledge, retained.hiveOperationKnowledge,
                retained.hiveTerritoryKnowledge, retained.hiveSettlementKnowledge, retained.hiveDoctrine);
    }

    StrategicPlanState addTask(StrategicTask task) {
        Objects.requireNonNull(task, "strategic task");
        StrategicPlanState retained = compactFor(0, 1, task.dependencies());
        StrategicObjective objective = retained.objectives.get(task.objectiveId());
        if (retained.tasks.containsKey(task.id()) || objective == null || objective.status() != StrategicObjectiveStatus.ACTIVE) {
            throw new IllegalArgumentException("strategic task identity or objective is invalid");
        }
        Map<SubjectId, StrategicTask> next = new LinkedHashMap<>(retained.tasks); next.put(task.id(), task);
        return new StrategicPlanState(retained.objectives, next, retained.routePatrols, retained.routeEngagements, retained.infectionKnowledge, retained.hiveOperationKnowledge,
                retained.hiveTerritoryKnowledge, retained.hiveSettlementKnowledge, retained.hiveDoctrine);
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
        return new StrategicPlanState(nextObjectives, nextTasks, routePatrols, routeEngagements, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    StrategicPlanState startPatrol(RoutePatrol patrol) {
        Objects.requireNonNull(patrol, "route patrol");
        if (routePatrols.containsKey(patrol.taskId())) throw new IllegalArgumentException("route patrol is already retained for its task");
        Map<SubjectId, RoutePatrol> next = new LinkedHashMap<>(routePatrols); next.put(patrol.taskId(), patrol);
        return new StrategicPlanState(objectives, tasks, next, routeEngagements, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    StrategicPlanState advancePatrol(SubjectId taskId, int routeIndex) {
        RoutePatrol current = routePatrols.get(taskId);
        if (current == null) throw new IllegalArgumentException("unknown route patrol");
        Map<SubjectId, RoutePatrol> next = new LinkedHashMap<>(routePatrols); next.put(taskId, current.advance(routeIndex));
        return new StrategicPlanState(objectives, tasks, next, routeEngagements, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    StrategicPlanState confirmPatrolObstruction(SubjectId taskId, BlockPosition position) {
        RoutePatrol current = routePatrols.get(taskId);
        if (current == null) throw new IllegalArgumentException("unknown route patrol");
        Map<SubjectId, RoutePatrol> next = new LinkedHashMap<>(routePatrols); next.put(taskId, current.confirm(position));
        return new StrategicPlanState(objectives, tasks, next, routeEngagements, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    StrategicPlanState failPatrol(SubjectId taskId) {
        RoutePatrol current = routePatrols.get(taskId);
        if (current == null) throw new IllegalArgumentException("unknown route patrol");
        Map<SubjectId, RoutePatrol> next = new LinkedHashMap<>(routePatrols); next.put(taskId, current.fail());
        return new StrategicPlanState(objectives, tasks, next, routeEngagements, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    StrategicPlanState startEngagement(RouteEngagement engagement) {
        Objects.requireNonNull(engagement, "route engagement");
        if (routeEngagements.containsKey(engagement.id()) || routeEngagements.values().stream()
                .anyMatch(value -> value.operationId().equals(engagement.operationId()) && value.status() != RouteEngagementStatus.RESOLVED)) {
            throw new IllegalArgumentException("route engagement identity or active operation is already retained");
        }
        Map<SubjectId, RouteEngagement> next = new LinkedHashMap<>(routeEngagements); next.put(engagement.id(), engagement);
        return new StrategicPlanState(objectives, tasks, routePatrols, next, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    StrategicPlanState transitionEngagement(SubjectId engagementId, RouteEngagementStatus nextStatus) {
        RouteEngagement current = routeEngagements.get(Objects.requireNonNull(engagementId, "route engagement id"));
        if (current == null || !allowed(current.status(), nextStatus)) throw new IllegalArgumentException("route engagement transition is not allowed");
        Map<SubjectId, RouteEngagement> next = new LinkedHashMap<>(routeEngagements); next.put(engagementId, current.withStatus(nextStatus));
        return new StrategicPlanState(objectives, tasks, routePatrols, next, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    StrategicPlanState replaceEngagement(RouteEngagement engagement) {
        Objects.requireNonNull(engagement, "route engagement");
        if (!routeEngagements.containsKey(engagement.id())) throw new IllegalArgumentException("unknown route engagement");
        Map<SubjectId, RouteEngagement> next = new LinkedHashMap<>(routeEngagements); next.put(engagement.id(), engagement);
        return new StrategicPlanState(objectives, tasks, routePatrols, next, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    @Override public boolean equals(Object other) {
        return other instanceof StrategicPlanState value && objectives.equals(value.objectives) && tasks.equals(value.tasks)
                && routePatrols.equals(value.routePatrols) && routeEngagements.equals(value.routeEngagements)
                && infectionKnowledge.equals(value.infectionKnowledge) && hiveOperationKnowledge.equals(value.hiveOperationKnowledge)
                && hiveTerritoryKnowledge.equals(value.hiveTerritoryKnowledge) && hiveSettlementKnowledge.equals(value.hiveSettlementKnowledge)
                && hiveDoctrine.equals(value.hiveDoctrine);
    }
    @Override public int hashCode() { return Objects.hash(objectives, tasks, routePatrols, routeEngagements, infectionKnowledge, hiveOperationKnowledge,
            hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine); }

    private StrategicPlanState compactFor(int newObjectives, int newTasks) { return compactFor(newObjectives, newTasks, List.of()); }

    private StrategicPlanState compactFor(int newObjectives, int newTasks, List<SubjectId> protectedTaskIds) {
        if (objectives.size() + newObjectives <= MAX_OBJECTIVES && tasks.size() + newTasks <= MAX_TASKS) return this;
        Map<SubjectId, StrategicObjective> retainedObjectives = new LinkedHashMap<>(objectives);
        Map<SubjectId, StrategicTask> retainedTasks = new LinkedHashMap<>(tasks);
        while (retainedObjectives.size() + newObjectives > MAX_OBJECTIVES || retainedTasks.size() + newTasks > MAX_TASKS) {
            RetentionIndex retention = RetentionIndex.forTasks(retainedTasks, retainedObjectives);
            StrategicObjective discard = retainedObjectives.values().stream().filter(value -> value.status() != StrategicObjectiveStatus.ACTIVE)
                    .filter(value -> retention.removable(value.id(), protectedTaskIds))
                    .sorted(java.util.Comparator.comparingInt(StrategicObjective::decisionOrdinal).thenComparing(StrategicObjective::id)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("strategic plan retention capacity exhausted by active work"));
            retainedObjectives.remove(discard.id());
            retainedTasks.values().removeIf(task -> task.objectiveId().equals(discard.id()));
        }
        Map<SubjectId, RoutePatrol> retainedPatrols = new LinkedHashMap<>(routePatrols);
        retainedPatrols.keySet().removeIf(taskId -> !retainedTasks.containsKey(taskId));
        Map<SubjectId, RouteEngagement> retainedEngagements = new LinkedHashMap<>(routeEngagements);
        retainedEngagements.values().removeIf(engagement -> !retainedTasks.containsKey(engagement.taskId()));
        return retainedObjectives.equals(objectives) && retainedTasks.equals(tasks) && retainedPatrols.equals(routePatrols)
                && retainedEngagements.equals(routeEngagements) ? this
                : new StrategicPlanState(retainedObjectives, retainedTasks, retainedPatrols, retainedEngagements, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    private static boolean allowed(StrategicTaskStatus current, StrategicTaskStatus next) {
        return current == StrategicTaskStatus.PENDING && (next == StrategicTaskStatus.ACTIVE || next == StrategicTaskStatus.BLOCKED)
                || current == StrategicTaskStatus.ACTIVE && (next == StrategicTaskStatus.COMPLETED || next == StrategicTaskStatus.BLOCKED);
    }

    private static boolean allowed(RouteEngagementStatus current, RouteEngagementStatus next) {
        return current == RouteEngagementStatus.APPROACHING && (next == RouteEngagementStatus.WAITING_FOR_INTERCEPT
                || next == RouteEngagementStatus.COLD_COMBAT || next == RouteEngagementStatus.UNKNOWN_AFTER_RESTART
                || next == RouteEngagementStatus.CONFLICT)
                || current == RouteEngagementStatus.WAITING_FOR_INTERCEPT && (next == RouteEngagementStatus.COLD_COMBAT
                || next == RouteEngagementStatus.UNKNOWN_AFTER_RESTART || next == RouteEngagementStatus.CONFLICT)
                || current == RouteEngagementStatus.COLD_COMBAT && (next == RouteEngagementStatus.HOT
                || next == RouteEngagementStatus.UNKNOWN_AFTER_RESTART || next == RouteEngagementStatus.CONFLICT)
                || current == RouteEngagementStatus.HOT && (next == RouteEngagementStatus.COLD_COMBAT
                || next == RouteEngagementStatus.UNKNOWN_AFTER_RESTART || next == RouteEngagementStatus.CONFLICT)
                || current == RouteEngagementStatus.UNKNOWN_AFTER_RESTART && (next == RouteEngagementStatus.HOT || next == RouteEngagementStatus.COLD_COMBAT)
                || current == RouteEngagementStatus.CONFLICT && next == RouteEngagementStatus.HOT;
    }

    StrategicPlanState resolveEngagement(SubjectId engagementId, RouteEngagementOutcome outcome) {
        RouteEngagement current = routeEngagements.get(Objects.requireNonNull(engagementId, "route engagement id"));
        if (current == null) throw new IllegalArgumentException("unknown route engagement");
        Map<SubjectId, RouteEngagement> next = new LinkedHashMap<>(routeEngagements); next.put(engagementId, current.resolve(outcome));
        return new StrategicPlanState(objectives, tasks, routePatrols, next, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    /** Blocks the affected delivery and aborts any active hive interception after a player takes its cargo. */
    StrategicPlanState interruptRouteOperation(SubjectId operationId, SubjectId settlementId) {
        Objects.requireNonNull(operationId, "operation id"); Objects.requireNonNull(settlementId, "settlement id");
        Map<SubjectId, StrategicTask> nextTasks = new LinkedHashMap<>(tasks);
        for (StrategicTask task : tasks.values()) {
            boolean intercept = task.kind() == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION && task.operationTarget().equals(java.util.Optional.of(operationId));
            boolean delivery = task.kind() == StrategicTaskKind.DELIVER_BREAD_TO_HIVE && task.ownerId().equals(settlementId);
            if ((intercept || delivery) && task.status() != StrategicTaskStatus.COMPLETED && task.status() != StrategicTaskStatus.BLOCKED) {
                nextTasks.put(task.id(), task.withStatus(StrategicTaskStatus.BLOCKED));
            }
        }
        Map<SubjectId, RouteEngagement> nextEngagements = new LinkedHashMap<>(routeEngagements);
        routeEngagements.values().stream().filter(engagement -> engagement.operationId().equals(operationId))
                .filter(engagement -> engagement.status() != RouteEngagementStatus.RESOLVED)
                .forEach(engagement -> nextEngagements.put(engagement.id(), engagement.abort()));
        return new StrategicPlanState(objectives, nextTasks, routePatrols, nextEngagements, infectionKnowledge, hiveOperationKnowledge,
                hiveTerritoryKnowledge, hiveSettlementKnowledge, hiveDoctrine);
    }

    /**
     * One compaction pass needs only task ownership and cross-objective dependency indexes.
     * Rebuilding those bounded indexes once per discarded objective preserves the exact same
     * retention rule without multiplying a 512-task scan by every terminal candidate.
     */
    private record RetentionIndex(Map<SubjectId, Set<SubjectId>> ownedByObjective, Set<SubjectId> externallyReferenced,
                                  Set<SubjectId> prospectiveDeliveryPredecessors) {
        static RetentionIndex forTasks(Map<SubjectId, StrategicTask> tasks, Map<SubjectId, StrategicObjective> objectives) {
            Map<SubjectId, Set<SubjectId>> owned = new HashMap<>();
            for (StrategicTask task : tasks.values()) {
                owned.computeIfAbsent(task.objectiveId(), ignored -> new HashSet<>()).add(task.id());
            }
            Set<SubjectId> external = new HashSet<>();
            for (StrategicTask task : tasks.values()) for (SubjectId dependency : task.dependencies()) {
                StrategicTask predecessor = tasks.get(dependency);
                if (predecessor != null && !predecessor.objectiveId().equals(task.objectiveId())) external.add(dependency);
            }
            // An objective is reduced before its tasks in one atomic command. A forthcoming
            // delivery task may therefore reference the newest completed local bread task that
            // is not yet visible in this intermediate state. Keep precisely that bounded
            // predecessor per owner; once a delivery task exists, its ordinary dependency index
            // takes over. This avoids a reducer-order-dependent dangling task reference.
            Map<SubjectId, StrategicTask> newestBread = new HashMap<>();
            for (StrategicTask task : tasks.values()) {
                if (task.kind() != StrategicTaskKind.PRODUCE_BREAD || task.status() != StrategicTaskStatus.COMPLETED) continue;
                StrategicTask prior = newestBread.get(task.ownerId());
                if (prior == null || newer(task, prior, objectives)) newestBread.put(task.ownerId(), task);
            }
            return new RetentionIndex(owned, external, Set.copyOf(newestBread.values().stream().map(StrategicTask::id).toList()));
        }

        boolean removable(SubjectId objectiveId, List<SubjectId> protectedTaskIds) {
            Set<SubjectId> owned = ownedByObjective.getOrDefault(objectiveId, Set.of());
            return java.util.Collections.disjoint(owned, protectedTaskIds) && java.util.Collections.disjoint(owned, externallyReferenced)
                    && java.util.Collections.disjoint(owned, prospectiveDeliveryPredecessors);
        }

        private static boolean newer(StrategicTask candidate, StrategicTask previous, Map<SubjectId, StrategicObjective> objectives) {
            StrategicObjective candidateObjective = objectives.get(candidate.objectiveId());
            StrategicObjective previousObjective = objectives.get(previous.objectiveId());
            if (candidateObjective == null || previousObjective == null) throw new IllegalArgumentException("production task lacks its objective");
            int byOrdinal = Integer.compare(candidateObjective.decisionOrdinal(), previousObjective.decisionOrdinal());
            return byOrdinal != 0 ? byOrdinal > 0 : candidate.id().compareTo(previous.id()) > 0;
        }
    }

    private static void validateDeliveryDecomposition(StrategicObjective objective, Map<SubjectId, StrategicTask> tasks) {
        List<StrategicTask> own = tasks.values().stream().filter(task -> task.objectiveId().equals(objective.id())).toList();
        if (own.stream().anyMatch(task -> task.kind() != StrategicTaskKind.PREPARE_BREAD_CARGO && task.kind() != StrategicTaskKind.DELIVER_BREAD_TO_HIVE)
                || own.stream().filter(task -> task.kind() == StrategicTaskKind.PREPARE_BREAD_CARGO).count() > 1
                || own.stream().filter(task -> task.kind() == StrategicTaskKind.DELIVER_BREAD_TO_HIVE).count() > 1) {
            throw new IllegalArgumentException("settlement delivery objective has an invalid task graph");
        }
    }

    private void validatePatrol(SubjectId taskId, RoutePatrol patrol) {
        StrategicTask task = tasks.get(taskId);
        if (!taskId.equals(patrol.taskId()) || task == null || task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE
                || !task.ownerId().equals(patrol.settlementId())) {
            throw new IllegalArgumentException("route patrol must bind one settlement patrol task");
        }
        if (patrol.status() == RoutePatrolStatus.EN_ROUTE && task.status() != StrategicTaskStatus.ACTIVE
                || patrol.status() == RoutePatrolStatus.ROUTE_CLEAR && task.status() != StrategicTaskStatus.ACTIVE && task.status() != StrategicTaskStatus.COMPLETED
                || patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED && task.status() != StrategicTaskStatus.ACTIVE && task.status() != StrategicTaskStatus.COMPLETED
                || patrol.status() == RoutePatrolStatus.FAILED && task.status() != StrategicTaskStatus.ACTIVE && task.status() != StrategicTaskStatus.BLOCKED) {
            throw new IllegalArgumentException("route patrol status must match its task terminal state");
        }
    }

    private void requireAcyclic(SubjectId id, java.util.Set<SubjectId> visiting, java.util.Set<SubjectId> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("strategic task dependency graph has a cycle");
        tasks.get(id).dependencies().forEach(dependency -> requireAcyclic(dependency, visiting, visited));
        visiting.remove(id); visited.add(id);
    }

    private static <T> Map<SubjectId, T> immutable(Map<SubjectId, T> source, String name) {
        Objects.requireNonNull(source, name); LinkedHashMap<SubjectId, T> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> { if (key == null || value == null || copy.put(key, value) != null) throw new IllegalArgumentException("invalid " + name); });
        return Map.copyOf(copy);
    }
}
