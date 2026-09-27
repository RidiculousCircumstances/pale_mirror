package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** A physical witness blocks or clears only its retained bakery job, never the whole scene. */
public record BakeryHotBlockChanged(SubjectId jobId, SceneLeaseId leaseId, BakeryWorkState.Phase phase,
                                    Optional<BakeryWorkBlock> block) implements FrontierPayload {
    public BakeryHotBlockChanged {
        Objects.requireNonNull(jobId, "bakery block job");
        Objects.requireNonNull(leaseId, "bakery block scene");
        Objects.requireNonNull(phase, "bakery block phase");
        block = Objects.requireNonNull(block, "bakery block disposition");
    }
    @Override public String type() { return "frontier.bakery_hot_block_changed"; }
}
