package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

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
public final class StrategicObjectiveProcess {
    private StrategicObjectiveProcess() { }

    public static ScheduledAction review(SubjectId owner, int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:objective-review-" + owner.value().replace(':', '-') + "-" + ordinal),
                new SimInstant(dueAt), 0, owner, "frontier.objective.review", 1);
    }

    /**
     * A physical route fact must wake only the settlements whose actual supply corridor it
     * invalidates.  This is deliberately a one-shot reconsideration: it cannot multiply the
     * ordinary recurring review stream just because one player broke a block.
     */
    public static ScheduledAction routeReconsideration(SubjectId settlementId, BlockPosition obstruction, String trigger, long dueAt) {
        requireKnownRouteTrigger(trigger);
        String owner = settlementId.value().replace(':', '-');
        String position = obstruction.x() + "-" + obstruction.y() + "-" + obstruction.z();
        return new ScheduledAction(new ScheduleId("schedule:objective-route-" + trigger + "-" + owner + "-" + position + "-1"),
                new SimInstant(dueAt), 0, settlementId, "frontier.objective.reconsider", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        return plan(state, action, true, true);
    }

    public static List<ProposedEvent> planReconsideration(FrontierWorldState state, ScheduledAction action) {
        if (!action.kind().equals("frontier.objective.reconsider")) throw new IllegalArgumentException("route reconsideration has an invalid action kind");
        return plan(state, action, false, true, action.id().value());
    }

    /**
     * One verified Scout sighting wakes the hive planner once.  The schedule identity retains
     * the observed fact rather than a route coordinate obtained later from the operation.
     * The planner still reads only the durable perception register when it chooses a task.
     */
    public static ScheduledAction interceptOpportunity(SubjectId hive, HiveOperationKnowledge.Sighting sighting, long dueAt) {
        return new ScheduledAction(interceptOpportunityId(sighting),
                new SimInstant(dueAt), 0, hive, "frontier.objective.interrupt", 1);
    }

    public static List<ProposedEvent> planOpportunity(FrontierWorldState state, ScheduledAction action) {
        if (!state.bootstrap().hive().id().equals(action.subject())) throw new IllegalArgumentException("intercept opportunity has a foreign owner");
        if (!action.kind().equals("frontier.objective.interrupt")) throw new IllegalArgumentException("intercept opportunity has an invalid action kind");
        // The wake-up itself contains no target authority.  It identifies exactly one retained
        // Scout fact, so a second simultaneous sighting cannot retarget this task and a stale
        // wake-up cannot fall through into an unrelated growth objective.
        Optional<HiveOperationKnowledge.Sighting> sighting = state.strategicPlans().hiveOperationKnowledge().entries().values().stream()
                .filter(value -> interceptOpportunityId(value).equals(action.id()))
                .filter(value -> value.observedAt() >= Math.subtractExact(action.dueAt().ticks(),
                        state.bootstrap().ruleset().cadence().hivePerceptionRefreshInterval())).findFirst();
        if (sighting.isEmpty()) {
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        }
        return plan(state, action, false, true, action.id().value(), sighting);
    }

    /** One exact Scout settlement sighting wakes a one-shot assault admission check. */
    public static ScheduledAction assaultOpportunity(SubjectId hive, HiveSettlementKnowledge.Sighting sighting, long dueAt) {
        return new ScheduledAction(assaultOpportunityId(sighting), new SimInstant(dueAt), 0, hive, "frontier.objective.assault", 1);
    }

    public static List<ProposedEvent> planAssaultOpportunity(FrontierWorldState state, ScheduledAction action) {
        SubjectId hive = state.bootstrap().hive().id();
        if (!hive.equals(action.subject()) || !action.kind().equals("frontier.objective.assault")) {
            throw new IllegalArgumentException("settlement assault opportunity has a foreign owner or invalid kind");
        }
        Optional<HiveSettlementKnowledge.Sighting> sighting = state.strategicPlans().hiveSettlementKnowledge().entries().values().stream()
                .filter(value -> assaultOpportunityId(value).equals(action.id()))
                .filter(value -> value.observedAt() >= Math.subtractExact(action.dueAt().ticks(),
                        state.bootstrap().ruleset().cadence().hiveSettlementKnowledgeMaxAge())).findFirst();
        if (sighting.isEmpty() || state.strategicPlans().hiveDoctrine().doctrine() != HiveDoctrine.INTERDICT
                || !HiveSettlementAssaultProcess.hasFreshLocalTerritory(state, sighting.orElseThrow(), action.dueAt().ticks())
                || HiveSettlementAssaultProcess.hasPendingOrActiveAssault(state, sighting.orElseThrow().settlementId())) {
            return List.of(new ProposedEvent(hive, new ScheduleEffect.Cancelled(action.id())));
        }
        List<ProposedEvent> preempted = state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(hive))
                .filter(task -> task.status() == StrategicTaskStatus.PENDING || task.status() == StrategicTaskStatus.ACTIVE)
                .sorted(Comparator.comparing(StrategicTask::id)).map(task -> new ProposedEvent(hive, new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED))).toList();
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:" + action.id().value().substring("schedule:".length())), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), ordinal, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:" + objective.id().value().substring("objective:".length())), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        List<ProposedEvent> events = new java.util.ArrayList<>(preempted);
        events.add(new ProposedEvent(hive, new StrategicObjectiveSelected(objective)));
        events.add(new ProposedEvent(hive, new StrategicTaskPlanned(task)));
        events.add(new ProposedEvent(task.id(), new ScheduleEffect.Created(HiveSettlementAssaultProcess.start(task, sighting.orElseThrow(), action.dueAt().ticks() + 1L))));
        return List.copyOf(events);
    }

    /** Development profiles may retain the same economy without admitting a competing hive strike. */
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean allowHiveInterception) {
        return plan(state, action, true, allowHiveInterception);
    }

    /** A ready exact field asks its settlement planner for work without bypassing durable task ownership. */
    public static ScheduledAction resourceHarvestOpportunity(FrontierWorldState state, ResourceSiteLifecycle lifecycle, long dueAt) {
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId());
        if (site == null || lifecycle.phase() != ResourceSitePhase.READY) throw new IllegalArgumentException("resource harvest opportunity requires a ready known field");
        String suffix = lifecycle.siteId().value().substring("site:".length());
        return new ScheduledAction(new ScheduleId("schedule:objective-resource-harvest-" + suffix + "-" + lifecycle.growthEpoch() + "-" + dueAt),
                new SimInstant(dueAt), 0, lifecycle.siteId(), "frontier.objective.resource_harvest", 1);
    }

    public static List<ProposedEvent> planResourceHarvestOpportunity(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(action.subject());
        if (lifecycle.phase() != ResourceSitePhase.READY || !action.id().equals(resourceHarvestOpportunity(state, lifecycle, action.dueAt().ticks()).id())) return List.of();
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId());
        SubjectId owner = site.settlementId();
        if (state.strategicPlans().hasActiveObjective(owner, StrategicObjectiveLane.FACILITY)) {
            return List.of(new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(resourceHarvestOpportunity(state, lifecycle,
                    Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval())))));
        }
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        Candidate candidate = new Candidate(StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, Optional.empty(), Optional.of(lifecycle.siteId()), FixedScalar.SCALE);
        StrategicObjective objective = objective(owner, candidate, ordinal); StrategicTask task = task(state, objective);
        return List.of(new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                new ProposedEvent(task.id(), new ScheduleEffect.Created(ResourceSiteHarvestProcess.start(task, Math.addExact(action.dueAt().ticks(), 100L)))));
    }

    private static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean recurring,
                                            boolean allowHiveInterception) {
        return plan(state, action, recurring, allowHiveInterception, null);
    }

    private static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean recurring,
                                            boolean allowHiveInterception, String eventIdentity) {
        return plan(state, action, recurring, allowHiveInterception, eventIdentity, Optional.empty());
    }

    private static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean recurring,
                                            boolean allowHiveInterception, String eventIdentity, Optional<HiveOperationKnowledge.Sighting> interceptSighting) {
        SubjectId owner = action.subject(); int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        requireKnownOwner(state.bootstrap(), owner);
        List<ProposedEvent> next = recurring ? List.of(new ProposedEvent(owner,
                new ScheduleEffect.Created(review(owner, ordinal + 1, action.dueAt().ticks()
                        + state.bootstrap().ruleset().cadence().strategicReviewInterval())))) : List.of();
        List<ProposedEvent> health = state.bootstrap().hive().id().equals(owner) ? List.of()
                : HumanHealthProcess.assess(state, FrontierWorldStateSupport.settlement(state.bootstrap(), owner), action.dueAt().ticks());
        boolean hive = state.bootstrap().hive().id().equals(owner);
        List<ProposedEvent> medical = hive ? List.of() : MedicalTreatmentProcess.planStart(state, owner, ordinal);
        SettlementPerceptionProcess.Refresh perception = hive ? new SettlementPerceptionProcess.Refresh(state.strategicPlans().infectionKnowledge(), List.of())
                : SettlementPerceptionProcess.refreshLocalInfection(state, FrontierWorldStateSupport.settlement(state.bootstrap(), owner), action.dueAt().ticks());
        HivePerceptionProcess.Refresh hivePerception = hive ? HivePerceptionProcess.refresh(state, action.dueAt().ticks())
                : new HivePerceptionProcess.Refresh(state.strategicPlans().hiveOperationKnowledge(), List.of());
        HiveTerritoryPerceptionProcess.Refresh territoryPerception = hive ? HiveTerritoryPerceptionProcess.refresh(state, action.dueAt().ticks())
                : new HiveTerritoryPerceptionProcess.Refresh(state.strategicPlans().hiveTerritoryKnowledge(), List.of());
        HiveSettlementPerceptionProcess.Refresh settlementPerception = hive ? HiveSettlementPerceptionProcess.refresh(state, action.dueAt().ticks())
                : new HiveSettlementPerceptionProcess.Refresh(state.strategicPlans().hiveSettlementKnowledge(), List.of());
        HiveDoctrineState doctrine = hive ? HiveDoctrineProcess.select(state.withStrategicPlans(state.strategicPlans()
                .withHiveOperationKnowledge(hivePerception.knowledge()).withHiveTerritoryKnowledge(territoryPerception.knowledge())
                .withHiveSettlementKnowledge(settlementPerception.knowledge())), action.dueAt().ticks(), allowHiveInterception)
                : state.strategicPlans().hiveDoctrine();
        FrontierWorldState decisionState = state.withStrategicPlans(state.strategicPlans().withInfectionKnowledge(perception.knowledge())
                .withHiveOperationKnowledge(hivePerception.knowledge()).withHiveTerritoryKnowledge(territoryPerception.knowledge())
                .withHiveSettlementKnowledge(settlementPerception.knowledge()).withHiveDoctrine(doctrine));
        List<ProposedEvent> doctrineEvent = hive && !doctrine.equals(state.strategicPlans().hiveDoctrine())
                ? List.of(new ProposedEvent(owner, new HiveDoctrineSelected(doctrine))) : List.of();
        List<ProposedEvent> observedAndHealth = concatenate(concatenate(concatenate(concatenate(concatenate(concatenate(perception.events(), hivePerception.events()),
                territoryPerception.events()), settlementPerception.events()), doctrineEvent), health), medical);
        Optional<Candidate> candidate = candidate(decisionState, owner, allowHiveInterception, action.dueAt().ticks(), interceptSighting);
        if (candidate.map(Candidate::kind).orElse(null) == StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION
                && HiveRouteEngagementProcess.hasPendingOrActiveInterception(state)) {
            return concatenate(observedAndHealth, next);
        }
        List<ProposedEvent> preempted = new java.util.ArrayList<>(preemptForInterception(state, owner, candidate));
        candidate.filter(value -> emergencyFoodCandidate(state, owner, value)).ifPresent(ignored -> preempted.addAll(preemptForEmergencyProvision(state, owner)));
        if (candidate.isEmpty()) return concatenate(observedAndHealth, next);
        Candidate value = candidate.orElseThrow(); StrategicObjective objective = objective(owner, value, ordinal, eventIdentity);
        if (state.strategicPlans().hasActiveObjective(owner, objective.lane()) && preempted.isEmpty()) return concatenate(observedAndHealth, next);
        if (objective.kind() == StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE) {
            StrategicTask preparation = cargoPreparationTask(state, objective); StrategicTask delivery = deliveryTask(objective, preparation);
            List<ProposedEvent> events = new java.util.ArrayList<>(perception.events()); events.addAll(preempted);
            events.addAll(List.of(new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(preparation)),
                    new ProposedEvent(owner, new StrategicTaskPlanned(delivery)), new ProposedEvent(owner,
                            new ScheduleEffect.Created(SupplyOperationProcess.start(preparation, action.dueAt().ticks() + 100L)))));
            events.addAll(health); events.addAll(medical); events.addAll(next); return List.copyOf(events);
        }
        StrategicTask task = task(state, objective, value.operationTarget(), value.operationObservationPosition());
        if (task.kind() == StrategicTaskKind.SPREAD_INFECTION_CELL) {
            return withPreemption(preempted, concatenate(observedAndHealth, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(HiveInfectionProcess.task(task, 1, action.dueAt().ticks() + 100L))));
        }
        if (task.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM) {
            return withPreemption(preempted, concatenate(observedAndHealth, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(HiveGrowthProcess.start(task, action.dueAt().ticks() + 100L))));
        }
        if (task.kind() == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION) {
            return withPreemption(preempted, concatenate(observedAndHealth, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(HiveRouteEngagementProcess.start(task, action.dueAt().ticks() + 1L))));
        }
        if (task.kind() == StrategicTaskKind.PRODUCE_BREAD) {
            MarketDemand demand = MarketClearingProcess.foodDemand(state, task, action.dueAt().ticks());
            return withPreemption(preempted, concatenate(observedAndHealth, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new MarketDemandOpened(demand)),
                    new ProposedEvent(demand.id(), new ScheduleEffect.Created(MarketClearingProcess.clear(demand, 1, action.dueAt().ticks() + 100L))));
        }
        if (task.kind() == StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE) {
            return withPreemption(preempted, concatenate(observedAndHealth, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(RoutePatrolProcess.start(task, action.dueAt().ticks() + 100L))));
        }
        if (task.kind() == StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS) {
            return withPreemption(preempted, concatenate(observedAndHealth, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                    new ProposedEvent(owner, new ScheduleEffect.Created(RouteConstructionProcess.start(task, action.dueAt().ticks() + 100L))));
        }
        return withPreemption(preempted, concatenate(observedAndHealth, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)));
    }

    public static FrontierWorldState reduceObjective(FrontierWorldState state, SubjectId subject, StrategicObjectiveSelected selected) {
        if (!subject.equals(selected.objective().ownerId())) throw new IllegalArgumentException("strategic objective has a foreign event owner");
        requireKnownOwner(state.bootstrap(), subject); return state.withStrategicPlans(state.strategicPlans().addObjective(selected.objective()));
    }

    public static FrontierWorldState reduceTask(FrontierWorldState state, SubjectId subject, StrategicTaskPlanned planned) {
        StrategicTask task = planned.task(); StrategicObjective objective = state.strategicPlans().objectives().get(task.objectiveId());
        if (objective == null || !subject.equals(task.ownerId()) || !objective.ownerId().equals(subject)) throw new IllegalArgumentException("strategic task has a foreign owner or objective");
        return state.withStrategicPlans(state.strategicPlans().addTask(task));
    }

    public static FrontierWorldState reduceTaskTransition(FrontierWorldState state, SubjectId subject, StrategicTaskTransition transition) {
        StrategicTask task = state.strategicPlans().tasks().get(transition.taskId());
        if (task == null || !task.ownerId().equals(subject)) throw new IllegalArgumentException("strategic task transition has a foreign owner");
        return state.withStrategicPlans(state.strategicPlans().transitionTask(task.id(), transition.status()));
    }

    private static Optional<Candidate> candidate(FrontierWorldState state, SubjectId owner, boolean allowHiveInterception, long now,
                                                  Optional<HiveOperationKnowledge.Sighting> interceptSighting) {
        return state.bootstrap().hive().id().equals(owner) ? hiveCandidate(state, allowHiveInterception, now, interceptSighting)
                : settlementCandidate(state, FrontierWorldStateSupport.settlement(state.bootstrap(), owner));
    }
    private static List<ProposedEvent> preemptForInterception(FrontierWorldState state, SubjectId owner, Optional<Candidate> candidate) {
        if (!state.bootstrap().hive().id().equals(owner) || candidate.map(Candidate::kind).orElse(null) != StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION) return List.of();
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(owner))
                .filter(task -> task.status() == StrategicTaskStatus.PENDING || task.status() == StrategicTaskStatus.ACTIVE)
                .sorted(Comparator.comparing(StrategicTask::id)).map(task -> new ProposedEvent(owner, new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED))).toList();
    }
    private static boolean emergencyFoodCandidate(FrontierWorldState state, SubjectId owner, Candidate candidate) {
        return candidate.kind() == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD && !state.bootstrap().hive().id().equals(owner)
                && SettlementProvisionProcess.availableFood(state, owner) < SettlementProvisionProcess.reserveRequirement(state, owner);
    }
    private static List<ProposedEvent> preemptForEmergencyProvision(FrontierWorldState state, SubjectId settlementId) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(settlementId))
                .filter(task -> task.kind() == StrategicTaskKind.DECONTAMINATE_INFECTION_CELL && task.status() == StrategicTaskStatus.PENDING)
                .sorted(Comparator.comparing(StrategicTask::id)).map(task -> new ProposedEvent(settlementId,
                        new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED))).toList();
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
        boolean workshop = settlement.structures().stream().anyMatch(structure -> structure.kind() == StructureKind.WORKSHOP
                && state.structureConditions().get(structure.id()) == StructureCondition.INTACT);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        boolean reserveShort = SettlementProvisionProcess.availableFood(state, settlement.id()) < SettlementProvisionProcess.reserveRequirement(state, settlement.id());
        boolean wheat = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:wheat")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot));
        boolean constructionActive = state.routeConstructions().values().stream().anyMatch(project -> project.settlementId().equals(settlement.id()));
        boolean alreadyConfirmed = state.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.settlementId().equals(settlement.id())
                && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED && patrol.obstruction().stream().anyMatch(state.physicalDeltas()::containsKey));
        boolean blockedRoute = !FrontierRouteNetwork.isPassable(state.bootstrap(), state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()), state.physicalDeltas());
        // A confirmed physical logistics failure outranks ordinary production and containment
        // selection.  It does not cancel an already active task; lane ownership remains the
        // sole authority for that decision.
        if (!constructionActive && alreadyConfirmed) {
            return Optional.of(new Candidate(StrategicObjectiveKind.SETTLEMENT_CONSTRUCT_ROUTE_BYPASS, Optional.empty(), Long.MAX_VALUE));
        }
        if (!constructionActive && !alreadyConfirmed && blockedRoute) {
            return Optional.of(new Candidate(StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE, Optional.empty(), Long.MAX_VALUE));
        }
        // Food may preempt a pending containment task that is waiting for an infirmary reagent,
        // but never a physical effect already under execution.
        if (workshop && wheat && reserveShort) {
            return Optional.of(new Candidate(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), Long.MAX_VALUE - 1L));
        }
        Optional<SettlementStructure> infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .filter(structure -> state.structureConditions().get(structure.id()) != StructureCondition.DESTROYED).min(Comparator.comparing(SettlementStructure::id));
        if (infirmary.isPresent()) {
            Optional<Candidate> containment = state.strategicPlans().infectionKnowledge().known(settlement.id()).values().stream()
                    .map(known -> new Candidate(StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(known.cell()), known.intensity().value().raw()))
                    .sorted(Candidate.HIGHEST_UTILITY).findFirst();
            if (containment.isPresent()) return containment;
        }
        if (workshop && wheat) return Optional.of(new Candidate(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(),
                reserveShort ? Long.MAX_VALUE - 1L : FixedScalar.SCALE));
        return SettlementProvisionProcess.exportableBread(state, settlement.id()).isPresent() && !state.humanPopulation().quarantined(settlement.id())
                ? Optional.of(new Candidate(StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE, Optional.empty(), FixedScalar.SCALE)) : Optional.empty();
    }
    private static Optional<Candidate> hiveCandidate(FrontierWorldState state, boolean allowInterception, long now,
                                                      Optional<HiveOperationKnowledge.Sighting> interceptSighting) {
        Optional<HiveOperationKnowledge.Sighting> sighted = allowInterception
                ? interceptSighting.or(() -> state.strategicPlans().hiveOperationKnowledge().freshest(now,
                        state.bootstrap().ruleset().cadence().hivePerceptionRefreshInterval())) : Optional.empty();
        if (state.strategicPlans().hiveDoctrine().doctrine() == HiveDoctrine.INTERDICT && sighted.isPresent()) {
            HiveOperationKnowledge.Sighting observation = sighted.orElseThrow();
            return Optional.of(new Candidate(StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.empty(),
                    Optional.of(observation.operationId()), Optional.of(observation.position()), Long.MAX_VALUE));
        }
        if (state.strategicPlans().hiveDoctrine().doctrine() == HiveDoctrine.CONSOLIDATE) return hiveGrowthCandidate(state);
        if (state.strategicPlans().hiveDoctrine().doctrine() != HiveDoctrine.EXPAND) return Optional.empty();
        return HiveInfectionProcess.expansionTarget(state, now).map(target -> new Candidate(StrategicObjectiveKind.HIVE_EXPAND_INFECTION, Optional.of(target),
                Math.subtractExact(FixedScalar.SCALE, state.strategicPlans().hiveTerritoryKnowledge().freshInfection(state.bootstrap().ruleset(), now)
                        .getOrDefault(target, new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(FixedScalar.ZERO)).value().raw())));
    }
    private static Optional<Candidate> hiveGrowthCandidate(FrontierWorldState state) {
        boolean capacity = state.hiveColony().growthJobs().isEmpty() && state.hiveColony().addedOrgans().size() < HiveColony.MAX_ADDED_ORGANS
                && state.hiveColony().spawnedBioforms().size() < HiveColony.MAX_SPAWNED_BIOFORMS;
        boolean biomass = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:rotten_flesh")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && state.isHiveStore(slot.containerId()));
        return capacity && biomass ? Optional.of(new Candidate(StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), FixedScalar.SCALE)) : Optional.empty();
    }
    private static StrategicObjective objective(SubjectId owner, Candidate candidate, int ordinal) {
        return objective(owner, candidate, ordinal, null);
    }
    private static ScheduleId interceptOpportunityId(HiveOperationKnowledge.Sighting sighting) {
        String suffix = sighting.operationId().value().replace(':', '-') + "-" + sighting.scoutId().value().replace(':', '-')
                + "-" + sighting.position().x() + "-" + sighting.position().y() + "-" + sighting.position().z()
                + "-" + sighting.observedAt();
        return new ScheduleId("schedule:objective-intercept-opportunity-" + suffix);
    }
    private static ScheduleId assaultOpportunityId(HiveSettlementKnowledge.Sighting sighting) {
        String suffix = sighting.settlementId().value().replace(':', '-') + "-" + sighting.scoutId().value().replace(':', '-')
                + "-" + sighting.settlementAnchor().x() + "-" + sighting.settlementAnchor().y() + "-" + sighting.settlementAnchor().z()
                + "-" + sighting.observedAt();
        return new ScheduleId("schedule:objective-assault-opportunity-" + suffix);
    }
    private static StrategicObjective objective(SubjectId owner, Candidate candidate, int ordinal, String eventIdentity) {
        String stem = eventIdentity == null ? owner.value().replace(':', '-') + "-" + candidate.kind().name().toLowerCase(java.util.Locale.ROOT) + "-" + ordinal
                : eventIdentity.substring("schedule:".length());
        return new StrategicObjective(new SubjectId("objective:" + stem), owner, candidate.kind(), candidate.target(), candidate.resourceSiteTarget(), ordinal,
                StrategicObjectiveStatus.ACTIVE);
    }
    private static void requireKnownRouteTrigger(String trigger) {
        if (!trigger.equals("loss") && !trigger.equals("confirmed")) throw new IllegalArgumentException("route reconsideration has an unknown trigger");
    }
    private static StrategicTask task(FrontierWorldState state, StrategicObjective objective) { return task(state, objective, Optional.empty(), Optional.empty()); }
    private static StrategicTask task(FrontierWorldState state, StrategicObjective objective, Optional<SubjectId> observedOperation,
                                      Optional<BlockPosition> operationObservationPosition) {
        List<StrategicTaskRequirement> requirements = switch (objective.kind()) {
            case SETTLEMENT_CONTAIN_LOCAL_INFECTION -> List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT);
            case HIVE_EXPAND_INFECTION -> List.of(StrategicTaskRequirement.OPERATIONAL_HEART);
            case HIVE_GROW_ORGANISM -> List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS);
            case HIVE_INTERCEPT_ROUTE_OPERATION -> List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD, StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER);
            case HIVE_ASSAULT_SETTLEMENT -> List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD, StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER);
            // Wheat-to-bread is an exact one-for-one replacement in the same owned
            // slot. Requiring a second vacant depot slot would incorrectly block a
            // full warehouse despite a completely safe transformation path.
            case SETTLEMENT_PRODUCE_BREAD -> List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT);
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
            case HIVE_ASSAULT_SETTLEMENT -> StrategicTaskKind.ASSAULT_SETTLEMENT;
            case SETTLEMENT_PRODUCE_BREAD -> StrategicTaskKind.PRODUCE_BREAD;
            case SETTLEMENT_DELIVER_BREAD_TO_HIVE -> throw new IllegalArgumentException("delivery objective requires its two-task decomposition");
            case SETTLEMENT_PATROL_OBSTRUCTED_ROUTE -> StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE;
            case SETTLEMENT_CONSTRUCT_ROUTE_BYPASS -> StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS;
            case SETTLEMENT_HARVEST_RESOURCE_SITE -> StrategicTaskKind.HARVEST_RESOURCE_SITE;
        };
        Optional<SubjectId> operation = kind == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION ? observedOperation : Optional.empty();
        Optional<BlockPosition> observation = kind == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION ? operationObservationPosition : Optional.empty();
        if (kind == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION && (operation.isEmpty() || observation.isEmpty())) {
            throw new IllegalArgumentException("hive interception requires one exact scout sighting");
        }
        return new StrategicTask(new SubjectId("task:" + objective.id().value().substring("objective:".length())), objective.id(), objective.ownerId(), kind,
                objective.infectionTarget(), operation, objective.resourceSiteTarget(), requirements, dependencies(state, objective), StrategicTaskStatus.PENDING, observation);
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
    private static void requireKnownOwner(FrontierBootstrap bootstrap, SubjectId owner) {
        if (!bootstrap.hive().id().equals(owner) && bootstrap.settlements().stream().noneMatch(settlement -> settlement.id().equals(owner))) {
            throw new IllegalArgumentException("strategic review has a foreign owner");
        }
    }
    private record Candidate(StrategicObjectiveKind kind, Optional<InfectionCell> target, Optional<SubjectId> resourceSiteTarget,
                             Optional<SubjectId> operationTarget, Optional<BlockPosition> operationObservationPosition, long utility) {
        Candidate(StrategicObjectiveKind kind, Optional<InfectionCell> target, long utility) {
            this(kind, target, Optional.empty(), Optional.empty(), Optional.empty(), utility);
        }
        Candidate(StrategicObjectiveKind kind, Optional<InfectionCell> target, Optional<SubjectId> resourceSiteTarget, long utility) {
            this(kind, target, resourceSiteTarget, Optional.empty(), Optional.empty(), utility);
        }
        private static final Comparator<Candidate> HIGHEST_UTILITY = Comparator.comparingLong(Candidate::utility).reversed()
                .thenComparing(Candidate::kind).thenComparing(value -> value.target().map(InfectionCell::x).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.target().map(InfectionCell::z).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.resourceSiteTarget().map(SubjectId::value).orElse(""))
                .thenComparing(value -> value.operationTarget().map(SubjectId::value).orElse(""))
                .thenComparing(value -> value.operationObservationPosition().map(BlockPosition::x).orElse(Integer.MIN_VALUE))
                .thenComparing(value -> value.operationObservationPosition().map(BlockPosition::z).orElse(Integer.MIN_VALUE));
    }
}
