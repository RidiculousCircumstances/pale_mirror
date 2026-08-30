package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicObjectiveProcessTest {
    @Test
    void hiveUtilitySelectsOneExactExpansionObjectiveAndDurableTask() {
        FrontierWorldState state = initial("frontier:strategic-hive", 401L); SubjectId hive = state.bootstrap().hive().id();
        state = state.withInventory(state.inventory().withoutItem(new SubjectId("item:bootstrap-hive-biomass")));

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(hive, 1, 60L));

        assertEquals(4, planned.size());
        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, planned.getFirst().payload());
        StrategicTaskPlanned task = assertInstanceOf(StrategicTaskPlanned.class, planned.get(1).payload());
        assertEquals(StrategicObjectiveKind.HIVE_EXPAND_INFECTION, selected.objective().kind());
        assertEquals(StrategicTaskKind.SPREAD_INFECTION_CELL, task.task().kind());
        assertEquals(List.of(StrategicTaskRequirement.OPERATIONAL_HEART), task.task().requirements());
        state = StrategicObjectiveProcess.reduceObjective(state, hive, selected);
        state = StrategicObjectiveProcess.reduceTask(state, hive, task);
        assertEquals(1, state.strategicPlans().objectives().size()); assertEquals(1, state.strategicPlans().tasks().size());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        assertEquals(selected, FrontierWorldRuntimeDefinition.payloadCodecs().decode(selected.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(selected)));
        assertEquals(task, FrontierWorldRuntimeDefinition.payloadCodecs().decode(task.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(task)));
    }

    @Test
    void settlementUtilityUsesOnlyItsLocalInfectionAndCannotDuplicateItsActiveObjective() {
        FrontierWorldState state = initial("frontier:strategic-settlement", 402L); Settlement settlement = state.bootstrap().settlements().getFirst();
        InfectionCell nearby = InfectionCell.at(settlement.anchor()); state = state.withInfection(nearby, new FixedRatio(new FixedScalar(750_000L)));

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 1, 40L));

        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, planned.getFirst().payload());
        assertEquals(StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, selected.objective().kind()); assertEquals(nearby, selected.objective().infectionTarget().orElseThrow());
        state = StrategicObjectiveProcess.reduceObjective(state, settlement.id(), selected);
        state = StrategicObjectiveProcess.reduceTask(state, settlement.id(), assertInstanceOf(StrategicTaskPlanned.class, planned.get(1).payload()));
        List<ProposedEvent> activeReview = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 2, 240L));
        assertEquals(3, activeReview.size(), "an already-active strategic lane still emits the exact health and quarantine facts before its next review");
        assertInstanceOf(ResidentHealthTransition.class, activeReview.getFirst().payload());
        assertInstanceOf(SettlementQuarantineTransition.class, activeReview.get(1).payload());
    }

    @Test
    void ownerHasAtMostOneActiveObjectivePerDerivedLane() {
        SubjectId owner = new SubjectId("settlement:1");
        StrategicObjective strategic = new StrategicObjective(new SubjectId("objective:lane-strategic"), owner,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, java.util.Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicObjective facility = new StrategicObjective(new SubjectId("objective:lane-facility"), owner,
                StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, java.util.Optional.empty(),
                java.util.Optional.of(new SubjectId("site:1-wheat-field")), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(strategic).addObjective(facility);

        assertTrue(plans.hasActiveObjective(owner, StrategicObjectiveLane.STRATEGIC));
        assertTrue(plans.hasActiveObjective(owner, StrategicObjectiveLane.FACILITY));
        StrategicObjective duplicateFacility = new StrategicObjective(new SubjectId("objective:lane-facility-duplicate"), owner,
                StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, java.util.Optional.empty(),
                java.util.Optional.of(new SubjectId("site:1-wheat-field")), 2, StrategicObjectiveStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> plans.addObjective(duplicateFacility));
    }

    @Test
    void hiveBiomassSelectsOneExactGrowthTaskBeforeFurtherExpansion() {
        FrontierWorldState state = initial("frontier:strategic-growth", 406L); SubjectId hive = state.bootstrap().hive().id();

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(hive, 1, 60L));

        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, planned.getFirst().payload());
        StrategicTaskPlanned task = assertInstanceOf(StrategicTaskPlanned.class, planned.get(1).payload());
        assertEquals(StrategicObjectiveKind.HIVE_GROW_ORGANISM, selected.objective().kind());
        assertEquals(StrategicTaskKind.GROW_HIVE_ORGANISM, task.task().kind());
        assertEquals(List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), task.task().requirements());
    }

    @Test
    void settlementWithoutLocalInfectionSelectsOneExactProductionTask() {
        FrontierWorldState state = initial("frontier:strategic-production", 407L); Settlement settlement = state.bootstrap().settlements().getFirst();

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 1, 60L));

        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, planned.getFirst().payload());
        StrategicTaskPlanned task = assertInstanceOf(StrategicTaskPlanned.class, planned.get(1).payload());
        assertEquals(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, selected.objective().kind());
        assertEquals(StrategicTaskKind.PRODUCE_BREAD, task.task().kind());
        assertEquals(List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT,
                StrategicTaskRequirement.FREE_DEPOT_SLOT), task.task().requirements());
        assertTrue(planned.stream().anyMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created created
                && created.action().kind().equals("frontier.settlement.production.task.start")));
    }

    @Test
    void foreignPlannerIdentityAndForgedTaskDecompositionFailClosed() {
        FrontierWorldState state = initial("frontier:strategic-rejection", 403L);
        assertThrows(IllegalArgumentException.class, () -> StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(new SubjectId("settlement:foreign"), 1, 1L)));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:test"), state.bootstrap().hive().id(), StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                java.util.Optional.of(InfectionCell.at(state.bootstrap().hive().seedNests().getFirst().anchor())), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(objective);
        StrategicTask forged = new StrategicTask(new SubjectId("task:test"), objective.id(), objective.ownerId(), StrategicTaskKind.DECONTAMINATE_INFECTION_CELL,
                objective.infectionTarget(), List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.PENDING);
        assertThrows(IllegalArgumentException.class, () -> plans.addTask(forged));
    }

    @Test
    void terminalTaskOutcomesAreDurableAndOldTerminalObjectivesCompactBeforeNewWork() {
        FrontierWorldState state = initial("frontier:strategic-terminal", 405L); SubjectId owner = state.bootstrap().hive().id();
        InfectionCell target = InfectionCell.at(state.bootstrap().hive().seedNests().getFirst().anchor());
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:terminal"), owner, StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                java.util.Optional.of(target), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask first = hiveTask("task:terminal-first", objective, StrategicTaskStatus.PENDING);
        StrategicTask second = hiveTask("task:terminal-second", objective, StrategicTaskStatus.PENDING);
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(objective).addTask(first).addTask(second);
        plans = plans.transitionTask(first.id(), StrategicTaskStatus.BLOCKED).transitionTask(second.id(), StrategicTaskStatus.ACTIVE)
                .transitionTask(second.id(), StrategicTaskStatus.COMPLETED);
        assertEquals(StrategicObjectiveStatus.BLOCKED, plans.objectives().get(objective.id()).status());
        assertEquals(plans, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state.withStrategicPlans(plans))).strategicPlans());

        StrategicPlanState retained = StrategicPlanState.empty();
        for (int ordinal = 1; ordinal <= StrategicPlanState.MAX_OBJECTIVES; ordinal++) {
            retained = retained.addObjective(new StrategicObjective(new SubjectId("objective:retained-" + ordinal), owner,
                    StrategicObjectiveKind.HIVE_EXPAND_INFECTION, java.util.Optional.of(target), ordinal, StrategicObjectiveStatus.COMPLETED));
        }
        StrategicPlanState compacted = retained.addObjective(new StrategicObjective(new SubjectId("objective:next"), owner,
                StrategicObjectiveKind.HIVE_EXPAND_INFECTION, java.util.Optional.of(target), 129, StrategicObjectiveStatus.ACTIVE));
        assertEquals(StrategicPlanState.MAX_OBJECTIVES, compacted.objectives().size());
        assertTrue(!compacted.objectives().containsKey(new SubjectId("objective:retained-1")) && compacted.objectives().containsKey(new SubjectId("objective:next")));
    }

    @Test
    void retentionCompactionKeepsACompletedProductionTaskReferencedByAnotherTerminalObjective() {
        SubjectId owner = new SubjectId("settlement:1");
        StrategicObjective production = new StrategicObjective(new SubjectId("objective:source"), owner,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, java.util.Optional.empty(), 1, StrategicObjectiveStatus.COMPLETED);
        StrategicTask produced = new StrategicTask(new SubjectId("task:source"), production.id(), owner, StrategicTaskKind.PRODUCE_BREAD,
                java.util.Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT,
                StrategicTaskRequirement.FREE_DEPOT_SLOT), List.of(), StrategicTaskStatus.COMPLETED);
        StrategicObjective delivery = new StrategicObjective(new SubjectId("objective:delivery"), owner,
                StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE, java.util.Optional.empty(), 2, StrategicObjectiveStatus.COMPLETED);
        StrategicTask prepared = new StrategicTask(new SubjectId("task:delivery-prepare"), delivery.id(), owner, StrategicTaskKind.PREPARE_BREAD_CARGO,
                java.util.Optional.empty(), List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), List.of(produced.id()), StrategicTaskStatus.COMPLETED);
        StrategicTask delivered = new StrategicTask(new SubjectId("task:delivery-deliver"), delivery.id(), owner, StrategicTaskKind.DELIVER_BREAD_TO_HIVE,
                java.util.Optional.empty(), List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE, StrategicTaskRequirement.AVAILABLE_HAULER,
                StrategicTaskRequirement.AVAILABLE_GUARD), List.of(prepared.id()), StrategicTaskStatus.COMPLETED);
        Map<SubjectId, StrategicObjective> objectives = new LinkedHashMap<>();
        objectives.put(production.id(), production); objectives.put(delivery.id(), delivery);
        for (int ordinal = 3; ordinal <= StrategicPlanState.MAX_OBJECTIVES; ordinal++) {
            StrategicObjective filler = new StrategicObjective(new SubjectId("objective:retention-filler-" + ordinal), owner,
                    StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, java.util.Optional.empty(), ordinal, StrategicObjectiveStatus.COMPLETED);
            objectives.put(filler.id(), filler);
        }
        StrategicPlanState full = new StrategicPlanState(objectives, Map.of(produced.id(), produced, prepared.id(), prepared, delivered.id(), delivered), Map.of(), Map.of());

        StrategicPlanState compacted = full.addObjective(new StrategicObjective(new SubjectId("objective:retention-next"), owner,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, java.util.Optional.empty(), StrategicPlanState.MAX_OBJECTIVES + 1, StrategicObjectiveStatus.ACTIVE));

        assertTrue(compacted.objectives().containsKey(production.id()));
        assertTrue(compacted.tasks().containsKey(produced.id()));
        assertTrue(compacted.objectives().containsKey(new SubjectId("objective:retention-next")));
        assertEquals(StrategicPlanState.MAX_OBJECTIVES, compacted.objectives().size());
    }

    @Test
    void scheduledWorldWorkPersistsOneUtilityPlanPerEligibleSide() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:strategic-scheduled"), 404L));

        for (long tick = 100L; tick <= 3_300L; tick += 100L) {
            engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(tick), new WorkBudget(64, 512));
        }

        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().failureDetail().orElse(""));
        assertTrue(state.strategicPlans().tasks().size() >= state.strategicPlans().objectives().size()
                && state.strategicPlans().tasks().size() <= StrategicPlanState.MAX_TASKS);
        org.junit.jupiter.api.Assertions.assertTrue(state.strategicPlans().objectives().size() >= 1 && state.strategicPlans().objectives().size() <= StrategicPlanState.MAX_OBJECTIVES,
                () -> "strategic objectives=" + state.strategicPlans().objectives());
        assertTrue(state.strategicPlans().objectives().values().stream().filter(value -> value.status() == StrategicObjectiveStatus.ACTIVE)
                .collect(java.util.stream.Collectors.groupingBy(value -> java.util.Map.entry(value.ownerId(), value.lane())))
                .values().stream().allMatch(values -> values.size() == 1));
    }

    @Test
    void operationInterruptPreemptsHiveWorkWithoutForkingItsPeriodicReviewCadence() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentRouteSceneReturnConfiguration(
                new WorldId("frontier:strategic-interrupt"), 91L));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()); SubjectId hive = state.bootstrap().hive().id();
        InfectionCell infection = InfectionCell.at(state.bootstrap().hive().seedNests().getFirst().anchor());
        StrategicObjective active = new StrategicObjective(new SubjectId("objective:hive-active-infection"), hive,
                StrategicObjectiveKind.HIVE_EXPAND_INFECTION, java.util.Optional.of(infection), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask activeTask = new StrategicTask(new SubjectId("task:hive-active-infection"), active.id(), hive,
                StrategicTaskKind.SPREAD_INFECTION_CELL, active.infectionTarget(), List.of(StrategicTaskRequirement.OPERATIONAL_HEART), List.of(), StrategicTaskStatus.ACTIVE);
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(active).addTask(activeTask));

        List<ProposedEvent> planned = StrategicObjectiveProcess.planOpportunity(state, StrategicObjectiveProcess.interceptOpportunity(hive, operation, 100L));

        StrategicTaskTransition preempted = assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload());
        assertEquals(activeTask.id(), preempted.taskId()); assertEquals(StrategicTaskStatus.BLOCKED, preempted.status());
        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, planned.get(1).payload());
        assertEquals(StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, selected.objective().kind());
        assertTrue(planned.stream().noneMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created created
                && created.action().kind().equals("frontier.objective.review")));
    }

    @Test
    void pendingInterceptionIsTheDurableHiveLaneClaimUntilItsStartRuns() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentRouteSceneReturnConfiguration(
                new WorldId("frontier:strategic-intercept-pending"), 91L));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId hive = state.bootstrap().hive().id();
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst().orElseThrow();

        List<ProposedEvent> first = StrategicObjectiveProcess.planOpportunity(state,
                StrategicObjectiveProcess.interceptOpportunity(hive, operation, 100L));
        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, first.getFirst().payload());
        StrategicTaskPlanned plannedTask = assertInstanceOf(StrategicTaskPlanned.class, first.get(1).payload());
        state = StrategicObjectiveProcess.reduceObjective(state, hive, selected);
        state = StrategicObjectiveProcess.reduceTask(state, hive, plannedTask);

        List<ProposedEvent> repeated = StrategicObjectiveProcess.planOpportunity(state,
                StrategicObjectiveProcess.interceptOpportunity(hive, operation, 120L));
        assertTrue(repeated.isEmpty(), "a second opportunity must not schedule a duplicate start for the retained task");
    }

    private static FrontierWorldState initial(String world, long seed) {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(world), seed));
    }

    private static StrategicTask hiveTask(String id, StrategicObjective objective, StrategicTaskStatus status) {
        return new StrategicTask(new SubjectId(id), objective.id(), objective.ownerId(), StrategicTaskKind.SPREAD_INFECTION_CELL,
                objective.infectionTarget(), List.of(StrategicTaskRequirement.OPERATIONAL_HEART), List.of(), status);
    }
}
