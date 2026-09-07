package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.Optional;

/** Pure server-thread admission gate for the shared HOT/COLD traversal cadence. */
final class FrontierV3TraversalScheduleGate {
    private FrontierV3TraversalScheduleGate() { }

    /**
     * An observed body may consume only the one retained continuation at its ordinary due
     * turn.  A physical arrival may occur earlier, but it remains observation evidence until
     * the same edge that could have admitted COLD; HOT therefore cannot gain canonical time
     * merely because a player presented the field early.
     */
    static boolean traversalCheckpointBound(CheckpointImage checkpoint, SubjectId jobId) {
        return dueBinding(checkpoint, jobId).isPresent();
    }

    /** A physically arrived exact next cell and its one engine binding are both necessary. */
    static boolean shouldCommitObservedTraversal(CheckpointImage checkpoint, SubjectId jobId, boolean atExactNextCell) {
        return atExactNextCell && traversalCheckpointBound(checkpoint, jobId);
    }

    static Optional<ScheduledAction> binding(CheckpointImage checkpoint, SubjectId jobId) {
        try { return Optional.of(FrontierV3ContinuationBinding.require(checkpoint, jobId,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)); }
        catch (IllegalArgumentException rejected) { return Optional.empty(); }
    }

    /** The exact binding remains available for admission/release; only progress consumes due work. */
    static Optional<ScheduledAction> dueBinding(CheckpointImage checkpoint, SubjectId jobId) {
        return binding(checkpoint, jobId).filter(action -> checkpoint.instant().compareTo(action.dueAt()) >= 0);
    }
}
