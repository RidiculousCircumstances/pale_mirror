package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

import java.util.Optional;

/** Narrow canonical mutation/read boundary hosted on the Minecraft server thread. */
public interface FrontierEngine<P extends FrontierProjection> {
    CommandResult submit(FrontierCommand command);

    AdvanceResult advanceTo(SimInstant target, WorkBudget budget);

    P projection(ProjectionQuery query);

    CheckpointImage checkpoint();

    /** Read-only next due instant for bounded background drivers; it never exposes mutable schedule state. */
    Optional<SimInstant> nextScheduledInstantAfter(SimInstant instant);

    /** Discards only transactions already covered by a durably installed snapshot. */
    void compact(Revision coveredRevision);

    EngineStatus status();
}
