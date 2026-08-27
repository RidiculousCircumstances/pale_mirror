package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Outcome of bounded due-action execution for one simulation-time advance. */
public record AdvanceResult(
        Revision revision,
        SimInstant instant,
        List<TransactionId> transactions,
        Optional<ScheduledAction> deferredAction,
        long simulationLagTicks,
        EngineStatus status
) {
    public AdvanceResult {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(instant, "instant");
        transactions = List.copyOf(transactions);
        deferredAction = Objects.requireNonNull(deferredAction, "deferred action");
        if (simulationLagTicks < 0L) {
            throw new IllegalArgumentException("simulation lag cannot be negative");
        }
        Objects.requireNonNull(status, "status");
    }
}
