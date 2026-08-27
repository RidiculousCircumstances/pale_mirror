package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.List;
import java.util.Objects;

/** Immutable test-store transaction. Wave 2 moves the same fact to WAL/snapshot storage. */
public record TransactionRecord(
        TransactionId id,
        WorldId worldId,
        Revision revision,
        SimInstant instant,
        List<FrontierEvent> events
) {
    public TransactionRecord {
        Objects.requireNonNull(id, "transaction id");
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(instant, "instant");
        events = List.copyOf(events);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("transaction must contain at least one event");
        }
    }
}
