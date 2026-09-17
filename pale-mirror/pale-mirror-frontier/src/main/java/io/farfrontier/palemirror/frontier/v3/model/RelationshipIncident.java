package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** First immutable local relationship failure retained by its smallest owner. */
public record RelationshipIncident(FrontierDomainRelationships.Kind kind, SubjectId ownerId, SubjectId sourceId,
                                   String expected, String observed, FrontierDomainRelationships.IncidentReason reason,
                                   FrontierDomainRelationships.Disposition disposition, long canonicalRevision,
                                   String traceCorrelation) {
    public RelationshipIncident {
        Objects.requireNonNull(kind, "relationship incident kind"); Objects.requireNonNull(ownerId, "relationship incident owner");
        Objects.requireNonNull(sourceId, "relationship incident source"); Objects.requireNonNull(expected, "relationship incident expected");
        Objects.requireNonNull(observed, "relationship incident observed"); Objects.requireNonNull(reason, "relationship incident reason");
        Objects.requireNonNull(disposition, "relationship incident disposition"); Objects.requireNonNull(traceCorrelation, "relationship incident trace");
        if (canonicalRevision < 0L || expected.isBlank() || observed.isBlank() || traceCorrelation.isBlank()) throw new IllegalArgumentException("relationship incident facts are invalid");
    }
}
