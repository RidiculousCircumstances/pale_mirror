package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Migration owner retains the existing non-interruptible journey policy, not body ownership. */
final class TransitActivityCapability implements ActorActivityCapability {
    static void requireDeclared(io.farfrontier.palemirror.frontier.v3.api.SubjectId resident, ActorExecutionId execution) {
        java.util.Objects.requireNonNull(execution, "migration execution identity");
        if (!execution.actorId().equals(resident) || !execution.activityOwnerId().equals(resident)
                || execution.activityKind() != ActorActivityKind.TRANSIT)
            throw new IllegalArgumentException("migration input has a foreign actor, owner or activity kind");
    }
    @Override public ActorActivityKind kind() { return ActorActivityKind.TRANSIT; }
    @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
    static void validateReferences(HumanPopulation people, ActorExecutionState executions) {
        for (var journey : people.migrations().values()) executions.requireCurrent(journey.executionId());
        for (var id : executions.current(ActorActivityKind.TRANSIT).values()) {
            var journey = people.migration(id.actorId());
            if (journey == null || !journey.executionId().equals(id))
                throw new IllegalArgumentException("transit execution lost its exact retained journey");
        }
    }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) {
        var journey = state.humanPopulation().migration(execution.actorId());
        if (execution.activityKind() != kind() || journey == null || !journey.executionId().equals(execution))
            throw new IllegalArgumentException("transit capability has no exact declared journey");
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        return new ActorActivityCheckpoint(state, execution, Optional.of(new ActorActivityCheckpoint.Wait(
                ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, execution.activityOwnerId())));
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("migration must reach its exact terminal journey boundary");
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("migration block reopening is owner-local, not a suspended activity");
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("migration completes or cancels through its exact owner");
    }
}
