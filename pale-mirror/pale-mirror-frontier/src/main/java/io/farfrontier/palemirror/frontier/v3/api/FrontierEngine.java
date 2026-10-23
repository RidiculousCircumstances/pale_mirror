package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

import java.util.Optional;

/** Narrow canonical mutation/read boundary hosted on the Minecraft server thread. */
public interface FrontierEngine<P extends FrontierProjection> {
    CommandResult submit(FrontierCommand command);

    /** Publishes an exact prepared transaction only after its host confirms durability. */
    boolean advanceDurability();
    boolean hasPendingCommit();
    default boolean hasPendingCalculation() { return false; }
    /** An external intervention invalidates unpublished advice, never committed effects. */
    default void cancelPendingCalculation() { }

    AdvanceResult advanceTo(SimInstant target, WorkBudget budget);

    P projection(ProjectionQuery query);

    CheckpointImage checkpoint();

    /** Capture immutable inputs on the owner; encoding may run independently of later transitions. */
    default java.util.function.Supplier<CheckpointImage> prepareCheckpoint() {
        var captured = checkpoint();
        return () -> captured;
    }

    /** Ordinary execution reads must not request serialized persistence state. */
    FrontierExecutionView executionView();

    /** Exact read-only continuation fence, without materializing the whole schedule queue. */
    boolean retainsScheduledAction(io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction action);

    /** Read-only current retention capacity; expired receipts count exactly as at command admission. */
    CommandAdmissionCapacity commandAdmissionCapacity();

    /** A host with a durable external obligation pins its exact command before submission.
     * After recovery the host must restore these pins before advancing/submitting work.
     * The host releases a pin only after recording the positive receipt durably elsewhere. */
    void pinCommandReceipt(CommandId id);
    void releaseCommandReceipt(CommandId id);
    Optional<CommandReceipt> commandReceipt(CommandId id);

    /** Read-only next due instant for bounded background drivers; it never exposes mutable schedule state. */
    Optional<SimInstant> nextScheduledInstantAfter(SimInstant instant);

    /** Earliest runnable deadline or parked-work audit, including an already-due backlog. */
    Optional<SimInstant> nextExecutionBoundary();

    /** Discards only transactions already covered by a durably installed snapshot. */
    void compact(Revision coveredRevision);

    EngineStatus status();

    /** Original failure, retained only in memory for the host's stack trace; never execution authority. */
    default Optional<RuntimeException> failureCause() { return Optional.empty(); }
}
