package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable evidence that one named owned soil or crop cell no longer matches its confirmed field. */
public record ResourceSiteConflictObserved(SubjectId siteId, BlockPosition position,
                                           ResourceSiteDiagnosticProducer producer) implements FrontierPayload {
    public ResourceSiteConflictObserved {
        Objects.requireNonNull(siteId, "resource-site conflict site id"); Objects.requireNonNull(position, "resource-site conflict position");
        producer = Objects.requireNonNull(producer, "resource-site conflict producer");
        if (!siteId.value().startsWith("site:")) throw new IllegalArgumentException("resource-site conflict identity is invalid");
    }
    public ResourceSiteConflictReason reason() { return producer.reason(); }
    public ResourceSiteConflictSource source() { return producer.source(); }
    public DiagnosticTuple diagnostic() { return producer.stamp(siteId, siteId); }
    @Override public String type() { return "frontier.resource_site_conflict_observed"; }
}
