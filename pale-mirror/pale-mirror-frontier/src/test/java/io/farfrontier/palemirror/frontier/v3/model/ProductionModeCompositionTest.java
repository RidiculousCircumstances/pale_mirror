package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Current representation parity: both inputs use the same baker/station custody protocol. */
class ProductionModeCompositionTest {
    @Test
    void exactAndFungibleBatchesUseTheSamePhysicalStationSequence() {
        Result fungible = run(false);
        Result exact = run(true);
        assertEquals(fungible.actions(), exact.actions());
        assertEquals(1, fungible.actions().stream().filter(action -> action == BakeryColdStep.Action.PICKUP).count());
        assertEquals(1, fungible.actions().stream().filter(action -> action == BakeryColdStep.Action.LOAD).count());
        assertEquals(ProductionWorkProgress.REQUIRED_PROCESSING_TICKS,
                fungible.actions().stream().filter(action -> action == BakeryColdStep.Action.WORK_TICK).count());
        assertEquals(1, fungible.actions().stream().filter(action -> action == BakeryColdStep.Action.RECIPE).count());
        assertEquals(1, fungible.actions().stream().filter(action -> action == BakeryColdStep.Action.UNLOAD).count());
        assertEquals(1, fungible.actions().stream().filter(action -> action == BakeryColdStep.Action.DELIVER).count());
        assertEquals("minecraft:bread", fungible.outputKind());
        assertEquals(fungible.outputKind(), exact.outputKind());
        assertEquals(64, fungible.outputCount());
        assertEquals(fungible.outputCount(), exact.outputCount());
    }

    private static Result run(boolean exact) {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId(exact ? "frontier:bakery-composition-exact" : "frontier:bakery-composition-fungible"), 91L));
        if (exact) initial = ProductionProcessTest.withLegacyExactWheat(initial);
        FrontierWorldState state = ProductionProcessTest.productionTask(initial, StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = started.job();
        List<BakeryColdStep.Action> actions = new ArrayList<>();
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        for (int turn = 0; state.productionJobs().containsKey(job.id()) && turn < 700; turn++) {
            BakeryColdStep step = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 300L + turn * 20L))
                    .stream().map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            // Recovery must preserve each custody/recipe boundary, not repeatedly
            // serialize the whole 1024² world for every navigation or labor tick.
            int workTicks = state.productionJobs().containsKey(job.id())
                    ? state.productionJobs().get(job.id()).bakeryWork().orElseThrow().completedWorkTicks() : 0;
            if (step.action() != BakeryColdStep.Action.MOVE && step.action() != BakeryColdStep.Action.WORK_TICK
                    || step.action() == BakeryColdStep.Action.WORK_TICK
                    && (workTicks == 1 || workTicks == ProductionWorkProgress.REQUIRED_PROCESSING_TICKS))
                state = codec.decode(codec.encode(state));
            if (step.action() != BakeryColdStep.Action.MOVE) actions.add(step.action());
            if (step.action() == BakeryColdStep.Action.RECIPE) {
                ProductionStationSpec station = state.inventory().containers().values().stream()
                        .flatMap(container -> container.productionStation().stream())
                        .filter(value -> value.id().equals(job.bakeryWork().orElseThrow().stationId()))
                        .findFirst().orElseThrow();
                if (exact) assertEquals(new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()),
                        state.inventory().items().get(job.outputItemId()).custody());
                else assertEquals(new ResourceCustody.Container(station.containerId()),
                        state.inventory().fungibleResources().accounts().get(job.bakeryWork().orElseThrow().stationAccountId()).custody());
            }
        }
        assertFalse(state.productionJobs().containsKey(job.id()), "bakery job did not finish its station sequence");
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(task.id()).status());
        String kind = exact ? state.inventory().items().get(job.outputItemId()).itemKind()
                : state.inventory().fungibleResources().lots().get(job.outputItemId()).itemKind();
        int count = exact ? state.inventory().items().get(job.outputItemId()).count()
                : state.inventory().fungibleResources().lots().get(job.outputItemId()).quantity();
        return new Result(List.copyOf(actions), kind, count);
    }

    private record Result(List<BakeryColdStep.Action> actions, String outputKind, int outputCount) { }
}
