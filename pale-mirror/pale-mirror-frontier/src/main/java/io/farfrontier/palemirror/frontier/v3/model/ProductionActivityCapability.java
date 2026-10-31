package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Production owner, not hunger or the lifecycle, decides its physical effect checkpoint. */
final class ProductionActivityCapability implements ActorActivityCapability {
    private static final ImmutableInvariantCheck<Void> REFERENCES = new ImmutableInvariantCheck<>();
    @Override public ActorActivityKind kind() { return ActorActivityKind.PRODUCTION; }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() {
        return request -> job(request.expectedState(), request.execution()).stationWork().isPresent()
                ? new ActorMovementBodyCheckpoint().acknowledge(request) : ProductionJourneyKnowledge.acknowledge(request);
    }
    @Override public java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(
            FrontierWorldState state, ActorExecutionId execution, long tick) {
        return java.util.List.of(ProductionContinuation.wake(job(state, execution), tick));
    }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public ActorActivityResumption resumptionReference() { return ActorActivityResumption.noRetainedExecutionReference(); }
    @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> {
            job(state, execution);
            // Production's post-death planner refunds only proven pre-effect jobs.
            // A possibly applied transform retains the exact job/input/effect for settlement.
            var changes = FrontierWorldStateUpdate.begin();
            var movement = state.actorMovements().get(execution.actorId());
            if (movement != null) {
                if (!movement.executionId().equals(execution)) throw new IllegalArgumentException("production death cannot retire foreign movement");
                var movements = new java.util.LinkedHashMap<>(state.actorMovements()); movements.remove(execution.actorId());
                changes.actorMovements(movements);
            }
            return new ActorActivityDeath.Acknowledgement(state, execution, changes,
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
        REFERENCES.validate(() -> validateCurrent(jobs, executions), jobs, executions);
    }
    private static void validateCurrent(java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ProductionJob> jobs,
                                    ActorExecutionState executions) {
        executions.current(ActorActivityKind.PRODUCTION).values().forEach(id -> job(jobs, id));
        executions.suspended().stream().filter(id -> id.activityKind() == ActorActivityKind.PRODUCTION)
                .forEach(id -> job(jobs, id));
    }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) { job(state, execution); }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        var job = job(state, execution);
        Optional<ActorActivityCheckpoint.Wait> waiting;
        var status = checkpointStatus(state, job);
        if (status == ResidentWorkYield.Status.OWNER_SAFETY_HOLD) waiting = Optional.of(new ActorActivityCheckpoint.Wait(
                ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, job.id()));
        else if (status == ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT) waiting = Optional.of(
                new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, job.id()));
        else if (state.actorMovements().containsKey(execution.actorId())) waiting = Optional.of(new ActorActivityCheckpoint.Wait(
                ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, job.id()));
        else if (status == ResidentWorkYield.Status.READY) waiting = Optional.empty();
        else throw new IllegalArgumentException("production checkpoint has no declared UAE translation");
        return new ActorActivityCheckpoint(state, execution, waiting);
    }
    @Override public ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state, ActorExecutionId execution) {
        var job = job(state, execution);
        var status = checkpointStatus(state, job);
        return new ActorActivityCheckpoint(state, execution, status == ResidentWorkYield.Status.READY ? Optional.empty()
                : Optional.of(new ActorActivityCheckpoint.Wait(status == ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT
                    ? ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION : ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, job.id())));
    }
    @Override public Optional<ActorActivityAmbientTarget> stationaryAmbientTarget(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        return Optional.of(new ActorActivityAmbientTarget(AmbientGoalKind.WORK,
                state.actorLocations().get(execution.actorId()).supportingSurface()));
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        validateReference(state, execution);
        var movement = state.actorMovements().get(execution.actorId());
        return lease.goal() == AmbientGoalKind.WORK
                    && lease.goalBody().supportingSurface().equals(state.actorLocations().get(execution.actorId()).supportingSurface())
                || lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT && movement != null && movement.executionId().equals(execution)
                && movement.order().legalStations().contains(lease.goalBody().supportingSurface());
    }
    static ResidentWorkYield.Status checkpointStatus(FrontierWorldState state, ProductionJob job) {
        if (job.stationWork().isEmpty()) return ResidentWorkYield.Status.OWNER_SAFETY_HOLD;
        var work = job.stationWork().orElseThrow();
        if (work.pendingPhysicalStep().isPresent()) return ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT;
        if (work.phase() == StationWorkState.Phase.PROCESSING || work.phase() == StationWorkState.Phase.STATION_UNLOAD) {
            StationWorkOperation.require(state, job);
            var machine = state.productionStations().require(work.stationId());
            if (machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent()) return ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT;
        }
        return ResidentWorkYield.Status.READY;
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        return StationWorkLabour.pauseJob(state, job(state, execution), atTick);
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        job(state, execution); return state;
    }
}
