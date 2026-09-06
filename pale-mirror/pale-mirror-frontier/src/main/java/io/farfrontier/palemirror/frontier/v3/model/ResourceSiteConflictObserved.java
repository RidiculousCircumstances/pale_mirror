package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable evidence that one named owned soil or crop cell no longer matches its confirmed field. */
public record ResourceSiteConflictObserved(SubjectId siteId, BlockPosition position, String cause) implements FrontierPayload {
    public ResourceSiteConflictObserved {
        Objects.requireNonNull(siteId, "resource-site conflict site id"); Objects.requireNonNull(position, "resource-site conflict position");
        cause = Objects.requireNonNull(cause, "resource-site conflict cause");
        if (!siteId.value().startsWith("site:") || cause.isBlank()) throw new IllegalArgumentException("resource-site conflict identity is invalid");
    }
    @Override public String type() { return "frontier.resource_site_conflict_observed"; }
}
