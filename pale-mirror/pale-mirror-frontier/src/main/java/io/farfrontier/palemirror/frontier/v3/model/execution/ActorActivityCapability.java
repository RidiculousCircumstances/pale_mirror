package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** A declared owner strategy; the common lifecycle cannot inspect family progress or custody. */
public interface ActorActivityCapability {
    ActorActivityKind kind();
    boolean supportsContinuation();
    void validateReference(FrontierWorldState state, ActorExecutionId execution);
    ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution);
    FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick);
    FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick);
}
