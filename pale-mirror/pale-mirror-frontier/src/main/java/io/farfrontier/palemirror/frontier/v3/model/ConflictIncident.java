package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/**
 * Immutable, bounded canonical explanation of the first accepted conflict at one owner.
 * It deliberately stores facts and a trace join key, never an adapter-owned repair decision.
 */
public record ConflictIncident(String id, DiagnosticTuple diagnostic, String source, String expectedFact, String observedFact,
                               String preCanonicalFact, String postCanonicalFact,
                               String traceCorrelation) {
    private static final int MAX_FACT_LENGTH = 192;

    public ConflictIncident {
        id = required(id, "conflict incident id");
        diagnostic = Objects.requireNonNull(diagnostic, "conflict incident diagnostic tuple");
        source = required(source, "conflict incident source");
        expectedFact = required(expectedFact, "conflict expected fact");
        observedFact = required(observedFact, "conflict observed fact");
        preCanonicalFact = required(preCanonicalFact, "conflict pre-canonical fact");
        postCanonicalFact = required(postCanonicalFact, "conflict post-canonical fact");
        traceCorrelation = required(traceCorrelation, "conflict trace correlation");
    }

    private static String required(String value, String label) {
        value = Objects.requireNonNull(value, label);
        if (value.isBlank() || value.length() > MAX_FACT_LENGTH) throw new IllegalArgumentException(label + " is invalid");
        return value;
    }

    /** These read-only accessors expose an already-validated stamped tuple. */
    public DiagnosticCategory category() { return diagnostic.category(); }
    public String reason() { return diagnostic.reason().name(); }
    public io.farfrontier.palemirror.frontier.v3.api.SubjectId ownerId() { return diagnostic.owner().id(); }
    public io.farfrontier.palemirror.frontier.v3.api.SubjectId subjectId() { return diagnostic.subject().id(); }
    public String disposition() { return diagnostic.disposition().name(); }
}
