package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

import java.util.Optional;

/** Narrow canonical mutation/read boundary hosted on the Minecraft server thread. */
public interface FrontierEngine<P extends FrontierProjection> {
    CommandResult submit(FrontierCommand command);

    AdvanceResult advanceTo(SimInstant target, WorkBudget budget);

    P projection(ProjectionQuery query);

    CheckpointImage checkpoint();

    /** Ordinary execution reads must not request serialized persistence state. */
    FrontierExecutionView executionView();

    /** Exact read-only continuation fence, without materializing the whole schedule queue. */
    boolean retainsScheduledAction(io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction action);

    /** Read-only current retention capacity; expired receipts count exactly as at command admission. */
    CommandAdmissionCapacity commandAdmissionCapacity();

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
