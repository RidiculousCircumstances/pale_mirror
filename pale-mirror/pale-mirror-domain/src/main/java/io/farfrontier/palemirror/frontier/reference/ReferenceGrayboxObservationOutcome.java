package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Explicit outcome for a physical observation; rejected facts remain visible to the adapter. */
public record ReferenceGrayboxObservationOutcome(
        String eventId,
        Status status,
        String reason,
        String stateRevision
) {
    public ReferenceGrayboxObservationOutcome {
        eventId = required(eventId, "eventId");
        status = Objects.requireNonNull(status, "status");
        reason = required(reason, "reason");
        stateRevision = required(stateRevision, "stateRevision");
    }

    public boolean applied() { return status == Status.APPLIED; }

    public enum Status { APPLIED, REJECTED_STALE, REJECTED_UNKNOWN, REJECTED_CONFLICT }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
