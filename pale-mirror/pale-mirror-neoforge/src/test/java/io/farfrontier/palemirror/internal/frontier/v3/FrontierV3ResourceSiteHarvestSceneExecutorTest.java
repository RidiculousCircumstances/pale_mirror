package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3ResourceSiteHarvestSceneExecutorTest {
    private static final SubjectId JOB = new SubjectId("job:test");

    @Test
    void hotTraversalMayCommitOnlyOnTheSharedColdDueTurn() {
        assertFalse(FrontierV3TraversalScheduleGate.traversalCheckpointDue(checkpoint(100L, 102L), JOB));
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointDue(checkpoint(100L, 101L), JOB));
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointDue(checkpoint(100L, 100L), JOB));
        assertFalse(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 102L), JOB, true));
        assertFalse(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 101L), JOB, false));
        assertTrue(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 101L), JOB, true));
    }

    @Test
    void missingOrAmbiguousColdTurnCannotGrantHotProgressAuthority() {
        assertFalse(FrontierV3TraversalScheduleGate.traversalCheckpointDue(emptyCheckpoint(100L), JOB));
        CheckpointImage duplicate = new CheckpointImage(new WorldId("frontier:test"), new Revision(1L), new SimInstant(100L), new byte[0], List.of(
                action(101L), action(101L)), List.of());
        assertFalse(FrontierV3TraversalScheduleGate.traversalCheckpointDue(duplicate, JOB));
    }

    @Test
    void typedBindingRejectsMissingDuplicateAndLaterActionBeforeHotAuthority() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ContinuationBinding.require(emptyCheckpoint(100L), JOB,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
        CheckpointImage duplicate = new CheckpointImage(new WorldId("frontier:test"), new Revision(1L), new SimInstant(100L), new byte[0], List.of(
                action(101L), action(102L)), List.of());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ContinuationBinding.require(duplicate, JOB,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ContinuationBinding.requireDueNextTurn(checkpoint(100L, 102L), JOB,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
        assertEquals(action(101L), FrontierV3ContinuationBinding.requireDueNextTurn(checkpoint(100L, 101L), JOB,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
    }

    private static CheckpointImage checkpoint(long instant, long dueAt) {
        return new CheckpointImage(new WorldId("frontier:test"), new Revision(1L), new SimInstant(instant), new byte[0], List.of(action(dueAt)), List.of());
    }

    private static CheckpointImage emptyCheckpoint(long instant) {
        return new CheckpointImage(new WorldId("frontier:test"), new Revision(1L), new SimInstant(instant), new byte[0], List.of(), List.of());
    }

    private static ScheduledAction action(long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:test"), new SimInstant(dueAt), 0, JOB,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, 1);
    }
}
