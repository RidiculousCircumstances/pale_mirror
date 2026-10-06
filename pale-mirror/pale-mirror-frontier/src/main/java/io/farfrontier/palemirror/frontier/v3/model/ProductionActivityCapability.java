package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Production owner, not hunger or the lifecycle, decides its physical effect checkpoint. */
final class ProductionActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.PRODUCTION; }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return ProductionJourneyKnowledge::acknowledge; }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public ActorActivityResumption resumptionReference() { return ActorActivityResumption.noRetainedExecutionReference(); }
    @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> {
            job(state, execution);
            // Production's post-death planner refunds only proven pre-effect jobs.
            // A possibly applied transform retains the exact job/input/effect for settlement.
            return new ActorActivityDeath.Acknowledgement(state, execution, FrontierWorldStateUpdate.begin(),
                    ActorActivityDeath.Disposition.RETAIN_CAUSAL_OWNER);
        });
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("production must retain its continuation or complete through its owner");
    }
    private ProductionJob job(FrontierWorldState state, ActorExecutionId execution) {
        return job(state.productionJobs(), execution);
    }
    private static ProductionJob job(java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ProductionJob> jobs,
                                      ActorExecutionId execution) {
        if (execution.activityKind() != ActorActivityKind.PRODUCTION) throw new IllegalArgumentException("foreign production execution kind");
        var job = jobs.get(execution.activityOwnerId());
        if (job == null || !job.workerId().equals(execution.actorId()))
            throw new IllegalArgumentException("production execution lost its exact job/worker");
        return job;
    }
    static void validateReferences(java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ProductionJob> jobs,
                                    ActorExecutionState executions) {
        executions.current(ActorActivityKind.PRODUCTION).values().forEach(id -> job(jobs, id));
        executions.suspended().stream().filter(id -> id.activityKind() == ActorActivityKind.PRODUCTION)
                .forEach(id -> job(jobs, id));
    }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) { job(state, execution); }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        var job = job(state, execution);
        Optional<ActorActivityCheckpoint.Wait> waiting;
        var status = checkpointStatus(job);
        if (status == ResidentWorkYield.Status.OWNER_SAFETY_HOLD) waiting = Optional.of(new ActorActivityCheckpoint.Wait(
                ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, job.id()));
        else if (status == ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT) waiting = Optional.of(
                new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, job.id()));
        else if (status == ResidentWorkYield.Status.READY) waiting = Optional.empty();
        else throw new IllegalArgumentException("production checkpoint has no declared UAE translation");
        return new ActorActivityCheckpoint(state, execution, waiting);
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        return false; // Production goals are issued by the exact production-scene consumer.
    }
    static ResidentWorkYield.Status checkpointStatus(ProductionJob job) {
        return job.bakeryWork().isEmpty() ? ResidentWorkYield.Status.OWNER_SAFETY_HOLD
                : job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent()
                    ? ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT : ResidentWorkYield.Status.READY;
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        job(state, execution);
        // Bakery progress accrues only by confirmed owned work steps, never elapsed travel time.
        return state;
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        job(state, execution); return state;
    }
}
