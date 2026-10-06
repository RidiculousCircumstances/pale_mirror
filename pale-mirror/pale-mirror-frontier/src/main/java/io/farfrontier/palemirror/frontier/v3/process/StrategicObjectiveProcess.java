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

    /** One confirmed field-to-depot transfer wakes the owning settlement, not a baker directly. */
    public static ScheduledAction stockReconsideration(SubjectId settlementId, SubjectId harvestJobId,
                                                        int deliveredBefore, long dueAt) {
        if (deliveredBefore < -1 || !settlementId.value().startsWith("settlement:")
                || !harvestJobId.value().startsWith("job:site-harvest-"))
            throw new IllegalArgumentException("stock wake has no declared settlement or harvest cause");
        String part = deliveredBefore < 0 ? "terminal" : "part-" + deliveredBefore;
        return new ScheduledAction(new ScheduleId("schedule:objective-stock-"
                + harvestJobId.value().substring("job:".length()) + "-" + part + "-1"),
                new SimInstant(dueAt), 0, settlementId, "frontier.objective.stock_reconsider", 1);
    }

    /** One durable, cause-identified wake after a player changes an owned depot's stock. */
    public static ScheduledAction playerStockReconsideration(SubjectId settlementId,
                                                             java.util.UUID interactionId, long dueAt) {
        if (!settlementId.value().startsWith("settlement:"))
            throw new IllegalArgumentException("player stock wake requires one settlement owner");
        return new ScheduledAction(new ScheduleId("schedule:objective-stock-player-"
                + interactionId.toString().replace("-", "") + "-1"), new SimInstant(dueAt),
                0, settlementId, "frontier.objective.stock_reconsider", 1);
    }

    public static List<ProposedEvent> planStockReconsideration(FrontierWorldState state, ScheduledAction action) {
        if (!action.kind().equals("frontier.objective.stock_reconsider")
                || !action.subject().value().startsWith("settlement:")
                || state.bootstrap().settlements().stream().noneMatch(value -> value.id().equals(action.subject())))
            throw new IllegalArgumentException("stock wake has a foreign settlement owner or action kind");
        // The wake is only a causal hint. Policy re-reads current stock, worker, station and
        // active lane before it may create a task. The recurring review remains a backstop.
        var events = new java.util.ArrayList<>(plan(state, action, false, false, action.id().value()));
        events.addAll(GoodsParticipantWakeup.container(state, FrontierWorldState.depotId(action.subject()), action.id().value(), action.dueAt().ticks()));
        return List.copyOf(events);
    }

    /** Station release is an availability signal; settlement policy remains the assignment owner. */
    public static ScheduledAction stationReconsideration(ProductionJob released, String receiptCause, long dueAt) {
        if (released.reservesFacility()) throw new IllegalArgumentException("station wake precedes actual release");
        return new ScheduledAction(new ScheduleId("schedule:objective-station-release-"
                + WorkOpportunityIdentity.digest(released.id().value() + "|" + receiptCause) + "-1"),
                new SimInstant(dueAt), 0, released.settlementId(), "frontier.objective.stock_reconsider", 1);
    }

    /** Availability carries an exact resident cause, not authority to choose a concrete job. */
    public static ScheduledAction workforceReconsideration(ResidentProfile resident, ScheduledAction review, long dueAt) {
        if (!review.subject().equals(resident.id())) throw new IllegalArgumentException("work wake has a foreign resident");
        return new ScheduledAction(new ScheduleId("schedule:objective-workforce-"
                + WorkOpportunityIdentity.digest(resident.id().value() + "|" + review.id().value()
                + "|" + review.dueAt().ticks()) + "-1"), new SimInstant(dueAt), 0,
                resident.settlementId(), "frontier.objective.stock_reconsider", 1);
    }

    /** A settled ration wakes only the retained pending field work of that settlement. */
    public static ScheduledAction provisionReconsideration(SettlementProvision provision, long dueAt) {
        String owner = provision.settlementId().value().replace(':', '-');
        return new ScheduledAction(new ScheduleId("schedule:objective-provision-secure-" + owner + "-" + provision.cycleOrdinal()),
                new SimInstant(dueAt), 0, provision.settlementId(), "frontier.objective.provision_reconsider", 1);
    }

    public static List<ProposedEvent> planProvisionReconsideration(FrontierWorldState state, ScheduledAction action) {
        SettlementProvision provision = state.humanPopulation().provision(action.subject());
        if (provision.status() != SettlementProvisionStatus.SECURE || !action.kind().equals("frontier.objective.provision_reconsider")
                || !action.id().equals(provisionReconsideration(provision, action.dueAt().ticks()).id())) return List.of();
        return pendingHarvestStarts(state, action.subject(), action.dueAt().ticks());
    }

    /**
     * A physical route fact must wake only the settlements whose actual supply corridor it
     * invalidates.  This is deliberately a one-shot reconsideration: it cannot multiply the
     * ordinary recurring review stream just because one player broke a block.
     */
    public static ScheduledAction routeReconsideration(SubjectId settlementId, BlockPosition obstruction, String trigger, long dueAt) {
        return routeReconsideration(settlementId, obstruction, trigger, dueAt, 1);
    }

    /** The trigger is causal identity, so failure confirmation cannot collide with patrol confirmation. */
    private static ScheduledAction routeReconsideration(SubjectId settlementId, BlockPosition obstruction, String trigger, long dueAt, int ordinal) {
        requireKnownRouteTrigger(trigger);
        if (ordinal <= 0) throw new IllegalArgumentException("route reconsideration ordinal must be positive");
        String owner = settlementId.value().replace(':', '-');
        String position = obstruction.x() + "-" + obstruction.y() + "-" + obstruction.z();
        return new ScheduledAction(new ScheduleId("schedule:objective-route-" + trigger + "-" + owner + "-" + position + "-" + ordinal),
                new SimInstant(dueAt), RoutePatrolProcess.REACTIVE_INSPECTION_PRIORITY, settlementId, "frontier.objective.reconsider", 1);
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
        return planOpportunity(state, action, action.dueAt().ticks());
    }
    public static List<ProposedEvent> planOpportunity(FrontierWorldState state, ScheduledAction action, long currentTick) {
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
        return plan(state, action, false, true, action.id().value(), sighting, currentTick);
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
        DecisionAuthority authority = state.strategicPlans().requireDecisionAuthority(hive); DecisionPolicyRegistry.require(authority);
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:" + action.id().value().substring("schedule:".length())), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), Optional.empty(), ordinal, StrategicObjectiveStatus.ACTIVE,
                authority.ownerId(), authority.reconsiderationEpoch());
        StrategicTask task = new StrategicTask(new SubjectId("task:" + objective.id().value().substring("objective:".length())), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), Optional.empty(), Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING, Optional.empty(), authority.ownerId(), authority.reconsiderationEpoch());
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

    /** A deferred policy admission records execution at commit time, never backdates it to the due hint. */
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                                          boolean allowHiveInterception, long currentTick) {
        return plan(state, action, true, allowHiveInterception, null, Optional.empty(), currentTick);
    }

    /** A ready exact field asks its settlement planner for work without bypassing durable task ownership. */
    public static ScheduledAction resourceHarvestOpportunity(FrontierWorldState state, ResourceSiteLifecycle lifecycle, long dueAt) {
        return resourceHarvestOpportunity(state, lifecycle, dueAt, "clock");
    }

    public static ScheduledAction resourceHarvestOpportunity(FrontierWorldState state, ResourceSiteLifecycle lifecycle,
                                                              long dueAt, String cause) {
        ResourceSite site = state.resourceSite(lifecycle.siteId());
        if (site == null || lifecycle.phase() != ResourceSitePhase.READY && lifecycle.phase() != ResourceSitePhase.HARVESTING)
            throw new IllegalArgumentException("resource harvest opportunity requires a workable known field");
        String suffix = lifecycle.siteId().value().substring("site:".length());
        return new ScheduledAction(new ScheduleId("schedule:objective-resource-harvest-" + suffix + "-"
                + lifecycle.growthEpoch() + "-" + WorkOpportunityIdentity.digest(cause) + "-" + dueAt),
                new SimInstant(dueAt), 0, lifecycle.siteId(), "frontier.objective.resource_harvest", 1);
    }

    public static List<ProposedEvent> planResourceHarvestOpportunity(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(action.subject());
        if (!action.kind().equals("frontier.objective.resource_harvest"))
            throw new IllegalArgumentException("field opportunity has a foreign scheduled kind");
        if (lifecycle.phase() == ResourceSitePhase.HARVESTING) {
            var retained = state.strategicPlans().tasks().values().stream()
                    .filter(task -> task.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE
                            && task.status() == StrategicTaskStatus.ACTIVE
                            && task.resourceSiteTarget().equals(Optional.of(lifecycle.siteId())))
                    .reduce((left, right) -> { throw new IllegalArgumentException("field has competing active tasks"); });
            return retained.map(task -> ResourceSiteHarvestPlanning.expandActiveTask(state, task, action.dueAt().ticks()))
                    .orElse(List.of());
        }
        if (lifecycle.phase() != ResourceSitePhase.READY) return List.of();
        ResourceSite site = state.resourceSite(lifecycle.siteId());
        SubjectId owner = site.settlementId();
        if (!SettlementManagement.available(state, owner, new StrategicOperationProposal(
                StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, Optional.empty(), Optional.of(site.id()), FixedScalar.SCALE))) {
            return List.of(new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(resourceHarvestOpportunity(state, lifecycle,
                    Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval())))));
        }
        // A full depot is a reversible admission condition, not a failed harvest.
        // Keep the one exact opportunity alive without creating a doomed task.
        if (state.firstFreeContainerSlot(FrontierWorldState.depotId(owner)).isEmpty()) {
            return List.of(new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(resourceHarvestOpportunity(state, lifecycle,
                    Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().strategicReviewInterval())))));
        }
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        StrategicOperationProposal candidate = new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, Optional.empty(), Optional.of(lifecycle.siteId()), FixedScalar.SCALE);
        DecisionPolicyRegistry.require(state.strategicPlans().requireDecisionAuthority(owner));
        StrategicObjective objective = objective(state, owner, candidate, ordinal); StrategicTask task = task(state, objective);
        // The ready-field opportunity has already passed its durable planner boundary.  The
        // bounded start offset reserves the planner's ordinary owner-handoff turn without
        // consuming the entire pre-ingress COLD window.  A full hundred ticks used to be harmless
        // while farmers were implicitly placed close to their work, but now steals the beginning
        // of their retained home-to-field traversal.
        return List.of(new ProposedEvent(owner, new StrategicObjectiveSelected(objective)), new ProposedEvent(owner, new StrategicTaskPlanned(task)),
                new ProposedEvent(task.id(), new ScheduleEffect.Created(ResourceSiteHarvestProcess.start(task, Math.addExact(action.dueAt().ticks(), 85L)))));
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
        return plan(state, action, recurring, allowHiveInterception, eventIdentity, interceptSighting, action.dueAt().ticks());
    }

    private static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, boolean recurring,
                                            boolean allowHiveInterception, String eventIdentity,
                                            Optional<HiveOperationKnowledge.Sighting> interceptSighting, long currentTick) {
        if (!action.subject().equals(state.bootstrap().hive().id())) {
            var policy = SettlementStaffingComposition.change(state, action.subject(), currentTick);
            if (policy.isPresent()) {
                var changed = policy.orElseThrow();
                var projected = SettlementStaffingComposition.reduce(state, action.subject(), changed);
                return concatenate(List.of(new ProposedEvent(action.subject(), changed)),
                        planWithStaffing(projected, action, recurring, allowHiveInterception, eventIdentity, interceptSighting, currentTick));
            }
        }
        return planWithStaffing(state, action, recurring, allowHiveInterception, eventIdentity, interceptSighting, currentTick);
    }

    private static List<ProposedEvent> planWithStaffing(FrontierWorldState state, ScheduledAction action, boolean recurring,
                                            boolean allowHiveInterception, String eventIdentity,
                                            Optional<HiveOperationKnowledge.Sighting> interceptSighting, long currentTick) {
        if (currentTick < action.dueAt().ticks()) throw new IllegalArgumentException("policy admission precedes its due action");
        SubjectId owner = action.subject(); int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        requireKnownOwner(state.bootstrap(), owner);
        DecisionPolicyRegistry.require(state.strategicPlans().requireDecisionAuthority(owner));
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
        if (hive) observedAndHealth = concatenate(observedAndHealth,
                HivePresenceProcess.planFree(state, owner, currentTick));
        Optional<SettlementManagement.Decision> management = state.bootstrap().hive().id().equals(owner)
                ? Optional.empty() : Optional.of(SettlementManagementComposition.MANAGEMENT.decide(decisionState,
                        FrontierWorldStateSupport.settlement(state.bootstrap(), owner)));
        if (management.isPresent()) {
            var expansion = SettlementManagementComposition.MANAGEMENT.expandActiveTasks(decisionState,
                    FrontierWorldStateSupport.settlement(state.bootstrap(), owner), action.id(), action.dueAt().ticks());
            if (!expansion.isEmpty()) return concatenate(concatenate(observedAndHealth, expansion), next);
        }
        Optional<StrategicOperationProposal> candidate = management.isPresent() ? management.orElseThrow().selected()
                : hiveCandidate(decisionState, allowHiveInterception, action.dueAt().ticks(), interceptSighting);
        if (candidate.map(StrategicOperationProposal::kind).orElse(null) == StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION
                && HiveRouteEngagementProcess.hasPendingOrActiveInterception(state)) {
            return concatenate(observedAndHealth, next);
        }
        List<ProposedEvent> preempted = new java.util.ArrayList<>(preemptForInterception(state, owner, candidate));
        management.ifPresent(decision -> decision.replacePendingTasks().forEach(id -> preempted.add(
                new ProposedEvent(owner, new StrategicTaskTransition(id, StrategicTaskStatus.BLOCKED)))));
        if (candidate.isEmpty()) return concatenate(observedAndHealth, next);
        StrategicOperationProposal value = candidate.orElseThrow(); StrategicObjective objective = objective(state, owner, value, ordinal, eventIdentity);
        if (state.strategicPlans().hasActiveObjective(owner, objective.lane()) && preempted.isEmpty()) {
            return concatenate(observedAndHealth, next);
        }
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
            return withPreemption(preempted, concatenate(observedAndHealth, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)),
                        new ProposedEvent(owner, new StrategicTaskPlanned(task)), new ProposedEvent(task.id(),
                        new ScheduleEffect.Created(ProductionProcess.start(task, action.dueAt().ticks() + 1L))));
        }
        if (task.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE) {
            return withPreemption(preempted, concatenate(observedAndHealth, next), new ProposedEvent(owner, new StrategicObjectiveSelected(objective)),
                    new ProposedEvent(owner, new StrategicTaskPlanned(task)), new ProposedEvent(task.id(),
                            new ScheduleEffect.Created(ResourceSiteHarvestProcess.start(task, action.dueAt().ticks() + 1L))));
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

    private static List<ProposedEvent> preemptForInterception(FrontierWorldState state, SubjectId owner, Optional<StrategicOperationProposal> candidate) {
        if (!state.bootstrap().hive().id().equals(owner) || candidate.map(StrategicOperationProposal::kind).orElse(null) != StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION) return List.of();
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(owner))
                .filter(task -> task.status() == StrategicTaskStatus.PENDING || task.status() == StrategicTaskStatus.ACTIVE)
                .sorted(Comparator.comparing(StrategicTask::id)).map(task -> new ProposedEvent(owner, new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED))).toList();
    }
    private static List<ProposedEvent> withPreemption(List<ProposedEvent> preempted, List<ProposedEvent> next, ProposedEvent... events) {
        List<ProposedEvent> result = new java.util.ArrayList<>(preempted);
        result.addAll(List.of(events)); result.addAll(next);
        return List.copyOf(result);
    }

    private static List<ProposedEvent> pendingHarvestStarts(FrontierWorldState state, SubjectId settlementId, long dueAt) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(settlementId))
                .filter(task -> task.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE && task.status() == StrategicTaskStatus.PENDING)
                .filter(task -> task.resourceSiteTarget().map(state.resourceSites()::site).map(site -> site.phase() == ResourceSitePhase.READY).orElse(false))
                .sorted(Comparator.comparing(StrategicTask::id)).map(task -> {
                    ScheduledAction replacement = ResourceSiteHarvestProcess.start(task, Math.addExact(dueAt, 1L));
                    // Resource-site opportunity admission owns this one stable start action.  A
                    // secured ration advances that retained action; creating a second action
                    // with the same task identity quarantines the whole canonical interval.
                    return new ProposedEvent(task.id(), new ScheduleEffect.Rescheduled(replacement.id(), replacement));
                }).toList();
    }
    private static List<ProposedEvent> concatenate(List<ProposedEvent> first, List<ProposedEvent> second) {
        List<ProposedEvent> result = new java.util.ArrayList<>(first); result.addAll(second); return List.copyOf(result);
    }
    private static Optional<StrategicOperationProposal> hiveCandidate(FrontierWorldState state, boolean allowInterception, long now,
                                                      Optional<HiveOperationKnowledge.Sighting> interceptSighting) {
        Optional<HiveOperationKnowledge.Sighting> sighted = allowInterception
                ? interceptSighting.or(() -> state.strategicPlans().hiveOperationKnowledge().freshest(now,
                        state.bootstrap().ruleset().cadence().hivePerceptionRefreshInterval())) : Optional.empty();
        if (state.strategicPlans().hiveDoctrine().doctrine() == HiveDoctrine.INTERDICT && sighted.isPresent()) {
            HiveOperationKnowledge.Sighting observation = sighted.orElseThrow();
            return Optional.of(new StrategicOperationProposal(StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.empty(),
                    Optional.of(observation.operationId()), Optional.of(observation.position()), Long.MAX_VALUE));
        }
        if (state.strategicPlans().hiveDoctrine().doctrine() == HiveDoctrine.CONSOLIDATE) return hiveGrowthCandidate(state);
        if (state.strategicPlans().hiveDoctrine().doctrine() != HiveDoctrine.EXPAND) return Optional.empty();
        return HiveInfectionProcess.expansionTarget(state, now).map(target -> new StrategicOperationProposal(StrategicObjectiveKind.HIVE_EXPAND_INFECTION, Optional.of(target),
                Math.subtractExact(FixedScalar.SCALE, state.strategicPlans().hiveTerritoryKnowledge().freshInfection(state.bootstrap().ruleset(), now)
                        .getOrDefault(target, new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(FixedScalar.ZERO)).value().raw())));
    }
    private static Optional<StrategicOperationProposal> hiveGrowthCandidate(FrontierWorldState state) {
        boolean capacity = state.hiveColony().growthJobs().isEmpty() && state.hiveColony().addedOrgans().size() < HiveColony.MAX_ADDED_ORGANS
                && state.hiveColony().spawnedBioforms().size() < HiveColony.MAX_SPAWNED_BIOFORMS;
        boolean biomass = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:rotten_flesh")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && state.isHiveStore(slot.containerId()))
                || state.inventory().fungibleResources().accounts().values().stream().anyMatch(account -> account.custody() instanceof ResourceCustody.Container container
                && state.isHiveStore(container.containerId()) && account.lotQuantities().entrySet().stream().anyMatch(entry ->
                state.inventory().fungibleResources().lots().get(entry.getKey()).itemKind().equals("minecraft:rotten_flesh") && entry.getValue() >= 64));
        return capacity && biomass ? Optional.of(new StrategicOperationProposal(StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), FixedScalar.SCALE)) : Optional.empty();
    }
    private static StrategicObjective objective(FrontierWorldState state, SubjectId owner, StrategicOperationProposal candidate, int ordinal) {
        return objective(state, owner, candidate, ordinal, null);
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
    private static StrategicObjective objective(FrontierWorldState state, SubjectId owner, StrategicOperationProposal candidate, int ordinal, String eventIdentity) {
        String stem = eventIdentity == null ? owner.value().replace(':', '-') + "-" + candidate.kind().name().toLowerCase(java.util.Locale.ROOT) + "-" + ordinal
                : eventIdentity.substring("schedule:".length());
        DecisionAuthority authority = state.strategicPlans().requireDecisionAuthority(owner);
        return new StrategicObjective(new SubjectId("objective:" + stem), owner, candidate.kind(), candidate.target(), candidate.resourceSiteTarget(), ordinal,
                StrategicObjectiveStatus.ACTIVE, authority.ownerId(), authority.reconsiderationEpoch());
    }
    private static void requireKnownRouteTrigger(String trigger) {
        if (!trigger.equals("loss") && !trigger.equals("failure") && !trigger.equals("confirmed")) {
            throw new IllegalArgumentException("route reconsideration has an unknown trigger");
        }
    }

    private static StrategicTask task(FrontierWorldState state, StrategicObjective objective) { return task(state, objective, Optional.empty(), Optional.empty()); }
    private static StrategicTask task(FrontierWorldState state, StrategicObjective objective, Optional<SubjectId> observedOperation,
                                      Optional<BlockPosition> operationObservationPosition) {
        List<StrategicTaskRequirement> requirements = StrategicOperationSpecifications.requirements(objective.kind());
        StrategicTaskKind kind = switch (objective.kind()) {
            case SETTLEMENT_CONTAIN_LOCAL_INFECTION -> StrategicTaskKind.DECONTAMINATE_INFECTION_CELL;
            case HIVE_EXPAND_INFECTION -> StrategicTaskKind.SPREAD_INFECTION_CELL;
            case HIVE_GROW_ORGANISM -> StrategicTaskKind.GROW_HIVE_ORGANISM;
            case HIVE_INTERCEPT_ROUTE_OPERATION -> StrategicTaskKind.INTERCEPT_ROUTE_OPERATION;
            case HIVE_ASSAULT_SETTLEMENT -> StrategicTaskKind.ASSAULT_SETTLEMENT;
            case SETTLEMENT_PRODUCE_BREAD, SETTLEMENT_COMPANY_PRODUCTION -> StrategicTaskKind.PRODUCE_BREAD;
            case SETTLEMENT_DELIVER_BREAD_TO_HIVE -> throw new IllegalArgumentException("delivery objective requires its two-task decomposition");
            case SETTLEMENT_PATROL_OBSTRUCTED_ROUTE -> StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE;
            case SETTLEMENT_CONSTRUCT_ROUTE_BYPASS -> StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS;
            case SETTLEMENT_HARVEST_RESOURCE_SITE -> StrategicTaskKind.HARVEST_RESOURCE_SITE;
        };
        boolean operationBacked = kind == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION || kind == StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE;
        Optional<SubjectId> operation = operationBacked ? observedOperation : Optional.empty();
        Optional<BlockPosition> observation = operationBacked ? operationObservationPosition : Optional.empty();
        if (kind == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION && (operation.isEmpty() || observation.isEmpty())) {
            throw new IllegalArgumentException("hive interception requires one exact scout sighting");
        }
        return new StrategicTask(new SubjectId("task:" + objective.id().value().substring("objective:".length())), objective.id(), objective.ownerId(), kind,
                objective.infectionTarget(), operation, objective.resourceSiteTarget(), requirements, dependencies(state, objective), StrategicTaskStatus.PENDING, observation,
                objective.authorityId(), objective.authorityEpoch());
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
        return new StrategicTask(taskId(objective, "prepare"), objective.id(), objective.ownerId(), StrategicTaskKind.PREPARE_BREAD_CARGO, Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), dependencies(state, objective), StrategicTaskStatus.PENDING, Optional.empty(), objective.authorityId(), objective.authorityEpoch());
    }
    private static StrategicTask deliveryTask(StrategicObjective objective, StrategicTask preparation) {
        return new StrategicTask(taskId(objective, "deliver"), objective.id(), objective.ownerId(), StrategicTaskKind.DELIVER_BREAD_TO_HIVE, Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE, StrategicTaskRequirement.AVAILABLE_HAULER, StrategicTaskRequirement.AVAILABLE_GUARD),
                List.of(preparation.id()), StrategicTaskStatus.PENDING, Optional.empty(), objective.authorityId(), objective.authorityEpoch());
    }
    private static SubjectId taskId(StrategicObjective objective, String phase) {
        return new SubjectId("task:" + objective.id().value().substring("objective:".length()) + "-" + phase);
    }
    private static void requireKnownOwner(FrontierBootstrap bootstrap, SubjectId owner) {
        if (!bootstrap.hive().id().equals(owner) && bootstrap.settlements().stream().noneMatch(settlement -> settlement.id().equals(owner))) {
            throw new IllegalArgumentException("strategic review has a foreign owner");
        }
    }
}
