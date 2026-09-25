package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.EngineStatus;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HeldHarvestSchedulingCompositionTest {
    @Test
    void carrierFenceConflictBeforeAndAfterLastCropRemainsLocalThroughJournalAndRecovery() {
        for (boolean complete : List.of(false, true)) {
            var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
            var state = hot.state().transitionPhysicalIntent(hot.job().intentId(),
                    io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
            if (complete) state = ResourceSiteHarvestProcessTest.completeHarvestWorkHot(state, hot.site(), hot.job(), hot.lease().id());
            state = state.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING);
            var job = (ResourceSiteHarvestJob) state.resourceSites().site(hot.site()).activeWork().orElseThrow();
            assertEquals(complete, job.progress().complete());
            var site = FrontierResourceSitePlan.compile(state.bootstrap()).get(hot.site());
            var witness = complete ? site.cropSlots().getLast() : site.cropSlots().get(job.progress().nextCropSlotIndex());
            var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed());
            var own = ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
            var otherId = new SubjectId("site:2-wheat-field");
            var other = ResourceSiteProcess.preparation(otherId, 22_302L);
            var journal = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord>();
            var config = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(22_300L),
                    base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                    base.limits(), List.of(own, other),
                    (io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter) (transaction, durability) -> journal.add(transaction),
                    base.stateValidator());
            var engine = FrontierEngines.createCanonicalStateAccess(config);
            var initial = engine.checkpoint();
            var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:carrier-fence-" + complete);
            var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, id, base.worldId(), initial.revision(),
                    initial.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                    io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id),
                    new ResourceSiteConflictObserved(hot.site(), witness, ResourceSiteDiagnosticProducer.SCENE_CARRIER_FENCE));
            var result = engine.submit(command);
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, result, result.toString());
            assertEquals(1, journal.size(), "conflict and exact continuation cancellation are atomic");
            assertEquals(List.of(other), engine.checkpoint().schedules());
            var conflicted = engine.canonicalState().state();
            var disposition = conflicted.resourceSites().site(hot.site()).conflictDisposition().orElseThrow();
            assertEquals(witness, disposition.position());
            assertEquals(ResourceSiteConflictReason.CARRIER_FENCE_UNRESOLVED, disposition.reason());
            assertEquals(ResourceSiteConflictPolicy.RECOVERY_INSPECTION_REQUIRED, disposition.policy());
            assertEquals(job, conflicted.resourceSites().site(hot.site()).activeWork().orElseThrow());
            var sceneId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:carrier-scene-conflict-" + complete);
            var checkpoint = engine.checkpoint();
            var sceneResult = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, sceneId,
                    base.worldId(), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                    io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(sceneId),
                    new SceneLeaseTransition(hot.lease().id(), SceneLeaseStatus.CONFLICT)));
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, sceneResult, sceneResult.toString());
            conflicted = engine.canonicalState().state();
            assertEquals(SceneLeaseStatus.CONFLICT, conflicted.sceneLeases().get(hot.lease().id()).status());
            var recovered = FrontierEngines.recoverCanonicalStateAccess(config,
                    new RecoveryImage(base.worldId(), Optional.of(new SnapshotRecord(initial, 0L)), journal));
            assertEquals(conflicted, recovered.canonicalState().state());
            var advanced = recovered.advanceTo(other.dueAt(), new WorkBudget(1, 64));
            assertEquals(EngineStatus.Kind.ACTIVE, advanced.status().kind(), advanced.status().failureDetail().orElse(""));
            assertEquals(ResourceSitePhase.GROWING, recovered.canonicalState().state().resourceSites().site(otherId).phase());
            assertEquals(disposition, recovered.canonicalState().state().resourceSites().site(hot.site()).conflictDisposition().orElseThrow());
        }
    }

    @Test
    void fieldConflictCancelsItsContinuationAtomicallyAndRetainsDispositionOnRecovery() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var state = hot.state();
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed());
        var continuation = ResourceSiteHarvestProcess.coldProgress(hot.job(), 22_301L);
        var durable = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord>();
        var config = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(22_300L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(continuation),
                (io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter) (transaction, durability) -> durable.add(transaction),
                base.stateValidator());
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        var initial = engine.checkpoint();
        var crop = FrontierResourceSitePlan.compile(state.bootstrap()).get(hot.site()).cropSlots().getFirst();
        var observation = new ResourceSiteConflictObserved(hot.site(), crop, ResourceSiteDiagnosticProducer.PLAYER_REMOVED);
        var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:atomic-harvest-retirement");
        var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, id, base.worldId(), initial.revision(),
                initial.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), observation);

        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(command));
        assertEquals(1, durable.size(), "disposition and cancellation must be a single durable transaction");
        assertTrue(engine.checkpoint().schedules().isEmpty());
        var conflicted = engine.canonicalState().state();
        assertEquals(ResourceSitePhase.CONFLICT, conflicted.resourceSites().site(hot.site()).phase());
        assertEquals(hot.job(), conflicted.resourceSites().site(hot.site()).activeWork().orElseThrow(),
                "retain the exact work witness until physical release, not runnable work");
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFLICTED,
                conflicted.physicalIntents().get(hot.job().intentId()).status());
        var replayed = FrontierEngines.recoverCanonicalStateAccess(config,
                new RecoveryImage(base.worldId(), Optional.of(new SnapshotRecord(initial, 0L)), durable));
        assertEquals(engine.canonicalState().state(), replayed.canonicalState().state());
        assertTrue(replayed.checkpoint().schedules().isEmpty());
        var terminal = engine.checkpoint();
        var restored = FrontierEngines.recoverCanonicalStateAccess(config,
                new RecoveryImage(base.worldId(), Optional.of(new SnapshotRecord(terminal, 1L)), List.of()));
        assertEquals(engine.canonicalState().state(), restored.canonicalState().state());
        assertTrue(restored.checkpoint().schedules().isEmpty());
    }

    @Test
    void realHotHarvestDoesNotBlockAnotherFieldsPreparationOrRecoveredGrowth() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(1);
        var state = hot.state();
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed());
        var retained = ResourceSiteHarvestProcess.coldProgress(hot.job(), 22_301L);
        var other = new SubjectId("site:2-wheat-field");
        var preparation = ResourceSiteProcess.preparation(other, 22_302L);
        var config = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(22_300L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(retained, preparation), base.transactionCommitter(), base.stateValidator());
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        var first = engine.advanceTo(new SimInstant(22_302L), new WorkBudget(1, 64));
        assertEquals(EngineStatus.Kind.ACTIVE, first.status().kind(), first.status().failureDetail().orElse(""));
        assertEquals(ResourceSitePhase.GROWING, engine.canonicalState().state().resourceSites().site(other).phase());
        assertEquals(hot.job(), engine.canonicalState().state().resourceSites().site(hot.site()).activeWork().orElseThrow());
        var cp = engine.checkpoint();
        assertTrue(cp.schedules().contains(retained), "independent work cannot replace the HOT owner's exact deadline");
        var growth = cp.schedules().stream().filter(action -> action.subject().equals(other)).findFirst().orElseThrow();
        var recovered = FrontierEngines.recoverCanonicalStateAccess(config,
                new RecoveryImage(cp.worldId(), Optional.of(new SnapshotRecord(cp, cp.revision().value())), List.of()));
        var second = recovered.advanceTo(growth.dueAt(), new WorkBudget(1, 64));
        assertEquals(EngineStatus.Kind.ACTIVE, second.status().kind(), second.status().failureDetail().orElse(""));
        assertEquals(1, recovered.canonicalState().state().resourceSites().site(other).growthStage());
        assertEquals(hot.job(), recovered.canonicalState().state().resourceSites().site(hot.site()).activeWork().orElseThrow());
        assertTrue(recovered.checkpoint().schedules().contains(retained));
        var releasedMembers = hot.lease().members().stream().map(member -> {
            var actor = recovered.canonicalState().state().actorLocations().get(member.actorId());
            return new SceneMemberPosition(member.actorId(), actor.body(), actor.condition().health());
        }).toList();
        var releasePayloads = List.<io.farfrontier.palemirror.frontier.v3.api.FrontierPayload>of(
                new SceneLeaseTransition(hot.lease().id(), SceneLeaseStatus.DRAINING),
                new SceneLeaseReleased(hot.lease().id(), releasedMembers));
        for (int index = 0; index < releasePayloads.size(); index++) {
            var before = recovered.checkpoint();
            var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:held-harvest-release-" + index);
            var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(2, id, base.worldId(), before.revision(), before.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), releasePayloads.get(index),
                    index == 1 ? Optional.of(new io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding(before.revision(), retained)) : Optional.empty());
            var result = recovered.submit(command);
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, result, result.toString());
        }
        assertTrue(recovered.checkpoint().schedules().contains(retained), "release cannot rebase the held deadline");
        var releasedCheckpoint = recovered.checkpoint();
        var resumed = FrontierEngines.recoverCanonicalStateAccess(config,
                new RecoveryImage(releasedCheckpoint.worldId(), Optional.of(new SnapshotRecord(releasedCheckpoint,
                        releasedCheckpoint.revision().value())), List.of()));
        var progress = resumed.advanceTo(releasedCheckpoint.instant(), new WorkBudget(1, 64));
        assertEquals(EngineStatus.Kind.ACTIVE, progress.status().kind(), progress.status().failureDetail().orElse(""));
        var continued = (ResourceSiteHarvestJob) resumed.canonicalState().state().resourceSites().site(hot.site()).activeWork().orElseThrow();
        assertEquals(hot.job().id(), continued.id());
        assertEquals(hot.job().workerId(), continued.workerId());
        assertTrue(!hot.job().equals(continued)
                        || !hot.state().actorLocations().get(continued.workerId()).body().equals(
                                resumed.canonicalState().state().actorLocations().get(continued.workerId()).body()),
                "the released overdue owner must resume actual-body travel or crop work after recovery");
    }
}
