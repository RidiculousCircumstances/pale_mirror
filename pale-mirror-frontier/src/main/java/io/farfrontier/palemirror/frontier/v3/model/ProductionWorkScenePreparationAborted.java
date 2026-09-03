package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Closes a prepared workshop scene before it has acquired authority over a worker body.
 *
 * <p>This is intentionally distinct from {@link SceneLeaseReleased}: PREPARED may have
 * emitted a provisional Minecraft projection, but has not crossed the HOT boundary. The
 * closed record is the durable cleanup instruction; no synthetic body observation is allowed.</p>
 */
public record ProductionWorkScenePreparationAborted(SceneLeaseId leaseId, SubjectId jobId) implements FrontierPayload {
    public ProductionWorkScenePreparationAborted {
        Objects.requireNonNull(leaseId, "production preparation lease");
        Objects.requireNonNull(jobId, "production preparation job");
    }
    @Override public String type() { return "frontier.production_work_scene_preparation_aborted"; }
}
