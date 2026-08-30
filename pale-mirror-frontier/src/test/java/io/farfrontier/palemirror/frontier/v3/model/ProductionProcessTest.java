package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionProcessTest {
    @Test
    void coldProductionStillAdvancesWithoutMaterializingAnUnloadedContainer() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:production"), 91L));
        for (long tick = 100L; tick <= 2_200L; tick += 100L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState completed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(completed.productionJobs().isEmpty());
        ExactItemStack bread = completed.inventory().items().get(new SubjectId("item:production-1-1-bread"));
        assertEquals("minecraft:bread", bread.itemKind());
        assertEquals(64, bread.count());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 0), bread.custody());
        assertEquals(StrategicTaskStatus.COMPLETED, completed.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD).findFirst().orElseThrow().status());
    }

    @Test
    void activeMaterializedProductionRetainsInputUntilOneDurablePhysicalTransformationConfirmsOutput() {
        PreparedProduction prepared = activePhysicalProduction();
        ExactItemStack input = prepared.state().inventory().items().get(prepared.job().consumedItemId());
        FrontierWorldState running = prepared.state().transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ProductionTransformationObservation receipt = new ProductionTransformationObservation(new PhysicalObservationId("observation:test-production"), prepared.intent().id(),
                input.id(), prepared.job().outputItemId(), input.count(), prepared.job().outputCount());
        PhysicalIntentTransition transition = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(receipt, ((PhysicalIntentTransition) FrontierWorldRuntimeDefinition.payloadCodecs().decode(transition.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(transition))).observation().orElseThrow());
        FrontierWorldState completed = running.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertTrue(completed.productionJobs().isEmpty());
        assertEquals("minecraft:bread", completed.inventory().items().get(prepared.job().outputItemId()).itemKind());
    }

    @Test
    void trustedPhysicalExecutorRoutesProductionTransitionsToTheirSettlementJob() {
        PreparedProduction prepared = activePhysicalProduction();
        WorldId world = new WorldId("frontier:production-command");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, prepared.state(), SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));

        assertInstanceOf(CommandResult.Accepted.class, submitTransition(engine, world, "running", prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty()));
        FrontierWorldState running = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(PhysicalIntentStatus.RUNNING, running.physicalIntents().get(prepared.intent().id()).status());

        ExactItemStack input = running.inventory().items().get(prepared.job().consumedItemId());
        ProductionTransformationObservation receipt = new ProductionTransformationObservation(new PhysicalObservationId("observation:production-command"), prepared.intent().id(),
                input.id(), prepared.job().outputItemId(), input.count(), prepared.job().outputCount());
        CommandResult confirmed = submitTransition(engine, world, "confirmed", prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertInstanceOf(CommandResult.Accepted.class, confirmed, confirmed.toString());
        FrontierWorldState completed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals("minecraft:bread", completed.inventory().items().get(prepared.job().outputItemId()).itemKind());
    }

    @Test
    void foreignOrMissingPhysicalInputBlocksTheTaskWithoutDiscardingCanonicalClaim() {
        PreparedProduction prepared = activePhysicalProduction();

        FrontierWorldState blocked = prepared.state().transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());

        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, blocked.physicalIntents().get(prepared.intent().id()).status());
        assertTrue(blocked.inventory().items().containsKey(new SubjectId("item:bootstrap-1-wheat")));
        assertEquals(StrategicTaskStatus.BLOCKED, blocked.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD).findFirst().orElseThrow().status());
    }

    @Test
    void productionRefusesToStartWithoutAnExactInputStack() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:input"), 91L));
        FrontierWorldState withoutInput = productionTask(initial.withInventory(initial.inventory().withoutItem(new SubjectId("item:bootstrap-1-wheat"))), StrategicTaskStatus.PENDING);
        StrategicTask task = withoutInput.strategicPlans().tasks().values().iterator().next();

        List<ProposedEvent> planned = FrontierWorldRuntimeDefinition.planScheduled(withoutInput, ProductionProcess.start(task, 200L));

        ProductionBlocked event = assertInstanceOf(ProductionBlocked.class, planned.getFirst().payload());
        assertEquals(ProductionBlockReason.INPUT_UNAVAILABLE, event.reason());
        FrontierWorldState reduced = ProductionProcess.reduceBlocked(withoutInput, withoutInput.bootstrap().settlements().getFirst().id(), event);
        reduced = StrategicObjectiveProcess.reduceTaskTransition(reduced, event.settlementId(), assertInstanceOf(StrategicTaskTransition.class, planned.get(1).payload()));
        assertEquals(StrategicTaskStatus.BLOCKED, reduced.strategicPlans().tasks().get(task.id()).status());
        assertEquals(StrategicObjectiveStatus.BLOCKED, reduced.strategicPlans().objectives().get(task.objectiveId()).status());
    }

    @Test
    void productionRefusesToStartWhenEveryCrafterIsStarvingAndRecoversAfterOneExactRation() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:starving-crafter"), 91L));
        Settlement settlement = initial.bootstrap().settlements().getFirst(); HumanPopulation population = initial.humanPopulation();
        List<ResidentProfile> crafters = population.residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id())
                && resident.role() == ResidentRole.CRAFTER).toList();
        for (int cycle = 1; cycle <= ResidentNutrition.STARVING_AFTER_MISSED_CYCLES; cycle++) {
            for (ResidentProfile crafter : crafters) population = population.resolveNutrition(crafter.id(), cycle, false);
        }
        FrontierWorldState starving = productionTask(initial.withHumanPopulation(population), StrategicTaskStatus.PENDING);
        StrategicTask task = starving.strategicPlans().tasks().values().iterator().next();

        List<ProposedEvent> planned = FrontierWorldRuntimeDefinition.planScheduled(starving, ProductionProcess.start(task, 200L));
        ProductionBlocked block = planned.stream().map(ProposedEvent::payload).filter(ProductionBlocked.class::isInstance)
                .map(ProductionBlocked.class::cast).findFirst().orElseThrow();
        assertEquals(ProductionBlockReason.WORKER_UNAVAILABLE, block.reason());
        assertEquals(block, FrontierWorldRuntimeDefinition.payloadCodecs().decode(block.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(block)));
        FrontierWorldState reduced = ProductionProcess.reduceBlocked(starving, settlement.id(), block);
        assertEquals(starving, reduced);

        for (ResidentProfile crafter : crafters) population = population.resolveNutrition(crafter.id(), ResidentNutrition.STARVING_AFTER_MISSED_CYCLES + 1, true);
        FrontierWorldState recovered = productionTask(initial.withHumanPopulation(population), StrategicTaskStatus.PENDING);
        List<ProposedEvent> retried = FrontierWorldRuntimeDefinition.planScheduled(recovered, ProductionProcess.start(task, 300L));
        assertTrue(retried.stream().map(ProposedEvent::payload).anyMatch(ProductionStarted.class::isInstance));
    }

    private static FrontierWorldState productionTask(FrontierWorldState state, StrategicTaskStatus status) {
        SubjectId settlement = new SubjectId("settlement:1");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:test-production"), settlement,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:test-production"), objective.id(), settlement, StrategicTaskKind.PRODUCE_BREAD,
                Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT,
                StrategicTaskRequirement.FREE_DEPOT_SLOT), List.of(), status);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }

    private static PreparedProduction activePhysicalProduction() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:production-physical"), 91L));
        SubjectId settlement = new SubjectId("settlement:1"), depot = new SubjectId("container:1-depot");
        FrontierWorldState state = productionTask(initial.withInventory(initial.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)), StrategicTaskStatus.ACTIVE);
        ProductionJob job = new ProductionJob(new SubjectId("job:production-1-physical"), settlement, new SubjectId("structure:1-workshop"),
                new SubjectId("resident:1-3"), new SubjectId("item:bootstrap-1-wheat"), new SubjectId("item:production-1-physical-bread"), "minecraft:bread", 64);
        state = state.withProductionJob(job);
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:production-transform-1-physical"), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, job.id(), List.of(job.id(), job.consumedItemId(), job.outputItemId()),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED);
        return new PreparedProduction(state.preparePhysicalIntent(intent), job, intent);
    }

    private static CommandResult submitTransition(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine, WorldId world,
                                                  String suffix, PhysicalIntentId intent, PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation) {
        CommandId command = new CommandId("command:production-" + suffix);
        var checkpoint = engine.checkpoint();
        return engine.submit(new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), new PhysicalIntentTransition(intent, status, observation)));
    }

    private record PreparedProduction(FrontierWorldState state, ProductionJob job, PhysicalIntent intent) { }
}
