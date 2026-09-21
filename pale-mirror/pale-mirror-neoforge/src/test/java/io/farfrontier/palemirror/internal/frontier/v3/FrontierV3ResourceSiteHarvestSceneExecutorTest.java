package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3ResourceSiteHarvestSceneExecutorTest {
    private static final SubjectId JOB = new SubjectId("job:test");

    @Test
    void observedHotTraversalKeepsMovingUnderItsOneSharedContinuationBeforeItsDueTurn() {
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointBound(checkpoint(100L, 102L), JOB));
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointBound(checkpoint(100L, 101L), JOB));
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointBound(checkpoint(100L, 100L), JOB));
        assertTrue(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 102L), JOB, true));
        assertFalse(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 101L), JOB, false));
        assertTrue(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 101L), JOB, true));
        assertTrue(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 100L), JOB, true));
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
    void exactLocalEnvelopePermitsOnlyTheInFlightSupportsOfItsOneRetainedFieldEdge() {
        SurfaceAnchor current = SurfaceAnchor.at(137, 63, -7);
        SurfaceAnchor next = SurfaceAnchor.at(137, 63, -6);
        assertTrue(FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(new BlockPos(136, 63, -7), current, next),
                "the collision-authoritative neighbouring support is an explicit bounded in-flight latitude, not a new route");
        assertFalse(FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(new BlockPos(135, 63, -7), current, next),
                "the field executor must still reject a body outside the canonical edge envelope");
    }

    @Test
    void completedCursorRetainsOnlyItsImmediatelyPrecedingEdgeUntilTheBodyReachesItsStation() {
        SurfaceAnchor previous = SurfaceAnchor.at(137, 63, -7);
        SurfaceAnchor current = SurfaceAnchor.at(137, 63, -6);
        assertTrue(FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(new BlockPos(137, 63, -7), previous, current),
                "a just-committed cursor may still physically occupy its exact predecessor support for one local turn");
        assertFalse(FrontierV3SemanticMovement.withinRetainedEdgeEnvelope(new BlockPos(135, 63, -7), previous, current),
                "the post-checkpoint bridge remains confined to the same immutable predecessor/current edge");
    }

    @Test
    void typedPreLeaseCannotFreezeColdTraversalOutsidePhysicalDemand() {
        assertFalse(FrontierV3PreLeaseDemandGate.holdsForTypedHandoff(true, false),
                "an unloaded candidate has no physical body authority and must release COLD");
        assertTrue(FrontierV3PreLeaseDemandGate.holdsForTypedHandoff(true, true),
                "a demanded typed candidate may retain its exact ambient body for hand-off");
        assertFalse(FrontierV3PreLeaseDemandGate.holdsForTypedHandoff(false, true));
    }

    @Test
    void registeredHarvestStandingPolicyReportsItsOwnTypedObstruction() {
        assertEquals("HARVEST_STATION_OBSTRUCTED", FrontierV3SceneBehaviorRegistry.standingUnavailableReason(
                SceneCauseKind.RESOURCE_SITE_HARVEST));
        assertEquals("AWAITING_EXACT_FLOOR", FrontierV3SceneBehaviorRegistry.standingUnavailableReason(
                SceneCauseKind.ENGINEERING_WORKSITE));
        assertTrue(FrontierV3SceneBehaviorRegistry.preLeaseStandingPositionProvider(SceneCauseKind.PRODUCTION_WORK) != null,
                "a retained workshop worker must have the same typed pre-lease body placement after restart as before hand-off");
    }

    @Test
    void idleFieldReleaseFencesThePresentWorkerBeforeGenericDrainGraceExpires() {
        assertTrue(FrontierV3ResourceSiteHarvestSceneExecutor.immediateColdRelease(false, false, false),
                "a no-demand field with no pending irreversible crop must hand off while its exact body remains naturally loaded");
        assertFalse(FrontierV3ResourceSiteHarvestSceneExecutor.immediateColdRelease(false, true, false),
                "a nearby player retains the visible worker and cannot trigger COLD release");
        assertFalse(FrontierV3ResourceSiteHarvestSceneExecutor.immediateColdRelease(true, false, false),
                "present local demand remains HOT authority");
        assertFalse(FrontierV3ResourceSiteHarvestSceneExecutor.immediateColdRelease(false, false, true),
                "a prepared crop retains its exact physical observation boundary");
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
