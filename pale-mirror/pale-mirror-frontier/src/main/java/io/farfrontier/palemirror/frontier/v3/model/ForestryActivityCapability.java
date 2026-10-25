package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Family-owned safe checkpoints. Tool/cargo do not prevent food between bounded effects. */
final class ForestryActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.FORESTRY; }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) { ForestryWorkAuthority.require(state, execution); }
    @Override public List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(
            FrontierWorldState state, ActorExecutionId execution, long tick) {
        validateReference(state, execution); return List.of(ForestryContinuation.wake(execution.activityOwnerId(), tick));
    }
    @Override public ActorActivityResumption resumptionReference() {
        return request -> {
            var state = request.expectedState(); var job = ForestryWorkAuthority.require(state, request.suspended());
            return new ActorActivityResumption.Acknowledgement(request, FrontierWorldStateUpdate.begin().forest(
                    state.forest().replaceWork(job, job.resumed(request.successor()))));
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
        return request -> { validateAmbientRelease(request.expectedState(), request.execution());
            return new ActorMovementBodyCheckpoint().acknowledge(request); };
    }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) {
        var job = ForestryWorkAuthority.require(state, execution);
        if (job.pending().isPresent() || ForestryWorkAuthority.physicalCargo(state, job))
            throw new IllegalArgumentException("forestry departure retains an unsettled effect/cargo binding");
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        validateReference(state, execution); var movement = state.actorMovements().get(execution.actorId());
        return lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT && movement != null && movement.executionId().equals(execution)
                && movement.order().legalStations().contains(lease.goalBody().supportingSurface());
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        var job = ForestryWorkAuthority.require(state, execution);
        var reason = job.pending().isPresent() || job.felling().isPresent() ? Optional.of(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION)
                : state.actorMovements().containsKey(execution.actorId()) ? Optional.of(ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY)
                : Optional.<ActorActivityCheckpoint.Reason>empty();
        return new ActorActivityCheckpoint(state, execution, reason.map(value -> new ActorActivityCheckpoint.Wait(value, job.id())));
    }
    @Override public ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state, ActorExecutionId execution) {
        var job = ForestryWorkAuthority.require(state, execution);
        return new ActorActivityCheckpoint(state, execution, job.pending().isPresent() || job.felling().isPresent()
                ? Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, job.id())) : Optional.empty());
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long tick) {
        if (!checkpoint(state, execution).ready()) throw new IllegalArgumentException("forestry pause lacks a safe boundary");
        return ForestryWorkAuthority.pauseLabour(state, ForestryWorkAuthority.require(state, execution), tick);
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long tick) { validateReference(state, execution); return state; }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("forestry must settle its exact tool/cargo before release");
    }
}
