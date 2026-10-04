package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Actor-owned passive presence has no job, labour or resource continuation. */
final class PresenceActivityCapability implements ActorActivityCapability {
    private final ActorPresencePolicies policies;
    PresenceActivityCapability(ActorPresencePolicies policies) {
        this.policies = java.util.Objects.requireNonNull(policies);
    }
    static void validateReferences(java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ActorLocation> actors,
                                    ActorExecutionState executions) {
        for (var id : executions.current(ActorActivityKind.PRESENCE).values())
            if (!id.activityOwnerId().equals(id.actorId()) || !actors.containsKey(id.actorId())
                    || actors.get(id.actorId()).condition().status() != ActorLifeStatus.ALIVE)
                throw new IllegalArgumentException("presence lost its declared actor owner");
    }
    @Override public ActorActivityKind kind() { return ActorActivityKind.PRESENCE; }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return ActorActivityBodyCheckpoint.usesActorLocation(); }
    @Override public Interruption interruption() { return Interruption.RELEASE; }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) {
        if (execution.activityKind() != kind() || !execution.activityOwnerId().equals(execution.actorId())
                || !state.actorLocations().containsKey(execution.actorId()))
            throw new IllegalArgumentException("presence requires its explicitly declared actor owner");
        policies.requireEligible(state, execution.actorId());
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        return new ActorActivityCheckpoint(state, execution, Optional.empty());
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        return lease.goal() == AmbientGoalKind.PATROL || lease.goal() == AmbientGoalKind.GUARD;
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        return state;
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("passive presence has no suspended continuation");
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("passive presence has no suspended continuation");
    }
}
