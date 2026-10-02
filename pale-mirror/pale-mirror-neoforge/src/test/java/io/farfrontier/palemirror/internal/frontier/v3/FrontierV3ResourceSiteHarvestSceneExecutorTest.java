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
    private static final SubjectId SITE = new SubjectId("site:test");

    @Test void inspectedRecoveryUsesItsCanonicalFenceNotTheIndependentCarrierClock() {
        var config = io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog
                .resourceSiteHarvestConfiguration(new WorldId("frontier:harvest-recovery-clocks"), 421L);
        var state = config.initialState();
        var site = new SubjectId("site:1-wheat-field");
        var job = (io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob)
                state.resourceSites().site(site).activeWork().orElseThrow();
        var body = state.actorLocations().get(job.workerId()).body();
        var lease = io.farfrontier.palemirror.frontier.v3.model.SceneLease.forCause(
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:harvest-recovery-clocks"),
                config.worldId(), new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause(site, job.id()),
                state.resourceSite(site).cropSlots().get(job.progress().nextCropSlotIndex()), config.initialInstant(), 17L,
                io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.PREPARED,
                List.of(new io.farfrontier.palemirror.frontier.v3.model.SceneMember(job.workerId(),
                        io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(config.worldId(), job.workerId()))),
                java.util.Map.of(job.workerId(), body), java.util.Set.of(), java.util.Optional.empty());
        state = state.prepareSceneLease(lease);
        assertTrue(FrontierV3ResourceSiteHarvestReconciliation.recoveryEpoch(state, lease).isEmpty());
        state = state.transitionSceneLease(lease.id(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CONFLICT);
        var conflict = state.sceneLeases().get(lease.id());
        long canonicalEpoch = state.fencedRecovery().current().get(
                io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(job.workerId()))
                .authorityEpoch();
        var carrier = FrontierV3AmbientActorExecutor.carrierDeclaration(state, job.workerId(),
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, lease.members().getFirst().entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), canonicalEpoch + 5L);
        assertTrue(carrier.epoch() != canonicalEpoch);
        assertEquals(canonicalEpoch, FrontierV3ResourceSiteHarvestReconciliation.recoveryEpoch(state, conflict).orElseThrow());
        assertTrue(FrontierV3ResourceSiteHarvestReconciliation.recoveryEpoch(config.initialState(), conflict).isEmpty());
    }

    @Test void genericRecoveryReleaseUsesTheRegisteredHarvestContinuationAndRejectsMissingAuthority() {
        var config = io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog
                .resourceSiteHarvestConfiguration(new WorldId("frontier:recovery-release-binding"), 421L);
        var state = config.initialState();
        var site = new SubjectId("site:1-wheat-field");
        var job = (io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob)
                state.resourceSites().site(site).activeWork().orElseThrow();
        var worker = job.workerId();
        var body = state.actorLocations().get(worker).body();
        var lease = io.farfrontier.palemirror.frontier.v3.model.SceneLease.forCause(
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:recovery-release-binding"),
                config.worldId(), new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause(site, job.id()),
                body.supportingSurface().support(), config.initialInstant(), 1L,
                io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.DRAINING,
                List.of(new io.farfrontier.palemirror.frontier.v3.model.SceneMember(worker,
                        io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(config.worldId(), worker))),
                java.util.Map.of(worker, body), java.util.Set.of(), java.util.Optional.empty());
        var checkpoint = new CheckpointImage(config.worldId(), new Revision(1L), config.initialInstant(),
                new byte[0], config.initialSchedules(), List.of());
        var expected = FrontierV3ContinuationBinding.require(checkpoint, site, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND);
        assertEquals(java.util.Optional.of(expected),
                FrontierV3SceneBehaviorRegistry.releaseBinding(checkpoint, state, lease),
                "stored recovery must submit the same exact action as normal harvest release");
        var missing = new CheckpointImage(config.worldId(), new Revision(1L), config.initialInstant(),
                new byte[0], List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3SceneBehaviorRegistry.releaseBinding(missing, state, lease),
                "missing authority cannot fall back to an unbound release or a fabricated continuation");
    }

    @Test
    void independentSceneFenceIncidentsCannotShareAnIdempotencyKey() {
        for (var fence : FrontierV3AmbientActorExecutor.SceneCarrierFenceResult.values()) {
            var firstId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("scene:first");
            var secondId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("scene:second");
            var first = FrontierV3ResourceSiteHarvestSceneExecutor.carrierFenceCommandId(firstId, 3L, fence);
            var second = FrontierV3ResourceSiteHarvestSceneExecutor.carrierFenceCommandId(secondId, 3L, fence);
            org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
            assertEquals(first, FrontierV3ResourceSiteHarvestSceneExecutor.carrierFenceCommandId(firstId, 3L, fence));
            org.junit.jupiter.api.Assertions.assertNotEquals(first,
                    FrontierV3ResourceSiteHarvestSceneExecutor.carrierFenceCommandId(firstId, 4L, fence));
        }
    }

    @Test
    void carrierFenceFailureHasAnExactWitnessBeforeAndAfterTheLastCrop() {
        var slots = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.model.BlockPosition>();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            slots.add(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(x + 140, 66, z - 340));
        }
        var site = new io.farfrontier.palemirror.frontier.v3.model.ResourceSite(new SubjectId("site:1-wheat-field"),
                new SubjectId("settlement:1"), new SubjectId("structure:1-farm"),
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind.WHEAT_FIELD,
                io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan.initialGrayboxLayout(slots));
        var progress = io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress.notStarted(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            assertEquals(slots.get(index), FrontierV3ResourceSiteHarvestSceneExecutor.carrierFenceWitness(site, progress));
            progress = progress.prepareNextCrop();
            assertEquals(slots.get(index), FrontierV3ResourceSiteHarvestSceneExecutor.carrierFenceWitness(site, progress));
            progress = progress.confirmPreparedCrop();
        }
        assertTrue(progress.complete());
        assertEquals(slots.getLast(), FrontierV3ResourceSiteHarvestSceneExecutor.carrierFenceWitness(site, progress),
                "a terminal diagnostic must not ask completed work for a nonexistent next crop");
    }

    @Test
    void observedHotTraversalKeepsMovingUnderItsOneSharedContinuationBeforeItsDueTurn() {
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointBound(checkpoint(100L, 102L), SITE));
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointBound(checkpoint(100L, 101L), SITE));
        assertTrue(FrontierV3TraversalScheduleGate.traversalCheckpointBound(checkpoint(100L, 100L), SITE));
        assertTrue(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 102L), SITE, true));
        assertFalse(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 101L), SITE, false));
        assertTrue(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 101L), SITE, true));
        assertTrue(FrontierV3TraversalScheduleGate.shouldCommitObservedTraversal(checkpoint(100L, 100L), SITE, true));
    }

    @Test
    void missingOrAmbiguousContinuationCannotGrantHotProgressAuthority() {
        assertFalse(FrontierV3TraversalScheduleGate.traversalCheckpointBound(emptyCheckpoint(100L), SITE));
        CheckpointImage duplicate = new CheckpointImage(new WorldId("frontier:test"), new Revision(1L), new SimInstant(100L), new byte[0], List.of(
                action(101L), action(101L)), List.of());
        assertFalse(FrontierV3TraversalScheduleGate.traversalCheckpointBound(duplicate, SITE));
    }

    @Test
    void typedBindingRejectsMissingAndDuplicateActionsButRetainsTheExactFutureAction() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ContinuationBinding.require(emptyCheckpoint(100L), SITE,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
        CheckpointImage duplicate = new CheckpointImage(new WorldId("frontier:test"), new Revision(1L), new SimInstant(100L), new byte[0], List.of(
                action(101L), action(102L)), List.of());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ContinuationBinding.require(duplicate, SITE,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
        assertEquals(action(102L), FrontierV3ContinuationBinding.require(checkpoint(100L, 102L), SITE,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
        assertEquals(action(101L), FrontierV3ContinuationBinding.require(checkpoint(100L, 101L), SITE,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND));
    }

    @Test
    void conflictedReleaseDoesNotRequireTheContinuationItsAcceptedConflictCancelled() {
        assertEquals(java.util.Optional.empty(), FrontierV3ResourceSiteHarvestReleaseBinding.forPhase(
                emptyCheckpoint(100L), SITE, ResourceSitePhase.CONFLICT));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceSiteHarvestReleaseBinding.forPhase(
                emptyCheckpoint(100L), SITE, ResourceSitePhase.HARVESTING));
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
        return new ScheduledAction(new ScheduleId("schedule:test"), new SimInstant(dueAt), 0, SITE,
                ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, 1);
    }
}
