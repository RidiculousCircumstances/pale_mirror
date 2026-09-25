package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One explicitly named site/job pair temporarily owned by a loaded field-work scene. */
public record ResourceSiteHarvestSceneCause(SubjectId siteId, SubjectId jobId) implements SceneCause {
    public ResourceSiteHarvestSceneCause {
        Objects.requireNonNull(siteId, "resource-site harvest scene site");
        Objects.requireNonNull(jobId, "resource-site harvest scene job");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")) {
            throw new IllegalArgumentException("resource-site harvest scene requires declared site and job identities");
        }
    }

    @Override public SceneCauseKind kind() { return SceneCauseKind.RESOURCE_SITE_HARVEST; }
}
