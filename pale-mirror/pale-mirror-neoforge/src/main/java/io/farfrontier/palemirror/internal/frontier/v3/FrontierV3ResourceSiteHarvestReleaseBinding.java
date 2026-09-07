package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;

import java.util.Optional;

/**
 * Resolves the sole engine-owned continuation required by a normal harvest-scene release.
 *
 * <p>An accepted player conflict has already cancelled that continuation in the same durable
 * transaction. Its subsequent body-exit receipt must therefore be unbound; requiring the
 * retired action would turn an authoritative conflict into a server quarantine.</p>
 */
final class FrontierV3ResourceSiteHarvestReleaseBinding {
    private FrontierV3ResourceSiteHarvestReleaseBinding() { }

    static Optional<ScheduledAction> forPhase(CheckpointImage checkpoint, SubjectId jobId,
                                              ResourceSitePhase phase) {
        return phase == ResourceSitePhase.HARVESTING
                ? Optional.of(FrontierV3ContinuationBinding.require(checkpoint, jobId,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND))
                : Optional.empty();
    }
}
