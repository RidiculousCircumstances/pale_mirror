package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A real worker carries target and materials separately; recipe completion alone is insufficient. */
class StationEquipmentWorkerTest {
    @Test void repairWorkerReturnsTheSameToolAfterEveryPhaseSurvivesRecovery() {
        run(true);
    }
    @Test void manufactureWorkerDeliversAnIndividualToolFromFungibleMaterials() {
        run(false);
    }
    @Test void playerTakingReservedTargetReleasesTheOrderWithoutCreatingOrRepairingAnItem() {
        var f = fixture(true); var state = f.state(); var tool = state.inventory().items().get(f.target());
        var player = new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000228"));
        var inventory = state.inventory().moveObservedItem(tool.id(), tool.custody(), player);
        var cancelled = StationWorkItemDeparture.observe(state, tool.id(), tool.custody(), inventory);
        assertFalse(cancelled.productionJobs().containsKey(f.job().id()));
        assertEquals(tool.withCustody(player), cancelled.inventory().items().get(tool.id()));
        assertTrue(cancelled.inventory().fungibleResources().claims().values().stream()
                .noneMatch(c -> c.claimantId().equals(f.job().id())));
        assertEquals(3, cancelled.inventory().fungibleResources().lots().get(f.material()).quantity());
        var codec = new FrontierWorldStateCodec(); assertEquals(cancelled, codec.decode(codec.encode(cancelled)));
    }
    @Test void hungryWorkerKeepsRepairTargetWhileEatingFromAnotherInventorySlot() {
        var f = fixture(true); var state = f.state(); var job = f.job();
        for (long tick = 300; tick < 10_000 && state.productionJobs().get(job.id()).stationWork().orElseThrow().phase()
                == StationWorkState.Phase.TARGET_PICKUP; tick += 20) {
            var step = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, tick)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, job.settlementId(), step);
        }
        assertEquals(StationWorkState.Phase.TARGET_LOAD, state.productionJobs().get(job.id()).stationWork().orElseThrow().phase());
        var tool = state.inventory().items().get(f.target());
        assertEquals(new InventoryCustody.Actor(job.workerId()), tool.custody());
        var bread = new SubjectId("lot:tool-worker-food"); var pocket = new SubjectId("custody:tool-worker-food");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(bread, job.settlementId(),
                FoodCatalog.BREAD, 3, "fixture:personal-food", List.of()), new CustodyAccount(pocket,
                new ResourceCustody.Actor(job.workerId()), Map.of(bread, 3), Map.of(), Optional.of(new ActorItemSlot.Pocket(0))));
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .humanPopulation(state.humanPopulation().accrueHunger(job.workerId(), 27_000)));
        var meal = ResidentMealProcess.selectSourceAtYield(state, job.workerId(), 27_000).orElseThrow();
        assertTrue(meal.meal().portable());
        state = ResidentMealProcess.reduceStarted(state, job.workerId(), meal);
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertTrue(ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 27_001)).stream()
                .noneMatch(event -> event.payload() instanceof BakeryColdStep));
        for (long tick = 27_001; tick < 28_000 && state.humanPopulation().meals().containsKey(job.workerId()); tick++) {
            var step = ResidentMealProcess.planColdStep(state, job.workerId(), tick);
            if (step.isPresent()) state = ResidentMealProcess.reduceColdStep(state, job.workerId(), step.orElseThrow());
        }
        assertFalse(state.humanPopulation().meals().containsKey(job.workerId()));
        assertEquals(tool, state.inventory().items().get(f.target()));
        assertEquals(StationWorkState.Phase.TARGET_LOAD, state.productionJobs().get(job.id()).stationWork().orElseThrow().phase());
        var resumed = ResidentActivityProcess.plan(state, ResidentActivityProcess.review(job.workerId(), 28_000)).stream()
                .map(ProposedEvent::payload).filter(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed.class::cast).findFirst().orElseThrow();
        state = FrontierWorldProcessCatalog.reduce("actor-execution", state, new FrontierEvent(1,
                new EventId("event:tool-worker-resume"), new TransactionId("transaction:tool-worker-resume"), state.bootstrap().worldId(),
                new Revision(1), new SimInstant(resumed.atTick()), job.workerId(),
                CauseChain.root(new CommandId("command:tool-worker-resume")), resumed));
        assertTrue(state.actorExecutions().owns(job.workerId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION, job.id()));
    }
    private static EquipmentWorkFixture.Fixture fixture(boolean repair) {
        return EquipmentWorkFixture.create(new WorldId("frontier:equipment-worker"), 41, repair);
    }
    private static void run(boolean repair) {
        var f = fixture(repair); var state = f.state(); var job = f.job(); var task = f.task();
        var home = f.home(); var material = f.material(); var target = f.target();
        var codec = new FrontierWorldStateCodec(); var phases = EnumSet.noneOf(StationWorkState.Phase.class);
        for (long tick = 300, attempts = 0; state.productionJobs().containsKey(job.id()) && attempts < 800; tick += 20, attempts++) {
            boolean enteringPhase = phases.add(state.productionJobs().get(job.id()).stationWork().orElseThrow().phase());
            tick = BakeryTestClock.due(state, job, tick);
            var events = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, tick));
            var step = events.stream().map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                    .map(BakeryColdStep.class::cast).findFirst().orElseThrow(() -> new AssertionError("station stalled: " + events));
            state = ProductionProcess.reduceBakeryColdStep(state, home.id(), step);
            if (enteringPhase) state = codec.decode(codec.encode(state));
        }
        assertFalse(state.productionJobs().containsKey(job.id()), "worker must deliver and release the whole order");
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(task.id()).status());
        var result = state.inventory().items().get(job.outputItemId());
        assertNotNull(result);
        assertEquals(repair ? target : job.outputItemId(), result.id());
        assertEquals(new ItemCondition(131, repair ? 35 : 0), result.condition());
        assertEquals(job.rights().destinationContainerId(), assertInstanceOf(InventoryCustody.ContainerSlot.class, result.custody()).containerId());
        assertTrue(phases.containsAll(List.of(StationWorkState.Phase.STATION_LOAD, StationWorkState.Phase.PROCESSING,
                StationWorkState.Phase.STATION_UNLOAD, StationWorkState.Phase.DEPOT_DELIVERY)));
        if (repair) assertTrue(phases.containsAll(List.of(StationWorkState.Phase.TARGET_PICKUP, StationWorkState.Phase.TARGET_LOAD)));
        assertEquals(repair ? 2 : 0, state.inventory().fungibleResources().lots().containsKey(material)
                ? state.inventory().fungibleResources().lots().get(material).quantity() : 0);
    }
}
