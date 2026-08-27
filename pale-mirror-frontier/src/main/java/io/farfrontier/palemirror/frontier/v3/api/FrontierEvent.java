package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Immutable accepted fact. Events are never revised or reinterpreted in place. */
public record FrontierEvent(
        int schemaVersion,
        EventId id,
        TransactionId transactionId,
        WorldId worldId,
        Revision revision,
        SimInstant instant,
        SubjectId subject,
        CauseChain causes,
        FrontierPayload payload
) {
    public static final int SCHEMA_VERSION = 1;

    public FrontierEvent {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported event schema version: " + schemaVersion);
        }
        Objects.requireNonNull(id, "event id");
        Objects.requireNonNull(transactionId, "transaction id");
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(causes, "causes");
        Objects.requireNonNull(payload, "payload");
    }
}
