package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.Optional;

/** Pure server-thread admission gate for the one retained HOT/COLD continuation. */
final class FrontierV3TraversalScheduleGate {
    private FrontierV3TraversalScheduleGate() { }

    static Optional<ScheduledAction> binding(FrontierScheduleView checkpoint,
            io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob job) {
        try {
            var action = FrontierV3ContinuationBinding.require(checkpoint,
                    ResourceSiteHarvestProcess.coldProgress(job, 0L).id(), job.siteId(),
                    ResourceSiteHarvestProcess.COLD_PROGRESS_KIND);
            ResourceSiteHarvestProcess.requireContinuationBinding(job, action);
            return Optional.of(action);
        } catch (IllegalArgumentException rejected) { return Optional.empty(); }
    }
    static Optional<ScheduledAction> dueBinding(FrontierScheduleView checkpoint,
            io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob job) {
        return binding(checkpoint, job).filter(action -> checkpoint.instant().compareTo(action.dueAt()) >= 0);
    }

    /**
     * A physically observed traversal checkpoint is admissible whenever the job still has its
     * one exact continuation.  It records the real HOT pose/cursor but does not consume or
     * reschedule that continuation: semantic crop work remains due-gated below.  This keeps
     * the scheduler out of the body's movement clock without creating a second route or timer.
     */
    static boolean traversalCheckpointBound(FrontierScheduleView checkpoint, SubjectId siteId) {
        return binding(checkpoint, siteId).isPresent();
    }

    /** A physically arrived exact next cell and its one engine binding are both necessary. */
    static boolean shouldCommitObservedTraversal(FrontierScheduleView checkpoint, SubjectId siteId, boolean atExactNextCell) {
        return atExactNextCell && traversalCheckpointBound(checkpoint, siteId);
    }

    static Optional<ScheduledAction> binding(FrontierScheduleView checkpoint, SubjectId siteId) {
        try { return Optional.of(FrontierV3ContinuationBinding.require(checkpoint, siteId,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)); }
        catch (IllegalArgumentException rejected) { return Optional.empty(); }
    }

    /** Irreversible crop mutation may consume the continuation only at its ordinary due turn. */
    static Optional<ScheduledAction> dueBinding(FrontierScheduleView checkpoint, SubjectId siteId) {
        return binding(checkpoint, siteId).filter(action -> checkpoint.instant().compareTo(action.dueAt()) >= 0);
    }
}
