package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Immutable, bounded canonical explanation of the first accepted conflict at one owner.
 * It deliberately stores facts and a trace join key, never an adapter-owned repair decision.
 */
public record ConflictIncident(String id, ConflictIncidentCategory category, String reason, SubjectId ownerId,
                               SubjectId subjectId, String source, String expectedFact, String observedFact,
                               String preCanonicalFact, String postCanonicalFact, String disposition,
                               String traceCorrelation) {
    private static final int MAX_FACT_LENGTH = 192;

    public ConflictIncident {
        id = required(id, "conflict incident id");
        category = Objects.requireNonNull(category, "conflict incident category");
        reason = required(reason, "conflict incident reason");
        ownerId = Objects.requireNonNull(ownerId, "conflict incident owner");
        subjectId = Objects.requireNonNull(subjectId, "conflict incident subject");
        source = required(source, "conflict incident source");
        expectedFact = required(expectedFact, "conflict expected fact");
        observedFact = required(observedFact, "conflict observed fact");
        preCanonicalFact = required(preCanonicalFact, "conflict pre-canonical fact");
        postCanonicalFact = required(postCanonicalFact, "conflict post-canonical fact");
        disposition = required(disposition, "conflict disposition");
        traceCorrelation = required(traceCorrelation, "conflict trace correlation");
    }

    private static String required(String value, String label) {
        value = Objects.requireNonNull(value, label);
        if (value.isBlank() || value.length() > MAX_FACT_LENGTH) throw new IllegalArgumentException(label + " is invalid");
        return value;
    }
}
