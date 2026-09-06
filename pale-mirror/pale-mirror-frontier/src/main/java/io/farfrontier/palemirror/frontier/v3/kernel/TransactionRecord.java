package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CommandReceipt;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable atomic canonical transaction, including the accepted command receipt when applicable. */
public record TransactionRecord(
        TransactionId id,
        WorldId worldId,
        Revision revision,
        SimInstant instant,
        List<FrontierEvent> events,
        Optional<CommandReceipt> acceptedCommandReceipt
) {
    public TransactionRecord {
        Objects.requireNonNull(id, "transaction id");
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(instant, "instant");
        events = List.copyOf(events);
        acceptedCommandReceipt = Objects.requireNonNull(acceptedCommandReceipt, "accepted command receipt");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("transaction must contain at least one event");
        }
        acceptedCommandReceipt.ifPresent(receipt -> {
            if (!id.equals(receipt.transactionId()) || !revision.equals(receipt.revision()) || !instant.equals(receipt.submittedAt())) {
                throw new IllegalArgumentException("accepted command receipt does not match transaction boundary");
            }
        });
    }

    public TransactionRecord(TransactionId id, WorldId worldId, Revision revision, SimInstant instant, List<FrontierEvent> events) {
        this(id, worldId, revision, instant, events, Optional.empty());
    }
}
