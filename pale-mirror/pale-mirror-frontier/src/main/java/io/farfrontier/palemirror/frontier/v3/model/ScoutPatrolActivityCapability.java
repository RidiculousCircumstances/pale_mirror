package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Autonomous scout policy owns its releasable circuit; no suspended job or copied position. */
final class ScoutPatrolActivityCapability implements ActorActivityCapability {
    static void validateReferences(FrontierBootstrap bootstrap, HiveColony colony, ActorExecutionState executions) {
        for (var id : executions.current(ActorActivityKind.SCOUT_PATROL).values())
            requireDeclared(bootstrap, colony, id);
    }
    private static void requireDeclared(FrontierBootstrap bootstrap, HiveColony colony, ActorExecutionId id) {
        if (id.activityKind() != ActorActivityKind.SCOUT_PATROL || !id.activityOwnerId().equals(id.actorId())
                || !FrontierWorldStateSupport.bioform(bootstrap, colony, id.actorId()).isScout())
            throw new IllegalArgumentException("scout activity lost its exact nominal scout owner");
    }
    @Override public ActorActivityKind kind() { return ActorActivityKind.SCOUT_PATROL; }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return ActorActivityBodyCheckpoint.usesActorLocation(); }
    @Override public Interruption interruption() { return Interruption.RELEASE; }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) {
        requireDeclared(state.bootstrap(), state.hiveColony(), id);
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
        validateReference(state, id);
        return new ActorActivityCheckpoint(state, id, Optional.empty());
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
        return lease.goal() == AmbientGoalKind.SCOUT_PATROL && lease.goalBody().supportingSurface().support().equals(
                io.farfrontier.palemirror.frontier.v3.process.HiveScoutPatrolProcess.nextPosition(state, id.actorId(),
                        state.actorLocations().get(id.actorId()).supportingSurface().support()));
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) {
        validateReference(state, id);
        return state;
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long atTick) {
        throw new IllegalArgumentException("scout patrol restarts from actual position, not a suspended circuit");
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long atTick) {
        throw new IllegalArgumentException("scout patrol has no suspended continuation");
    }
}
