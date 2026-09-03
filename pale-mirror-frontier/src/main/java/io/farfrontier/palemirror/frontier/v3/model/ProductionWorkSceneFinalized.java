package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Retires one blocked job only after its exact workshop scene has durably closed. */
public record ProductionWorkSceneFinalized(SceneLeaseId leaseId, SubjectId jobId) implements FrontierPayload {
    public ProductionWorkSceneFinalized {
        Objects.requireNonNull(leaseId, "production scene lease"); Objects.requireNonNull(jobId, "production job");
    }
    @Override public String type() { return "frontier.production_work_scene_finalized"; }
}
