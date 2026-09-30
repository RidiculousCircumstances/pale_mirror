package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Retires a body-free field scene whose exact canonical standing column is obstructed. */
public record ResourceSiteHarvestScenePreparationAborted(SceneLeaseId leaseId, SubjectId siteId,
                                                          SubjectId jobId) implements FrontierPayload {
    public ResourceSiteHarvestScenePreparationAborted {
        Objects.requireNonNull(leaseId, "field preparation lease");
        Objects.requireNonNull(siteId, "field preparation site");
        Objects.requireNonNull(jobId, "field preparation job");
    }

    @Override public String type() { return "frontier.resource_site_harvest_scene_preparation_aborted"; }
}
