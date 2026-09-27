package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** WAL boundary before one non-replayable loaded-world bakery transfer or recipe. */
public record BakeryHotEffectPrepared(SubjectId jobId, SceneLeaseId leaseId,
                                      BakeryWorkState.Phase phase, int destinationSlot)
        implements FrontierPayload {
    public BakeryHotEffectPrepared {
        Objects.requireNonNull(jobId, "bakery effect job");
        new BakeryPhysicalStep(Objects.requireNonNull(phase, "bakery effect phase"),
                Objects.requireNonNull(leaseId, "bakery effect lease"), destinationSlot);
    }
    @Override public String type() { return "frontier.bakery_hot_effect_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
