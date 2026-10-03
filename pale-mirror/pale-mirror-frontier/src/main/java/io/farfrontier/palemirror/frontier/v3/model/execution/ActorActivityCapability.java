package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** A declared owner strategy; the common lifecycle cannot inspect family progress or custody. */
public interface ActorActivityCapability {
    ActorActivityKind kind();
    /** Explicit owner policy, not inferred from profession, scene or current job shape. */
    enum Interruption { RETAIN_CONTINUATION, RELEASE, TERMINAL_ONLY }
    Interruption interruption();
    default boolean supportsContinuation() { return interruption() == Interruption.RETAIN_CONTINUATION; }
    void validateReference(FrontierWorldState state, ActorExecutionId execution);
    ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution);
    FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick);
    FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick);
    /** Release owners retire their own ephemeral data before common authority changes. */
    FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution);
}
