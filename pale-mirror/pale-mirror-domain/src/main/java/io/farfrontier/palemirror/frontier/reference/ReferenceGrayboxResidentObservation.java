package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/**
 * Versioned, validated fact reported by one managed Villager materialization.
 *
 * <p>The event describes a physical fact, never an instruction to invent a
 * casualty. Its source-state revision is checked before the appropriate
 * canonical custodian mutates named-person state.</p>
 */
public record ReferenceGrayboxResidentObservation(
        int version,
        String eventId,
        String observedStateRevision,
        String residentId,
        Kind kind
) {
    public static final int VERSION = 1;

    public ReferenceGrayboxResidentObservation {
        if (version != VERSION) throw new IllegalArgumentException("unsupported graybox resident observation version: " + version);
        eventId = required(eventId, "eventId");
        observedStateRevision = required(observedStateRevision, "observedStateRevision");
        residentId = required(residentId, "residentId");
        kind = Objects.requireNonNull(kind, "kind");
        if (eventId.length() > 128) throw new IllegalArgumentException("graybox resident observation event ID is too long");
        if (!observedStateRevision.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("graybox resident observation revision is invalid");
        if (!residentId.startsWith("resident:")) throw new IllegalArgumentException("graybox resident observation target is invalid");
    }

    public static ReferenceGrayboxResidentObservation killed(String eventId, String revision, String residentId) {
        return new ReferenceGrayboxResidentObservation(VERSION, eventId, revision, residentId, Kind.KILLED);
    }

    public enum Kind { KILLED, WOUNDED }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
