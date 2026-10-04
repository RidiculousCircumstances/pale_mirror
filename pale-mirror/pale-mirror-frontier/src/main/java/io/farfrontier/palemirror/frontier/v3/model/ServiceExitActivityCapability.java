package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Optional exit movement is retired by its exact interruption/arrival protocol, not suspended work. */
final class ServiceExitActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.SERVICE_EXIT; }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return ActorActivityBodyCheckpoint.usesActorLocation(); }
    @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("service exit uses its exact movement retirement boundary");
    }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) {
        var movement = state.actorMovements().get(execution.actorId());
        if (execution.activityKind() != kind() || movement == null || !movement.executionId().equals(execution))
            throw new IllegalArgumentException("service-exit capability has no exact movement execution");
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        return new ActorActivityCheckpoint(state, execution, Optional.of(new ActorActivityCheckpoint.Wait(
                ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, execution.activityOwnerId())));
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        var movement = state.actorMovements().get(execution.actorId());
        return lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT
                && movement != null && movement.executionId().equals(execution)
                && movement.order().legalStations().contains(lease.goalBody().supportingSurface());
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("service-exit uses exact movement retirement, not work suspension");
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("service-exit has no retained work continuation");
    }
}
