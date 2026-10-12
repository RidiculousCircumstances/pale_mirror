package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Bakery supplies eligibility; generic progress/statistics do not know recipes or jobs. */
public final class BakeryLabour {
    private BakeryLabour() { }

    public static WorkProgress next(FrontierWorldState state, ProductionJob job, long tick, boolean run) {
        var work = job.bakeryWork().orElseThrow();
        if (work.phase() != BakeryWorkState.Phase.PROCESSING || work.pendingPhysicalStep().isPresent()
                || run && work.block().filter(BakeryWorkBlock::requiresPhysicalReconciliation).isPresent())
            throw new IllegalArgumentException("bakery labour needs an open loaded station");
        var catalog = state.bootstrap().ruleset().workCatalog();
        var definition = catalog.require(job.outputItemKind(), WorkOperation.PRODUCE);
        var prior = work.labour().orElseGet(() -> WorkProgress.pending(definition.workUnits(), tick));
        if (prior.requiredMilliWork() != Math.multiplyExact(definition.workUnits(), 1_000L))
            throw new IllegalArgumentException("bakery labour has a foreign work definition");
        var settled = prior.pause(tick);
        if (!run) return settled;
        if (settled.complete()) throw new IllegalArgumentException("completed bakery labour cannot restart");
        var station = BakeryWorkGoal.current(state, job).station();
        if (!state.actorLocations().get(job.workerId()).supportingSurface().equals(station)
                || !state.actorExecutions().owns(job.workerId(), ActorActivityKind.PRODUCTION, job.id())
                || !ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.workerId(), tick))
            throw new IllegalArgumentException("bakery labour needs its working baker at the retained station");
        return settled.resume(tick, ResidentWorkStatistics.speedPermille(
                state.humanPopulation().resident(job.workerId()), definition, catalog),
                ResidentWorkIntervals.permittedUntil(state, job.workerId(), tick));
    }

    public static void require(FrontierWorldState state, ProductionJob job, BakeryLabourChange change) {
        if (!job.bakeryWork().orElseThrow().labour().equals(change.previous())
                || !next(state, job, change.atTick(), change.next().running()).equals(change.next()))
            throw new IllegalArgumentException("bakery labour has stale progress, rate or elapsed work");
    }

    public static FrontierWorldState pauseJob(FrontierWorldState state, ProductionJob job, long tick) {
        var work = job.bakeryWork();
        if (work.isEmpty() || work.orElseThrow().labour().filter(WorkProgress::running).isEmpty()) return state;
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs());
        jobs.put(job.id(), job.withBakeryWork(work.orElseThrow().withLabour(
                work.orElseThrow().labour().orElseThrow().pause(tick))));
        return state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(jobs));
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
