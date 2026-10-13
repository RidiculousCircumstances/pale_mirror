package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BakeryLabourIntegrationTest {
    record Fixture(FrontierWorldState state, ProductionJob job, long tick) { }

    private static Fixture loadedStation() {
        var state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-labour"), 41L)), StrategicTaskStatus.PENDING);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        for (long tick = 300, turn = 0; turn < 200; tick += 20, turn++) {
            var job = state.productionJobs().get(started.job().id());
            if (job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.PROCESSING)
                return new Fixture(state, job, tick);
            var step = step(state, job, tick);
            state = ProductionProcess.reduceBakeryColdStep(state, job.settlementId(), step);
        }
        throw new AssertionError("ordinary input did not reach station");
    }
    private static BakeryColdStep step(FrontierWorldState state, ProductionJob job, long tick) {
        return ProductionProcess.planCompletion(state, ProductionProcess.complete(job, tick)).stream()
                .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
    }

    @Test void elapsedWorkReplaysWithoutSearchAndNeverTransformsStockOrAwardsCalls() throws Exception {
        var f = loadedStation();
        var start = step(f.state(), f.job(), f.tick());
        assertEquals(BakeryColdStep.Action.LABOUR, start.action());
        var codecs = FrontierWorldPayloadCodecs.create();
        var codec = new FrontierWorldStateCodec();
        assertEquals(start, codecs.decode(start.type(), codecs.encode(start)));
        try (var ignored = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            throw new AssertionError("labour replay must not query navigation");
        })) {
            var running = ProductionProcess.reduceBakeryColdStep(codec.decode(codec.encode(f.state())),
                    f.job().settlementId(), start);
            assertEquals(f.state().inventory(), running.inventory());
            assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryColdStep(
                    running, f.job().settlementId(), start), "duplicate start cannot award labour");
            var progress = BakeryLabour.progress(running, f.job()).orElseThrow();
            assertEquals(0, progress.completedMilliWork());
            assertTrue(progress.activeUntilTick() > f.tick());
        }
        var running = ProductionProcess.reduceBakeryColdStep(f.state(), f.job().settlementId(), start);
        var partial = ProductionProcess.planCompletion(running, ProductionProcess.complete(f.job(), f.tick() + 1));
        assertTrue(partial.stream().noneMatch(event -> event.payload() instanceof BakeryColdStep),
                "an executor call before the retained deadline cannot award another work unit");
        var progress = BakeryLabour.progress(running, f.job()).orElseThrow();
        var finish = step(running, f.job(), progress.activeUntilTick());
        assertTrue(finish.labourChange().orElseThrow().next().complete());
        var complete = ProductionProcess.reduceBakeryColdStep(codec.decode(codec.encode(running)),
                f.job().settlementId(), (BakeryColdStep) codecs.decode(finish.type(), codecs.encode(finish)));
        assertEquals(running.inventory(), complete.inventory(), "labour completion is not station conversion");
        assertEquals(BakeryColdStep.Action.RECIPE, step(complete, f.job(), progress.activeUntilTick() + 1).action());
        assertThrows(IllegalArgumentException.class, () -> BakeryColdStep.Action.fromWireTag(4),
                "the retired invocation-count action must not be reinterpreted");
    }

    @Test void modifierAndOwnerInterruptionSettleOldRateAndExcludeTimeAway() {
        var f = loadedStation();
        var running = ProductionProcess.reduceBakeryColdStep(f.state(), f.job().settlementId(),
                step(f.state(), f.job(), f.tick()));
        var job = running.productionJobs().get(f.job().id());
        var before = BakeryLabour.progress(running, job).orElseThrow();
        var previous = running.humanPopulation().resident(job.workerId()).characteristics().workModifiers();
        var next = previous.with(new ResidentWorkModifiers.Modifier(new SubjectId("modifier:bakery-speed"),
                HumanCapability.INDUSTRY, 2_000));
        var edited = ResidentWorkModifiersProcess.reduce(running, job.workerId(),
                new ResidentWorkModifiersChanged(job.workerId(), f.tick() + 100, previous, next));
        var paused = BakeryLabour.progress(edited, job).orElseThrow();
        assertEquals(before.completedAt(f.tick() + 100), paused.completedMilliWork());
        assertFalse(paused.running());
        var resumed = BakeryLabour.next(edited, edited.productionJobs().get(job.id()), f.tick() + 300, true);
        assertEquals(paused.completedMilliWork(), resumed.completedMilliWork(), "paused time is not work");
        assertEquals(Math.min(edited.bootstrap().ruleset().workCatalog().maxSpeedPermille(), before.ratePermille() * 2),
                resumed.ratePermille());
        var execution = edited.actorExecutions().current(ActorActivityKind.PRODUCTION).get(job.workerId());
        edited = BakeryLabour.change(edited, job, resumed);
        var suspended = ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.PRODUCTION)
                .pause(edited, execution, f.tick() + 350);
        var settled = BakeryLabour.progress(suspended, job).orElseThrow();
        assertFalse(settled.running());
        assertEquals(resumed.completedAt(f.tick() + 350), settled.completedMilliWork());
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(suspended));
        assertEquals(settled, BakeryLabour.progress(recovered, job).orElseThrow());
    }

    @Test void anOwnerLocalStationHoldStopsLabourWithoutChangingStockOrClearingTheHold() {
        var f = loadedStation();
        var running = ProductionProcess.reduceBakeryColdStep(f.state(), f.job().settlementId(),
                step(f.state(), f.job(), f.tick()));
        var job = running.productionJobs().get(f.job().id());
        var work = job.bakeryWork().orElseThrow();
        var hold = new BakeryWorkBlock(BakeryWorkBlock.Reason.MACHINE_UNAVAILABLE,
                work.stationId(), -1, "minecraft:air", 0);
        var blocked = running.withChanges(FrontierWorldStateUpdate.begin().productionJobs(Map.of(job.id(),
                job.withBakeryWork(work.withBlock(Optional.of(hold))))));
        var pause = step(blocked, job, f.tick() + 100);
        assertFalse(pause.labourChange().orElseThrow().next().running());
        var held = ProductionProcess.reduceBakeryColdStep(blocked, job.settlementId(), pause);
        assertEquals(blocked.inventory(), held.inventory());
        assertEquals(Optional.of(hold), held.productionJobs().get(job.id()).bakeryWork().orElseThrow().block());
        assertEquals(BakeryLabour.progress(running, job).orElseThrow().completedAt(f.tick() + 100),
                BakeryLabour.progress(held, job).orElseThrow().completedMilliWork());
        assertTrue(ProductionProcess.planCompletion(held, ProductionProcess.complete(job, f.tick() + 1_000)).stream()
                .noneMatch(event -> event.payload() instanceof BakeryColdStep));
    }

    @Test void stationCannotConvertEarlyAndLabourCannotForgeRateOrPredecessor() {
        var f = loadedStation();
        var start = step(f.state(), f.job(), f.tick());
        var early = new BakeryColdStep(f.job().id(), BakeryWorkState.Phase.PROCESSING,
                BakeryColdStep.Action.RECIPE, start.nextSurface(), start.executionId(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryColdStep(
                f.state(), f.job().settlementId(), early));
        var next = start.labourChange().orElseThrow().next();
        var forged = new BakeryColdStep(start.jobId(), start.expectedPhase(), start.action(), start.nextSurface(),
                start.executionId(), Optional.empty(), Optional.of(new BakeryLabourChange(Optional.empty(),
                new WorkProgress(next.requiredMilliWork(), 1_000, next.evaluatedAtTick(), next.ratePermille(), next.activeUntilTick()))));
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceBakeryColdStep(
                f.state(), f.job().settlementId(), forged));
        var codecs = FrontierWorldPayloadCodecs.create();
        var bytes = codecs.encode(start);
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(start.type(), Arrays.copyOf(bytes, bytes.length - 1)));
    }
}
