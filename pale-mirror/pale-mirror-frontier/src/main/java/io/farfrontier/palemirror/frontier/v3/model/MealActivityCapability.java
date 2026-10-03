package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** A portion remains owned until consumption or explicit resource reconciliation. */
final class MealActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.MEAL; }
    @Override public boolean supportsContinuation() { return false; }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) {
        var meal = state.humanPopulation().meals().get(execution.actorId());
        if (execution.activityKind() != kind() || meal == null || !meal.executionId().equals(execution))
            throw new IllegalArgumentException("meal capability has no exact retained portion execution");
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        var meal = state.humanPopulation().meals().get(execution.actorId());
        return new ActorActivityCheckpoint(state, execution, Optional.of(new ActorActivityCheckpoint.Wait(
                meal.pendingPhysicalStep().isPresent() ? ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION
                        : ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, execution.activityOwnerId())));
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("meal must settle its portion before replacement");
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("meal has no suspended continuation");
    }
}
