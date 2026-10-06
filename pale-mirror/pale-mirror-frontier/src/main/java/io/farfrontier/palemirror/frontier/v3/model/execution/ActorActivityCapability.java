package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;

/** A declared owner strategy; the common lifecycle cannot inspect family progress or custody. */
public interface ActorActivityCapability {
    ActorActivityKind kind();
    /** Explicit owner policy, not inferred from profession, scene or current job shape. */
    enum Interruption { RETAIN_CONTINUATION, RELEASE, TERMINAL_ONLY }
    Interruption interruption();
    /** Mandatory explicit spatial handoff policy, independent of interruption policy. */
    ActorActivityBodyCheckpoint bodyCheckpoint();
    /** Closing presentation retains this execution and the independently owned body.
     * It is not an interruption, body departure or route-checkpoint acknowledgement. */
    void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution);
    default boolean supportsContinuation() { return interruption() == Interruption.RETAIN_CONTINUATION; }
    /** Retained owners must explicitly declare how the shared new generation reaches their record. */
    default ActorActivityResumption resumptionReference() {
        throw new IllegalArgumentException("retained owner has no registered resumption-reference policy");
    }
    /** Mandatory for retained work. Other causal owners may retain their exact
     * execution until their coordinated terminal settlement; scenes never own death outcomes. */
    default java.util.Optional<ActorActivityDeath> deathAcknowledgement() { return java.util.Optional.empty(); }
    void validateReference(FrontierWorldState state, ActorExecutionId execution);
    /** Family-owned semantic permission. Presentation purpose alone never grants actuation. */
    boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease);
    ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution);
    FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick);
    FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick);
    /** Release owners retire their own ephemeral data before common authority changes. */
    FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution);
}
