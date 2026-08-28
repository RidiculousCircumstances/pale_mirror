package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionProcessTest {
    @Test
    void productionConsumesARealInputThenStoresOnlyItsDurableExactOutput() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:production"), 91L));
        for (long tick = 100L; tick <= 2_100L; tick += 100L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));

        FrontierWorldState started = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(1, started.productionJobs().size());
        ProductionJob job = started.productionJobs().values().iterator().next();
        assertEquals(new SubjectId("item:bootstrap-1-wheat"), job.consumedItemId());
        assertTrue(!started.inventory().items().containsKey(job.consumedItemId()));
        assertEquals(started, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(started)));

        engine.advanceTo(new SimInstant(2_200L), new WorkBudget(64, 512));
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
    void completionBlocksWhenAPlayerOrAnotherProcessHasFilledEveryExactDepotSlot() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:storage"), 91L));
        SubjectId depot = new SubjectId("container:1-depot"); Map<SubjectId, ExactItemStack> fullItems = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            SubjectId id = new SubjectId("item:storage-" + slot);
            fullItems.put(id, new ExactItemStack(id, new SubjectId("settlement:1"), "minecraft:cobblestone", 64, new InventoryCustody.ContainerSlot(depot, slot)));
        }
        ExactInventory fullInventory = new ExactInventory(baseline.inventory().containers(), fullItems, Map.of(), Map.of(), Map.of(), Map.of(), baseline.inventory().surfaces());
        ProductionJob job = new ProductionJob(new SubjectId("job:production-1-99"), new SubjectId("settlement:1"),
                new SubjectId("structure:1-workshop"), new SubjectId("resident:1-3"), new SubjectId("item:consumed-99"),
                new SubjectId("item:production-1-99-bread"), "minecraft:bread", 64);
        FrontierWorldState blocked = productionTask(baseline.withInventory(fullInventory).withProductionJob(job), StrategicTaskStatus.ACTIVE);

        List<ProposedEvent> planned = FrontierWorldRuntimeDefinition.planScheduled(blocked,
                new ScheduledAction(new ScheduleId("schedule:production-task-complete-production-1-99"), new SimInstant(500L), 0, job.id(), "frontier.settlement.production.task.complete", 1));

        ProductionBlocked event = assertInstanceOf(ProductionBlocked.class, planned.getFirst().payload());
        assertEquals(ProductionBlockReason.OUTPUT_STORAGE_UNAVAILABLE, event.reason());
        assertEquals(job.id(), event.workId());
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

    private static FrontierWorldState productionTask(FrontierWorldState state, StrategicTaskStatus status) {
        SubjectId settlement = new SubjectId("settlement:1");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:test-production"), settlement,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:test-production"), objective.id(), settlement, StrategicTaskKind.PRODUCE_BREAD,
                Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT,
                StrategicTaskRequirement.FREE_DEPOT_SLOT), List.of(), status);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }
}
