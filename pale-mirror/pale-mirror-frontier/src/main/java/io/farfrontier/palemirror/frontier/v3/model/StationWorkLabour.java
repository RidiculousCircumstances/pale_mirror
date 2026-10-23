package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Bakery supplies eligibility; generic progress/statistics do not know recipes or jobs. */
public final class StationWorkLabour {
    private StationWorkLabour() { }

    public static boolean attended(FrontierWorldState state, ProductionJob job) {
        return state.bootstrap().ruleset().production().require(job.stationWork().orElseThrow().recipeId()).processingMode()
                == ProductionRecipe.ProcessingMode.ATTENDED;
    }

    public static Optional<WorkProgress> progress(FrontierWorldState state, ProductionJob job) {
        if (job.stationWork().orElseThrow().phase() != StationWorkState.Phase.PROCESSING) return Optional.empty();
        return StationWorkOperation.require(state, job).progress();
    }
    public static boolean complete(FrontierWorldState state, ProductionJob job) {
        return progress(state, job).filter(WorkProgress::complete).isPresent();
    }
    public static int permille(FrontierWorldState state, ProductionJob job) {
        return progress(state, job).map(value -> (int)(value.completedMilliWork() * 1_000L / value.requiredMilliWork())).orElse(0);
    }
    public static FrontierWorldState change(FrontierWorldState state, ProductionJob job, WorkProgress next) {
        return state.withChanges(FrontierWorldStateUpdate.begin().productionStations(
                ProductionStationProcessing.progress(state, StationWorkOperation.require(state, job), next)));
    }

    public static WorkProgress next(FrontierWorldState state, ProductionJob job, long tick, boolean run) {
        var work = job.stationWork().orElseThrow();
        if (work.phase() != StationWorkState.Phase.PROCESSING || work.pendingPhysicalStep().isPresent()
                || run && work.block().filter(BakeryWorkBlock::requiresPhysicalReconciliation).isPresent())
            throw new IllegalArgumentException("bakery labour needs an open loaded station");
        var catalog = state.bootstrap().ruleset().workCatalog();
        var definition = catalog.require(job.outputItemKind(), WorkOperation.PRODUCE);
        var operation = StationWorkOperation.require(state, job);
        if (state.bootstrap().ruleset().production().require(work.recipeId()).processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC)
            throw new IllegalArgumentException("automatic recipe clock belongs to its machine process, not the baker");
        if (!run) return ProductionStationClock.next(state, operation, tick, Optional.empty());
        if (operation.progress().filter(progress -> progress.completedAt(tick) == progress.requiredMilliWork()).isPresent())
            throw new IllegalArgumentException("completed bakery labour cannot restart");
        var station = StationWorkGoal.current(state, job).station();
        if (!state.actorLocations().get(job.workerId()).supportingSurface().equals(station)
                || !state.actorExecutions().owns(job.workerId(), ActorActivityKind.PRODUCTION, job.id())
                || !ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.workerId(), tick))
            throw new IllegalArgumentException("bakery labour needs its working baker at the retained station");
        return ProductionStationClock.next(state, operation, tick, Optional.of(new ProductionStationClock.Attendance(
                ResidentWorkStatistics.speedPermille(state.humanPopulation().resident(job.workerId()), definition, catalog),
                ResidentWorkIntervals.permittedUntil(state, job.workerId(), tick))));
    }

    public static void require(FrontierWorldState state, ProductionJob job, BakeryLabourChange change) {
        if (!progress(state, job).equals(change.previous())
                || !next(state, job, change.atTick(), change.next().running()).equals(change.next()))
            throw new IllegalArgumentException("bakery labour has stale progress, rate or elapsed work");
    }

    public static FrontierWorldState pauseJob(FrontierWorldState state, ProductionJob job, long tick) {
        var work = job.stationWork();
        if (work.isEmpty() || !attended(state, job) || progress(state, job).filter(WorkProgress::running).isEmpty()) return state;
        return change(state, job, progress(state, job).orElseThrow().pause(tick));
    }
    private static ProductionJob assigned(FrontierWorldState state, HumanAssignment assignment) {
        if (assignment.kind() != HumanAssignmentKind.PRODUCTION)
            throw new IllegalArgumentException("foreign bakery labour assignment");
        var job = state.productionJobs().get(assignment.ownerId().orElseThrow());
        if (job == null || !job.workerId().equals(assignment.residentId()))
            throw new IllegalArgumentException("production labour lost its exact worker/job");
        return job;
    }
    public static FrontierWorldState pause(FrontierWorldState state, HumanAssignment assignment, long tick) {
        return pauseJob(state, assigned(state, assignment), tick);
    }
    public static List<ProposedEvent> workStatsChanged(FrontierWorldState state, HumanAssignment assignment, long tick) {
        var job = assigned(state, assignment);
        var next = ProductionContinuation.at(job, Math.addExact(tick, 1));
        return List.of(new ProposedEvent(job.settlementId(), new ScheduleEffect.Rescheduled(next.id(), next)));
    }
}
