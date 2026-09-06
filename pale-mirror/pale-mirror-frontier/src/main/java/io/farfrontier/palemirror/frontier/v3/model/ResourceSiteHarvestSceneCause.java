package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact harvest job temporarily owned by a loaded field-work scene. */
public record ResourceSiteHarvestSceneCause(SubjectId jobId) implements SceneCause {
    public ResourceSiteHarvestSceneCause {
        Objects.requireNonNull(jobId, "resource-site harvest scene job");
        if (!jobId.value().startsWith("job:site-harvest-")) {
            throw new IllegalArgumentException("resource-site harvest scene requires a harvest job");
        }
    }

    @Override public SceneCauseKind kind() { return SceneCauseKind.RESOURCE_SITE_HARVEST; }
}
