package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Event-triggered deterministic utility selection and first durable task expansion. */
final class StrategicObjectiveProcess {
    private static final long REVIEW_INTERVAL = 400L;
    private static final long LOCAL_INFECTION_RADIUS_SQUARED = 25_600L;
    private StrategicObjectiveProcess() { }

    static ScheduledAction review(SubjectId owner, int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:objective-review-" + owner.value().replace(':', '-') + "-" + ordinal),
                new SimInstant(dueAt), 0, owner, "frontier.objective.review", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        return plan(state, action, true, true);
    }

    static ScheduledAction interceptOpportunity(SubjectId hive, RouteOperation operation, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:objective-intercept-opportunity-" + operation.id().value().replace(':', '-') + "-1"),
                new SimInstant(dueAt), 0, hive, "frontier.objective.interrupt", 1);
    }

    static List<ProposedEvent> planOpportunity(FrontierWorldState state, ScheduledAction action) {
        return plan(state, action, false, true);
    }

    /** Development profiles may retain the same economy without admitting a competing hive strike. */
    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean allowHiveInterception) {
        return plan(state, action, true, allowHiveInterception);
    }

    /** A ready exact field asks its settlement planner for work without bypassing durable task ownership. */
    static ScheduledAction resourceHarvestOpportunity(FrontierWorldState state, ResourceSiteLifecycle lifecycle, long dueAt) {
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId());
        if (site == null || lifecycle.phase() != ResourceSitePhase.READY) throw new IllegalArgumentException("resource harvest opportunity requires a ready known field");
        String suffix = lifecycle.siteId().value().substring("site:".length());
        return new ScheduledAction(new ScheduleId("schedule:objective-resource-harvest-" + suffix + "-" + lifecycle.growthEpoch() + "-" + dueAt),
                new SimInstant(dueAt), 0, lifecycle.siteId(), "frontier.objective.resource_harvest", 1);
    }

    static List<ProposedEvent> planResourceHarvestOpportunity(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(action.subject());
        if (lifecycle.phase() != ResourceSitePhase.READY || !action.id().equals(resourceHarvestOpportunity(state, lifecycle, action.dueAt().ticks()).id())) return List.of();
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId());
        SubjectId owner = site.settlementId();
        if (state.strategicPlans().hasActiveObjective(owner, StrategicObjectiveLane.FACILITY)) {
            return List.of(new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(resourceHarvestOpportunity(state, lifecycle,
                    Math.addExact(action.dueAt().ticks(), ResourceSiteHarvestProcess.RETRY_INTERVAL)))));
        }
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        Candidate candidate = new Candidate(StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, Optional.empty(), Optional.of(lifecycle.siteId()), FixedScalar.SCALE);
        StrategicObjective objective = objective(owner, candidate, ordinal); StrategicTask task = task(state, objective);
        return List.of(new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                new ProposedEvent(task.id(), new ScheduleEffect.Created(ResourceSiteHarvestProcess.start(task, Math.addExact(action.dueAt().ticks(), 100L)))));
    }

    private static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean recurring,
                                            boolean allowHiveInterception) {
        SubjectId owner = action.subject(); int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        requireKnownOwner(state.bootstrap(), owner);
        List<ProposedEvent> next = recurring ? List.of(new ProposedEvent(owner,
                new ScheduleEffect.Created(review(owner, ordinal + 1, action.dueAt().ticks() + REVIEW_INTERVAL)))) : List.of();
        List<ProposedEvent> health = state.bootstrap().hive().id().equals(owner) ? List.of()
                : HumanHealthProcess.assess(state, FrontierWorldStateSupport.settlement(state.bootstrap(), owner), action.dueAt().ticks());
        Optional<Candidate> candidate = candidate(state, owner, allowHiveInterception);
        if (candidate.map(Candidate::kind).orElse(null) == StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION
                && HiveRouteEngagementProcess.hasPendingOrActiveInterception(state)) {
            return concatenate(health, next);
        }
        List<ProposedEvent> preempted = preemptForInterception(state, owner, candidate);
        if (state.strategicPlans().hasActiveObjective(owner, StrategicObjectiveLane.STRATEGIC) && preempted.isEmpty()) return concatenate(health, next);
        if (candidate.isEmpty()) return concatenate(health, next);
        Candidate value = candidate.orElseThrow(); StrategicObjective objective = objective(owner, value, ordinal);
        if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE) {
            StrategicTask preparation = cargoPreparationTask(state, objective); StrategicTask delivery = deliveryTask(objective, preparation);
            List<ProposedEvent> events = new java.util.ArrayList<>(preempted);
            events.addAll(List.of(new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(preparation)),
                    new ProposedEvent(owner, new StrategicTaskPlanned(delivery)), new ProposedEvent(owner,
                            new ScheduleEffect.Created(SupplyOperationProcess.start(preparation, action.dueAt().ticks() + 100L)))));
            events.addAll(health); events.addAll(next); return List.copyOf(events);
        }
        StrategicTask task = task(state, objective);
        if (task.kind() == StrategicTaskKind.SPREAD_INFECTION_CELL) {
            return withPreemption(preempted, concatenate(health, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(HiveInfectionProcess.task(task, 1, action.dueAt().ticks() + 100L))));
        }
        if (task.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM) {
            return withPreemption(preempted, concatenate(health, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(HiveGrowthProcess.start(task, action.dueAt().ticks() + 100L))));
        }
        if (task.kind() == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION) {
            return withPreemption(preempted, concatenate(health, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(HiveRouteEngagementProcess.start(task, action.dueAt().ticks() + 100L))));
        }
        if (task.kind() == StrategicTaskKind.PRODUCE_BREAD) {
            return withPreemption(preempted, concatenate(health, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(ProductionProcess.start(task, action.dueAt().ticks() + 100L))));
        }
        if (task.kind() == StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE) {
            return withPreemption(preempted, concatenate(health, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(RoutePatrolProcess.start(task, action.dueAt().ticks() + 100L))));
        }
        if (task.kind() == StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS) {
            return withPreemption(preempted, concatenate(health, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(RouteConstructionProcess.start(task, action.dueAt().ticks() + 100L))));
        }
        return withPreemption(preempted, concatenate(health, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)));
    }

    static FrontierWorldState reduceObjective(FrontierWorldState state, SubjectId subject, StrategicObjectiveSelected selected) {
        if (!subject.equals(selected.objective().ownerId())) throw new IllegalArgumentException("strategic objective has a foreign event owner");
        requireKnownOwner(state.bootstrap(), subject); return state.withStrategicPlans(state.strategicPlans().addObjective(selected.objective()));
    }

    static FrontierWorldState reduceTask(FrontierWorldState state, SubjectId subject, StrategicTaskPlanned planned) {
        StrategicTask task = planned.task(); StrategicObjective objective = state.strategicPlans().objectives().get(task.objectiveId());
        if (objective == null || !subject.equals(task.ownerId()) || !objective.ownerId().equals(subject)) throw new IllegalArgumentException("strategic task has a foreign owner or objective");
        return state.withStrategicPlans(state.strategicPlans().addTask(task));
    }

    static FrontierWorldState reduceTaskTransition(FrontierWorldState state, SubjectId subject, StrategicTaskTransition transition) {
        StrategicTask task = state.strategicPlans().tasks().get(transition.taskId());
        if (task == null || !task.ownerId().equals(subject)) throw new IllegalArgumentException("strategic task transition has a foreign owner");
        return state.withStrategicPlans(state.strategicPlans().transitionTask(task.id(), transition.status()));
    }

    private static Optional<Candidate> candidate(FrontierWorldState state, SubjectId owner, boolean allowHiveInterception) {
        return state.bootstrap().hive().id().equals(owner) ? hiveCandidate(state, allowHiveInterception)
                : settlementCandidate(state, FrontierWorldStateSupport.settlement(state.bootstrap(), owner));
    }
    private static List<ProposedEvent> preemptForInterception(FrontierWorldState state, SubjectId owner, Optional<Candidate> candidate) {
        if (!state.bootstrap().hive().id().equals(owner) || candidate.map(Candidate::kind).orElse(null) != StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION) return List.of();
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(owner))
                .filter(task -> task.status() == StrategicTaskStatus.PENDING || task.status() == StrategicTaskStatus.ACTIVE)
                .sorted(Comparator.comparing(StrategicTask::id)).map(task -> new ProposedEvent(owner, new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED))).toList();
    }
    private static List<ProposedEvent> withPreemption(List<ProposedEvent> preempted, List<ProposedEvent> next, ProposedEvent... events) {
        List<ProposedEvent> result = new java.util.ArrayList<>(preempted);
        result.addAll(List.of(events)); result.addAll(next);
        return List.copyOf(result);
    }
    private static List<ProposedEvent> concatenate(List<ProposedEvent> first, List<ProposedEvent> second) {
        List<ProposedEvent> result = new java.util.ArrayList<>(first); result.addAll(second); return List.copyOf(result);
    }
    private static Optional<Candidate> settlementCandidate(FrontierWorldState state, Settlement settlement) {
        Optional<SettlementStructure> infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .filter(structure -> state.structureConditions().get(structure.id()) != StructureCondition.DESTROYED).min(Comparator.comparing(SettlementStructure::id));
        if (infirmary.isPresent()) {
            SettlementStructure facility = infirmary.orElseThrow();
            Optional<Candidate> containment = state.infection().entrySet().stream().filter(entry -> local(facility, entry.getKey()))
                    .map(entry -> new Candidate(StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(entry.getKey()), entry.getValue().value().raw()))
                    .sorted(Candidate.HIGHEST_UTILITY).findFirst();
            if (containment.isPresent()) return containment;
        }
        boolean constructionActive = state.routeConstructions().values().stream().anyMatch(project -> project.settlementId().equals(settlement.id()));
        boolean alreadyConfirmed = state.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.settlementId().equals(settlement.id())
                && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED && patrol.obstruction().stream().anyMatch(state.physicalDeltas()::containsKey));
        boolean blockedRoute = !FrontierRouteNetwork.isPassable(state.bootstrap(), state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()), state.physicalDeltas());
        if (!constructionActive && alreadyConfirmed) {
            return Optional.of(new Candidate(StrategicObjectiveKind.SETTLEMENT_CONSTRUCT_ROUTE_BYPASS, Optional.empty(), Long.MAX_VALUE));
        }
        if (!constructionActive && !alreadyConfirmed && blockedRoute) {
            return Optional.of(new Candidate(StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE, Optional.empty(), Long.MAX_VALUE));
        }
        boolean workshop = settlement.structures().stream().anyMatch(structure -> structure.kind() == StructureKind.WORKSHOP
                && state.structureConditions().get(structure.id()) == StructureCondition.INTACT);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        boolean wheat = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:wheat")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot));
        if (workshop && wheat && state.inventory().firstFreeSlot(depot).isPresent()) return Optional.of(new Candidate(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), FixedScalar.SCALE));
        boolean bread = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:bread")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot));
        return bread && !state.humanPopulation().quarantined(settlement.id())
                ? Optional.of(new Candidate(StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE, Optional.empty(), FixedScalar.SCALE)) : Optional.empty();
    }
    private static Optional<Candidate> hiveCandidate(FrontierWorldState state, boolean allowInterception) {
        Optional<SubjectId> intercept = allowInterception ? HiveRouteEngagementProcess.targetOperation(state) : Optional.empty();
        if (intercept.isPresent()) return Optional.of(new Candidate(StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), Long.MAX_VALUE));
        Optional<Candidate> growth = hiveGrowthCandidate(state); if (growth.isPresent()) return growth;
        return HiveInfectionProcess.expansionTarget(state).map(target -> new Candidate(StrategicObjectiveKind.HIVE_EXPAND_INFECTION, Optional.of(target),
                Math.subtractExact(FixedScalar.SCALE, state.infection().getOrDefault(target, new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(FixedScalar.ZERO)).value().raw())));
    }
    private static Optional<Candidate> hiveGrowthCandidate(FrontierWorldState state) {
        boolean capacity = state.hiveColony().growthJobs().isEmpty() && state.hiveColony().addedOrgans().size() < HiveColony.MAX_ADDED_ORGANS
                && state.hiveColony().spawnedBioforms().size() < HiveColony.MAX_SPAWNED_BIOFORMS;
        boolean biomass = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:rotten_flesh")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && state.isHiveStore(slot.containerId()));
        return capacity && biomass ? Optional.of(new Candidate(StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), FixedScalar.SCALE)) : Optional.empty();
    }
    private static StrategicObjective objective(SubjectId owner, Candidate candidate, int ordinal) {
        String stem = owner.value().replace(':', '-') + "-" + candidate.kind().name().toLowerCase(java.util.Locale.ROOT) + "-" + ordinal;
        return new StrategicObjective(new SubjectId("objective:" + stem), owner, candidate.kind(), candidate.target(), candidate.resourceSiteTarget(), ordinal,
                StrategicObjectiveStatus.ACTIVE);
    }
    private static StrategicTask task(FrontierWorldState state, StrategicObjective objective) {
        List<StrategicTaskRequirement> requirements = switch (objective.kind()) {
            case SETTLEMENT_CONTAIN_LOCAL_INFECTION -> List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT);
            case HIVE_EXPAND_INFECTION -> List.of(StrategicTaskRequirement.OPERATIONAL_HEART);
            case HIVE_GROW_ORGANISM -> List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS);
            case HIVE_INTERCEPT_ROUTE_OPERATION -> List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD, StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER);
            case SETTLEMENT_PRODUCE_BREAD -> List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT, StrategicTaskRequirement.FREE_DEPOT_SLOT);
            case SETTLEMENT_DELIVER_BREAD_TO_HIVE -> throw new IllegalArgumentException("delivery objective requires its two-task decomposition");
            case SETTLEMENT_PATROL_OBSTRUCTED_ROUTE -> List.of(StrategicTaskRequirement.AVAILABLE_GUARD);
            case SETTLEMENT_CONSTRUCT_ROUTE_BYPASS -> List.of(StrategicTaskRequirement.CONFIRMED_ROUTE_OBSTRUCTION, StrategicTaskRequirement.EXACT_ROUTE_CONSTRUCTION_MATERIAL);
            case SETTLEMENT_HARVEST_RESOURCE_SITE -> List.of(StrategicTaskRequirement.ACTIVE_FARM, StrategicTaskRequirement.AVAILABLE_FARMER,
                    StrategicTaskRequirement.FREE_DEPOT_SLOT);
        };
        StrategicTaskKind kind = switch (objective.kind()) {
            case SETTLEMENT_CONTAIN_LOCAL_INFECTION -> StrategicTaskKind.DECONTAMINATE_INFECTION_CELL;
            case HIVE_EXPAND_INFECTION -> StrategicTaskKind.SPREAD_INFECTION_CELL;
            case HIVE_GROW_ORGANISM -> StrategicTaskKind.GROW_HIVE_ORGANISM;
            case HIVE_INTERCEPT_ROUTE_OPERATION -> StrategicTaskKind.INTERCEPT_ROUTE_OPERATION;
            case SETTLEMENT_PRODUCE_BREAD -> StrategicTaskKind.PRODUCE_BREAD;
            case SETTLEMENT_DELIVER_BREAD_TO_HIVE -> throw new IllegalArgumentException("delivery objective requires its two-task decomposition");
            case SETTLEMENT_PATROL_OBSTRUCTED_ROUTE -> StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE;
            case SETTLEMENT_CONSTRUCT_ROUTE_BYPASS -> StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS;
            case SETTLEMENT_HARVEST_RESOURCE_SITE -> StrategicTaskKind.HARVEST_RESOURCE_SITE;
        };
        Optional<SubjectId> operation = kind == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION ? HiveRouteEngagementProcess.targetOperation(state) : Optional.empty();
        if (kind == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION && operation.isEmpty()) throw new IllegalStateException("route interception lost its target during task creation");
        return new StrategicTask(new SubjectId("task:" + objective.id().value().substring("objective:".length())), objective.id(), objective.ownerId(), kind,
                objective.infectionTarget(), operation, objective.resourceSiteTarget(), requirements, dependencies(state, objective), StrategicTaskStatus.PENDING);
    }
    private static List<SubjectId> dependencies(FrontierWorldState state, StrategicObjective objective) {
        if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_CONSTRUCT_ROUTE_BYPASS) {
            return state.strategicPlans().routePatrols().values().stream().filter(patrol -> patrol.settlementId().equals(objective.ownerId())
                    && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED && patrol.obstruction().stream().anyMatch(state.physicalDeltas()::containsKey))
                    .sorted(Comparator.comparing(RoutePatrol::taskId).reversed()).map(RoutePatrol::taskId).limit(1).toList();
        }
        if (objective.kind() != StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE) return List.of();
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(objective.ownerId())
                && task.kind() == StrategicTaskKind.PRODUCE_BREAD && task.status() == StrategicTaskStatus.COMPLETED)
                .sorted(Comparator.comparing((StrategicTask task) -> state.strategicPlans().objectives().get(task.objectiveId()).decisionOrdinal()).reversed()
                        .thenComparing(StrategicTask::id)).map(StrategicTask::id).limit(1).toList();
    }
    private static StrategicTask cargoPreparationTask(FrontierWorldState state, StrategicObjective objective) {
        return new StrategicTask(taskId(objective, "prepare"), objective.id(), objective.ownerId(), StrategicTaskKind.PREPARE_BREAD_CARGO, Optional.empty(),
                List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), dependencies(state, objective), StrategicTaskStatus.PENDING);
    }
    private static StrategicTask deliveryTask(StrategicObjective objective, StrategicTask preparation) {
        return new StrategicTask(taskId(objective, "deliver"), objective.id(), objective.ownerId(), StrategicTaskKind.DELIVER_BREAD_TO_HIVE, Optional.empty(),
                List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE, StrategicTaskRequirement.AVAILABLE_HAULER, StrategicTaskRequirement.AVAILABLE_GUARD),
                List.of(preparation.id()), StrategicTaskStatus.PENDING);
    }
    private static SubjectId taskId(StrategicObjective objective, String phase) {
        return new SubjectId("task:" + objective.id().value().substring("objective:".length()) + "-" + phase);
    }
    private static boolean local(SettlementStructure facility, InfectionCell cell) {
        BlockPosition position = cell.originAtY(facility.anchor().y()); long dx = position.x() - facility.anchor().x(), dz = position.z() - facility.anchor().z();
        return dx * dx + dz * dz <= LOCAL_INFECTION_RADIUS_SQUARED;
    }
    private static void requireKnownOwner(FrontierBootstrap bootstrap, SubjectId owner) {
        if (!bootstrap.hive().id().equals(owner) && bootstrap.settlements().stream().noneMatch(settlement -> settlement.id().equals(owner))) {
            throw new IllegalArgumentException("strategic review has a foreign owner");
        }
    }
    private record Candidate(StrategicObjectiveKind kind, Optional<InfectionCell> target, Optional<SubjectId> resourceSiteTarget, long utility) {
        Candidate(StrategicObjectiveKind kind, Optional<InfectionCell> target, long utility) {
            this(kind, target, Optional.empty(), utility);
        }
        private static final Comparator<Candidate> HIGHEST_UTILITY = Comparator.comparingLong(Candidate::utility).reversed()
                .thenComparing(Candidate::kind).thenComparing(value -> value.target().map(InfectionCell::x).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.target().map(InfectionCell::z).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.resourceSiteTarget().map(SubjectId::value).orElse(""));
    }
}
