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
     * An observed body may consume the one retained continuation before its due turn.  That
     * consumes rather than creates the shared COLD action and preserves its due-derived next
     * cadence, so a loaded visit cannot introduce a second clock or gain unbound authority.
     */
    static boolean traversalCheckpointBound(CheckpointImage checkpoint, SubjectId jobId) {
        return binding(checkpoint, jobId).isPresent();
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
}
