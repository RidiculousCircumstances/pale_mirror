package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Mining is an interruptible retained activity. Food does not destroy its tool, cargo or source reservation. */
final class ExtractionActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.EXTRACTION; }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(
            FrontierWorldState state, ActorExecutionId execution, long tick) {
        validateReference(state, execution);
        return java.util.List.of(ExtractionContinuation.wake(execution.activityOwnerId(), tick));
    }
    @Override public ActorActivityResumption resumptionReference() {
        return request -> {
            var job = ExtractionWorkAuthority.require(request.expectedState(), request.suspended());
            return new ActorActivityResumption.Acknowledgement(request, FrontierWorldStateUpdate.begin().extractionSites(
                    request.expectedState().extractionSites().replaceWork(job, job.resumed(request.successor()))));
        };
    }
    @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> {
            validateReference(state, execution);
            return new ActorActivityDeath.Acknowledgement(state, execution, FrontierWorldStateUpdate.begin(),
                    ActorActivityDeath.Disposition.RETAIN_CAUSAL_OWNER);
        });
    }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() {
        return request -> {
            validateAmbientRelease(request.expectedState(), request.execution());
            return new ActorMovementBodyCheckpoint().acknowledge(request);
        };
    }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) {
        var job = ExtractionWorkAuthority.require(state, execution);
        if (job.pending().isPresent() || ExtractionWorkAuthority.physicalCargo(state, job))
            throw new IllegalArgumentException("extraction body departure retains an unsettled effect or cargo binding");
    }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) { ExtractionWorkAuthority.require(state, execution); }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        validateReference(state, execution); var movement = state.actorMovements().get(execution.actorId());
        return lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT && movement != null && movement.executionId().equals(execution)
                && movement.order().legalStations().contains(lease.goalBody().supportingSurface());
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        var job = ExtractionWorkAuthority.require(state, execution);
        var reason = job.pending().isPresent() ? Optional.of(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION)
                : state.actorMovements().containsKey(execution.actorId()) ? Optional.of(ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY)
                : Optional.<ActorActivityCheckpoint.Reason>empty();
        return new ActorActivityCheckpoint(state, execution, reason.map(value -> new ActorActivityCheckpoint.Wait(value, job.id())));
    }
    @Override public ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state, ActorExecutionId execution) {
        var job = ExtractionWorkAuthority.require(state, execution);
        return new ActorActivityCheckpoint(state, execution, job.pending().map(step ->
                new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, job.id())));
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long tick) {
        var checkpoint = checkpoint(state, execution);
        if (!checkpoint.ready()) throw new IllegalArgumentException("mining pause has no safe effect/movement boundary");
        return ExtractionWorkAuthority.pauseLabour(state, ExtractionWorkAuthority.require(state, execution), tick);
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long tick) {
        validateReference(state, execution); return state;
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("mining must settle its exact resources and return equipment before releasing work");
    }
}
