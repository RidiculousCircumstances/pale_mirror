package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierExecutionMetricsTest {
    @Test
    void reportsCompleteDueActionStagesWithoutChangingCanonicalBytes() {
        WorldId world = new WorldId("frontier:execution-metrics");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> baseline = withExactReview(
                FrontierWorldRuntimeDefinition.configuration(world, 712L));
        CapturingMetrics metrics = new CapturingMetrics();
        FrontierEngine<FrontierWorldProjection> measured = FrontierEngines.create(baseline.withExecutionMetrics(metrics));
        FrontierEngine<FrontierWorldProjection> control = FrontierEngines.create(baseline);

        var resident = baseline.initialState().humanPopulation().resident(
                baseline.initialState().bootstrap().settlements().getFirst().residents().getFirst().id());
        long due = ResidentNeedProcess.firstReviewAfter(resident, 0L,
                baseline.initialState().bootstrap().ruleset().residentLife()).dueAt().ticks();
        measured.advanceTo(new SimInstant(due), new WorkBudget(128, 512));
        control.advanceTo(new SimInstant(due), new WorkBudget(128, 512));

        assertArrayEquals(control.checkpoint().canonicalState(), measured.checkpoint().canonicalState());
        assertTrue(metrics.stages.contains(FrontierExecutionMetrics.Stage.SCHEDULE_ALLOCATION));
        assertTrue(metrics.stages.contains(FrontierExecutionMetrics.Stage.SCHEDULE_PLAN));
        assertTrue(metrics.stages.contains(FrontierExecutionMetrics.Stage.REDUCTION));
        assertTrue(metrics.stages.contains(FrontierExecutionMetrics.Stage.VALIDATION));
        assertTrue(metrics.stages.contains(FrontierExecutionMetrics.Stage.TRANSACTION));
        assertTrue(metrics.queueObserved, "queue depth and deferred-lag observation is emitted on every canonical advance");
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> withExactReview(
            FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base) {
        var resident = base.initialState().humanPopulation().resident(
                base.initialState().bootstrap().settlements().getFirst().residents().getFirst().id());
        return new FrontierEngineConfiguration<>(base.worldId(), base.initialState(), base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(ResidentNeedProcess.firstReviewAfter(
                        resident, 0L, base.initialState().bootstrap().ruleset().residentLife())),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics(),
                base.kernelQuarantineReporter());
    }

    @Test
    void failedObservationCannotChangeCanonicalOutcome() {
        WorldId world = new WorldId("frontier:faulting-execution-metrics");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> baseline = FrontierWorldRuntimeDefinition.configuration(world, 713L);
        FrontierEngine<FrontierWorldProjection> measured = FrontierEngines.create(baseline.withExecutionMetrics(new FaultingMetrics()));
        FrontierEngine<FrontierWorldProjection> control = FrontierEngines.create(baseline);

        measured.advanceTo(new SimInstant(1L), new WorkBudget(128, 512));
        control.advanceTo(new SimInstant(1L), new WorkBudget(128, 512));

        assertArrayEquals(control.checkpoint().canonicalState(), measured.checkpoint().canonicalState(),
                "a failed timing callback may not alter canonical events, schedules or state");
    }

    private static final class CapturingMetrics implements FrontierExecutionMetrics {
        private final List<Stage> stages = new ArrayList<>();
        private boolean queueObserved;

        @Override public Span begin(Stage stage, String kind, String owner) {
            return () -> stages.add(stage);
        }

        @Override public void observeQueue(SimInstant instant, int queueDepth, Optional<ScheduledAction> deferred) {
            queueObserved = true;
        }

        @Override public Snapshot snapshot() { return Snapshot.empty(); }
    }

    private static final class FaultingMetrics implements FrontierExecutionMetrics {
        @Override public Span begin(Stage stage, String kind, String owner) {
            return () -> { throw new IllegalStateException("synthetic diagnostic close failure"); };
        }

        @Override public void observeQueue(SimInstant instant, int queueDepth, Optional<ScheduledAction> deferred) {
            throw new IllegalStateException("synthetic queue observation failure");
        }

        @Override public Snapshot snapshot() { throw new IllegalStateException("synthetic snapshot failure"); }
    }
}
