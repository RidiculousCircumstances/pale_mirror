package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;

/** A declared owner strategy; the common lifecycle cannot inspect family progress or custody. */
public interface ActorActivityCapability {
    ActorActivityKind kind();
    default boolean permitsHomeFood(FrontierWorldState state, ActorExecutionId execution) { return true; }
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
    /** Optional owner-retained semantic navigation order. Absence requires a
     * periodically refreshed local directive; it grants no continuing path.
     * Implementations must read their current exact goal without route search. */
    default java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder> navigationOrder(
            FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        return java.util.Optional.empty();
    }
    /** Optional explicit idle/service presentation between legs; generic projection knows no family phase. */
    default java.util.Optional<ActorActivityAmbientTarget> stationaryAmbientTarget(FrontierWorldState state, ActorExecutionId execution) {
        return java.util.Optional.empty();
    }
    /** Family-owned semantic permission. Presentation purpose alone never grants actuation. */
    boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease);
    ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution);
    /** Local avoidance keeps this execution and custody. This is not activity interruption.
     * Owners with a different spatial safety boundary explicitly override this policy. */
    default ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state, ActorExecutionId execution) {
        return checkpoint(state, execution);
    }
    FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick);
    FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick);
    /** Notify the retained owner after its successor authority is installed. Never moves the body. */
    default java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(
            FrontierWorldState state, ActorExecutionId successor, long atTick) {
        validateReference(state, successor);
        return java.util.List.of();
    }
    /** Release owners retire their own ephemeral data before common authority changes. */
    FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution);
}
