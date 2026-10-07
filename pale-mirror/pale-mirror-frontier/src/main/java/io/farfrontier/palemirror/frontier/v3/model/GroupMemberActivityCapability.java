package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import java.util.Optional;

/** Group participation retains purpose across self-care; individual UAE remains the sole actuator owner. */
final class GroupMemberActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.GROUP_MEMBER; }
    @Override public boolean permitsHomeFood(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        var group = state.unitGroups().groups().get(execution.activityOwnerId());
        return io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts.require(group)
                .permitsHomeFood(state, group);
    }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return new ActorMovementBodyCheckpoint(); }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { validateReference(state, execution); }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) {
        var group = state.unitGroups().groups().get(execution.activityOwnerId());
        if (execution.activityKind() != kind() || group == null || group.phase() == UnitGroup.Phase.CLOSED)
            throw new IllegalArgumentException("group member execution lost its exact active group");
        group.member(execution.actorId());
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        return new ActorActivityCheckpoint(state, execution, Optional.empty());
    }
    @Override public ActorActivityResumption resumptionReference() {
        return request -> { validateReference(request.expectedState(), request.suspended());
            return new ActorActivityResumption.Acknowledgement(request, FrontierWorldStateUpdate.begin()); };
    }
    @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> {
            validateReference(state, execution);
            return new ActorActivityDeath.Acknowledgement(state, execution, FrontierWorldStateUpdate.begin(),
                    ActorActivityDeath.Disposition.RETAIN_CAUSAL_OWNER);
        });
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        validateReference(state, execution); var movement = state.actorMovements().get(execution.actorId());
        return lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT && movement != null && movement.executionId().equals(execution)
                && movement.order().legalStations().contains(lease.goalBody().supportingSurface());
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long tick) {
        validateReference(state, execution);
        if (state.actorMovements().containsKey(execution.actorId())) throw new IllegalArgumentException("group movement must acknowledge its interruption first");
        return state;
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long tick) { validateReference(state, execution); return state; }
    @Override public java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(
            FrontierWorldState state, ActorExecutionId successor, long tick) {
        validateReference(state, successor);
        state.actorExecutions().requireCurrent(successor);
        return java.util.List.of(UnitGroupContinuation.wake(successor.activityOwnerId(), tick));
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("group purpose ends only through the group completion protocol");
    }
    static void validateReferences(io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState groups, ActorExecutionState executions) {
        for (var actor : executions.actors().values()) for (var id : java.util.stream.Stream.concat(actor.current().stream(), actor.suspended().stream()).toList()) {
            if (id.activityKind() != ActorActivityKind.GROUP_MEMBER) continue;
            var group = groups.groups().get(id.activityOwnerId());
            if (group == null || group.phase() == UnitGroup.Phase.CLOSED) throw new IllegalArgumentException("group execution has no retained roster");
            group.member(id.actorId());
        }
    }
}
