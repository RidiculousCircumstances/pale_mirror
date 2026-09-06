package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/**
 * Versioned physical fact for a non-entity source-graybox subject.
 *
 * <p>The adapter supplies the exact source quantity represented by its
 * claimed physical slot. It does not select a victim, infer a cohort, or
 * invent a semantic effect from an arbitrary world block.</p>
 */
public record ReferenceGrayboxStructureObservation(
        int version,
        String eventId,
        String observedStateRevision,
        Kind kind,
        String subjectId,
        double weight
) {
    public static final int VERSION = 1;

    public ReferenceGrayboxStructureObservation {
        if (version != VERSION) throw new IllegalArgumentException("unsupported graybox structure observation version: " + version);
        eventId = required(eventId, "eventId", 128);
        observedStateRevision = required(observedStateRevision, "observedStateRevision", 64);
        if (!observedStateRevision.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("graybox structure observation revision is invalid");
        kind = Objects.requireNonNull(kind, "kind");
        subjectId = required(subjectId, "subjectId", 192);
        if (!Double.isFinite(weight) || weight <= 0.0d) {
            throw new IllegalArgumentException("graybox structure observation weight must be positive and finite");
        }
    }

    public enum Kind {
        FACILITY_DAMAGED,
        SITE_DAMAGED,
        ROUTE_DAMAGED,
        ORGAN_DAMAGED,
        OPERATION_CARGO_LOST,
        FIELD_POST_CARGO_LOST,
        FIELD_POST_DAMAGED,
        FIELD_LINK_DAMAGED
    }

    private static String required(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
