package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed one-edge arrival of the exact field worker on their retained work topology. */
public record ResourceSiteHarvestTraversalAdvanced(SubjectId jobId, int nextCursor) implements FrontierPayload {
    public ResourceSiteHarvestTraversalAdvanced {
        jobId = Objects.requireNonNull(jobId, "field-work traversal job");
        if (!jobId.value().startsWith("job:site-harvest-") || nextCursor < 1 || nextCursor >= TraversalTopology.MAX_NODES) {
            throw new IllegalArgumentException("field-work traversal advance is invalid");
        }
    }
    @Override public String type() { return "frontier.resource_site_harvest_traversal_advanced"; }
}
