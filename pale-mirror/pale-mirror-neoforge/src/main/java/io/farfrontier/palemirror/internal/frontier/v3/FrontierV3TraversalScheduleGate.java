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
     * The physical executor runs before the canonical engine advances one server turn. A due
     * action at {@code instant + 1} is therefore the one it may satisfy; a missing, duplicate,
     * or later action gives the loaded observer no progress authority.
     */
    static boolean traversalCheckpointDue(CheckpointImage checkpoint, SubjectId jobId) {
        return dueBinding(checkpoint, jobId).isPresent();
    }

    /** A physically arrived next cell is necessary, but the schedule turn is the sole authority. */
    static boolean shouldCommitObservedTraversal(CheckpointImage checkpoint, SubjectId jobId, boolean atExactNextCell) {
        return atExactNextCell && traversalCheckpointDue(checkpoint, jobId);
    }

    static Optional<ScheduledAction> dueBinding(CheckpointImage checkpoint, SubjectId jobId) {
        try { return Optional.of(FrontierV3ContinuationBinding.requireDueNextTurn(checkpoint, jobId,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)); }
        catch (IllegalArgumentException rejected) { return Optional.empty(); }
    }
}
