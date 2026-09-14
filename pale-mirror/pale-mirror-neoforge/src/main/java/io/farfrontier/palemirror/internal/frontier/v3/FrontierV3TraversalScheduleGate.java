package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.Optional;

/** Pure server-thread admission gate for the one retained HOT/COLD continuation. */
final class FrontierV3TraversalScheduleGate {
    private FrontierV3TraversalScheduleGate() { }

    /**
     * A physically observed traversal checkpoint is admissible whenever the job still has its
     * one exact continuation.  It records the real HOT pose/cursor but does not consume or
     * reschedule that continuation: semantic crop work remains due-gated below.  This keeps
     * the scheduler out of the body's movement clock without creating a second route or timer.
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

    /** Irreversible crop mutation may consume the continuation only at its ordinary due turn. */
    static Optional<ScheduledAction> dueBinding(CheckpointImage checkpoint, SubjectId jobId) {
        return binding(checkpoint, jobId).filter(action -> checkpoint.instant().compareTo(action.dueAt()) >= 0);
    }
}
