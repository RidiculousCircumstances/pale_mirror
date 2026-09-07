package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
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
    void observedHotTraversalConsumesTheOneSharedContinuationBeforeItsDueTurn() {
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointBound(checkpoint(100L, 102L), JOB));
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointBound(checkpoint(100L, 101L), JOB));
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointBound(checkpoint(100L, 100L), JOB));
        assertTrue(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 102L), JOB, true));
        assertFalse(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 101L), JOB, false));
        assertTrue(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 101L), JOB, true));
    }

    @Test
    void missingOrAmbiguousContinuationCannotGrantHotProgressAuthority() {
        assertFalse(FrontierV3TraversalScheduleGate.traversalCheckpointBound(emptyCheckpoint(100L), JOB));
        CheckpointImage duplicate = new CheckpointImage(new WorldId("frontier:test"), new Revision(1L), new SimInstant(100L), new byte[0], List.of(
                action(101L), action(101L)), List.of());
        assertFalse(FrontierV3TraversalScheduleGate.traversalCheckpointBound(duplicate, JOB));
    }

    @Test
    void typedBindingRejectsMissingAndDuplicateActionsButRetainsTheExactFutureAction() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ContinuationBinding.require(emptyCheckpoint(100L), JOB,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
        CheckpointImage duplicate = new CheckpointImage(new WorldId("frontier:test"), new Revision(1L), new SimInstant(100L), new byte[0], List.of(
                action(101L), action(102L)), List.of());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ContinuationBinding.require(duplicate, JOB,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
        assertEquals(action(102L), FrontierV3ContinuationBinding.require(checkpoint(100L, 102L), JOB,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
        assertEquals(action(101L), FrontierV3ContinuationBinding.require(checkpoint(100L, 101L), JOB,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
    }

    @Test
    void conflictedReleaseDoesNotRequireTheContinuationItsAcceptedConflictCancelled() {
        assertEquals(java.util.Optional.empty(), FrontierV3ResourceSiteHarvestReleaseBinding.forPhase(
                emptyCheckpoint(100L), JOB, ResourceSitePhase.CONFLICT));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceSiteHarvestReleaseBinding.forPhase(
                emptyCheckpoint(100L), JOB, ResourceSitePhase.HARVESTING));
    }

    @Test
    void descendingPhysicalEdgeMayRemainInFlightButCannotBecomeAnotherTraversal() {
        SurfaceAnchor current = SurfaceAnchor.at(390, 64, -370);
        SurfaceAnchor next = SurfaceAnchor.at(389, 63, -370);
        assertTrue(FrontierV3TraversalEdgeEnvelope.contains(new BodyPosition(389, 65, -370), current, next),
                "the horizontal successor before its one-block descent is still the retained edge");
        assertFalse(FrontierV3TraversalEdgeEnvelope.contains(new BodyPosition(388, 65, -370), current, next),
                "an off-edge body remains a visible physical conflict rather than a route choice");
    }

    @Test
    void typedPreLeaseCannotFreezeColdTraversalOutsidePhysicalDemand() {
        assertFalse(FrontierV3PreLeaseDemandGate.holdsForTypedHandoff(true, false),
                "an unloaded candidate has no physical body authority and must release COLD");
        assertTrue(FrontierV3PreLeaseDemandGate.holdsForTypedHandoff(true, true),
                "a demanded typed candidate may retain its exact ambient body for hand-off");
        assertFalse(FrontierV3PreLeaseDemandGate.holdsForTypedHandoff(false, true));
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
