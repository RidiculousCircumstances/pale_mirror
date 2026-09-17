package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding;
import io.farfrontier.palemirror.frontier.v3.api.EngineStatus;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteHarvestProcessTest {
    @Test
    void fieldWorkerRetainsOneAdjacentTopologyAndCannotPrepareACropBeforeObservedArrival() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L)).get(1).payload();
        ResourceSiteHarvestJob job = started.job();
        assertEquals(tasked.actorLocations().get(job.workerId()).supportingSurface(), job.traversal().linearCorridorSurfaces().getFirst());
        assertEquals(64, job.traversal().linearCorridorSurfaces().size() - job.firstCropCursor());
        for (int index = 1; index < job.traversal().linearCorridorSurfaces().size(); index++) {
            SurfaceAnchor prior = job.traversal().linearCorridorSurfaces().get(index - 1), next = job.traversal().linearCorridorSurfaces().get(index);
            assertEquals(1, Math.abs(prior.x() - next.x()) + Math.abs(prior.z() - next.z()));
            assertTrue(Math.abs(prior.y() - next.y()) <= 1);
        }
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        FrontierWorldState harvesting = ResourceSiteHarvestProcess.reduceStarted(active, site, started);
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceCropPrepared(harvesting, site,
                new ResourceSiteHarvestCropPrepared(job.id(), 0)));
        FrontierWorldState atField = advanceToCurrentCropStation(harvesting, site, job.id());
        ResourceSiteHarvestJob arrived = (ResourceSiteHarvestJob) atField.resourceSites().site(site).activeWork().orElseThrow();
        assertTrue(arrived.atCurrentCropStation());
        assertEquals(arrived, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(atField))
                .resourceSites().site(site).activeWork().orElseThrow(), "restart retains the same field-work cursor and topology");
    }

    @Test
    void ambientHandoffRebasesOnlyAnUnstartedFieldTopologyToTheObservedFarmer() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        harvesting = harvesting.transitionPhysicalIntent(started.job().intentId(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) harvesting.resourceSites().site(site).activeWork().orElseThrow();
        SurfaceAnchor observedSurface = SurfaceAnchor.at(job.traversal().linearCorridorSurfaces().getFirst().x() + 1,
                job.traversal().linearCorridorSurfaces().getFirst().y(), job.traversal().linearCorridorSurfaces().getFirst().z());
        BodyPosition observedBody = BodyPosition.aboveSupportCell(observedSurface.support());
        BlockPosition crop = FrontierResourceSitePlan.compile(harvesting.bootstrap()).get(site).cropSlots().getFirst();
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:site-harvest-observed-handoff"), harvesting.bootstrap().worldId(),
                new ResourceSiteHarvestSceneCause(job.id()), crop, new SimInstant(22_200L), 0L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(harvesting.bootstrap().worldId(), job.workerId()))),
                java.util.Map.of(job.workerId(), observedBody), java.util.Set.of(job.workerId()), Optional.empty());
        ResourceSiteHarvestSceneLeaseHandoff handoff = new ResourceSiteHarvestSceneLeaseHandoff(lease,
                List.of(new SceneMemberPosition(job.workerId(), observedBody, harvesting.actorLocations().get(job.workerId()).condition().health())));

        FrontierWorldState rebased = ResourceSiteHarvestProcess.rebaseForAmbientHandoff(harvesting, new SubjectId("settlement:1"), handoff);
        ResourceSiteHarvestJob accepted = (ResourceSiteHarvestJob) rebased.resourceSites().site(site).activeWork().orElseThrow();
        assertEquals(observedSurface, accepted.traversal().linearCorridorSurfaces().getFirst());
        assertEquals(0, accepted.traversalCursor());
        assertEquals(accepted, (ResourceSiteHarvestJob) new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(rebased))
                .resourceSites().site(site).activeWork().orElseThrow(), "restart retains the observed HOT hand-off topology");

        FrontierWorldState advanced = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(
                rebased.withActorBody(job.workerId(), accepted.traversal().linearCorridorSurfaces().getFirst().standingBody()), site,
                new ResourceSiteHarvestColdTraversalAdvanced(job.id(), job.workerId(), 1));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.rebaseForAmbientHandoff(advanced, new SubjectId("settlement:1"), handoff),
                "a later hand-off may not erase retained worker progress");
    }

    @Test
    void ambientHandoffAfterColdProgressRetainsTheSameCursorInsteadOfRebasingIt() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction cold =
                ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created) planned.get(3).payload()).action();
        ResourceSiteHarvestColdTraversalAdvanced advanced = (ResourceSiteHarvestColdTraversalAdvanced)
                ResourceSiteHarvestProcess.planColdProgress(harvesting, cold).getFirst().payload();
        FrontierWorldState afterCold = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(harvesting, site, advanced);
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) afterCold.resourceSites().site(site).activeWork().orElseThrow();
        BodyPosition retained = job.traversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
        BlockPosition crop = FrontierResourceSitePlan.compile(afterCold.bootstrap()).get(site).cropSlots().getFirst();
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:site-harvest-cold-handoff"), afterCold.bootstrap().worldId(),
                new ResourceSiteHarvestSceneCause(job.id()), crop, new SimInstant(22_200L), 0L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(afterCold.bootstrap().worldId(), job.workerId()))),
                java.util.Map.of(job.workerId(), retained), java.util.Set.of(job.workerId()), Optional.empty());
        ResourceSiteHarvestSceneLeaseHandoff handoff = new ResourceSiteHarvestSceneLeaseHandoff(lease,
                List.of(new SceneMemberPosition(job.workerId(), retained, afterCold.actorLocations().get(job.workerId()).condition().health())));

        FrontierWorldState accepted = ResourceSiteHarvestProcess.rebaseForAmbientHandoff(afterCold, new SubjectId("settlement:1"), handoff);

        assertSame(afterCold, accepted, "a late hand-off proves the retained COLD station; it must not rewrite topology or cursor");
    }

    @Test
    void releasedFieldSceneUsesItsRegisteredContinuationInsteadOfLogisticsCargoPolicy() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        harvesting = harvesting.transitionPhysicalIntent(started.job().intentId(), PhysicalIntentStatus.RUNNING, Optional.empty());
        harvesting = advanceToCurrentCropStation(harvesting, site, started.job().id());
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) harvesting.resourceSites().site(site).activeWork().orElseThrow();
        BodyPosition workerBody = BodyPosition.aboveSupportCell(job.traversal().linearCorridorSurfaces().get(job.traversalCursor()).support());
        harvesting = harvesting.withActorBody(job.workerId(), workerBody);
        BlockPosition currentCrop = FrontierResourceSitePlan.compile(harvesting.bootstrap()).get(site).cropSlots().get(job.progress().nextCropSlotIndex());
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:site-harvest-release"), harvesting.bootstrap().worldId(),
                new ResourceSiteHarvestSceneCause(job.id()), currentCrop, new SimInstant(22_200L), 0L,
                SceneLeaseStatus.PREPARED, List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(harvesting.bootstrap().worldId(), job.workerId()))),
                java.util.Map.of(job.workerId(), workerBody), java.util.Set.of(job.workerId()), Optional.empty());
        FrontierWorldState draining = harvesting.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT)
                .transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        BodyPosition observedBetweenRetainedSurfaces = new BodyPosition(workerBody.x() + 1, workerBody.y(), workerBody.z());
        SceneLeaseReleased released = new SceneLeaseReleased(lease.id(), List.of(new SceneMemberPosition(job.workerId(), observedBetweenRetainedSurfaces,
                draining.actorLocations().get(job.workerId()).condition().health())));

        ScheduledAction retained = ResourceSiteHarvestProcess.coldProgress(job, 22_220L);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuation = FrontierSceneContinuationPlanner.releaseEvents(draining, lease, 22_220L, released,
                Optional.of(retained));

        assertEquals(List.of(new SubjectId("settlement:1")), continuation.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::subject).toList());
        assertEquals(released, continuation.getFirst().payload());
        FrontierWorldState releasedState = draining.releaseSceneLease(lease.id(), released.members());
        assertEquals(SceneLeaseStatus.CLOSED, releasedState.sceneLeases().get(lease.id()).status());
        assertEquals(workerBody, releasedState.actorLocations().get(job.workerId()).body(),
                "a partial Minecraft movement body must not replace the retained harvest cursor on COLD release");
    }

    @Test
    void closedFieldSceneRemainsValidWhileItsExactReceiptConsumesTheCompletedJob() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        PhysicalIntentPrepared prepared = (PhysicalIntentPrepared) planned.get(2).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, prepared.intent());
        harvesting = harvesting.transitionPhysicalIntent(started.job().intentId(), PhysicalIntentStatus.RUNNING, Optional.empty());
        harvesting = advanceToCurrentCropStation(harvesting, site, started.job().id());
        ResourceSiteHarvestJob atField = (ResourceSiteHarvestJob) harvesting.resourceSites().site(site).activeWork().orElseThrow();
        BodyPosition workerBody = BodyPosition.aboveSupportCell(atField.traversal().linearCorridorSurfaces().get(atField.traversalCursor()).support());
        harvesting = harvesting.withActorBody(atField.workerId(), workerBody);
        BlockPosition crop = FrontierResourceSitePlan.compile(harvesting.bootstrap()).get(site).cropSlots().get(atField.progress().nextCropSlotIndex());
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:site-harvest-confirmation"), harvesting.bootstrap().worldId(),
                new ResourceSiteHarvestSceneCause(atField.id()), crop, new SimInstant(22_200L), 0L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(atField.workerId(), SceneLease.deterministicEntityId(harvesting.bootstrap().worldId(), atField.workerId()))),
                java.util.Map.of(atField.workerId(), workerBody), java.util.Set.of(atField.workerId()), Optional.empty());
        FrontierWorldState hot = harvesting.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        FrontierWorldState completeWork = completeHarvestWorkHot(hot, site, atField, lease.id());
        FrontierWorldState closed = completeWork.transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING)
                .releaseSceneLease(lease.id(), List.of(new SceneMemberPosition(atField.workerId(), workerBody,
                        completeWork.actorLocations().get(atField.workerId()).condition().health())));
        ExactItemStack output = new ExactItemStack(atField.outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, atField.outputSlot());
        FrontierWorldState confirmed = closed.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED,
                Optional.of(receipt(prepared.intent(), atField, output)));

        assertEquals(SceneLeaseStatus.CLOSED, confirmed.sceneLeases().get(lease.id()).status());
        assertEquals(ResourceSitePhase.GROWING, confirmed.resourceSites().site(site).phase());
        assertEquals(output, confirmed.inventory().items().get(output.id()));
    }

    @Test
    void terminalReceiptCanReleaseItsSameDrainingFarmerAfterTheJobIsConsumed() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        PhysicalIntentPrepared prepared = (PhysicalIntentPrepared) planned.get(2).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, prepared.intent());
        harvesting = harvesting.transitionPhysicalIntent(started.job().intentId(), PhysicalIntentStatus.RUNNING, Optional.empty());
        harvesting = advanceToCurrentCropStation(harvesting, site, started.job().id());
        ResourceSiteHarvestJob atField = (ResourceSiteHarvestJob) harvesting.resourceSites().site(site).activeWork().orElseThrow();
        BodyPosition workerBody = BodyPosition.aboveSupportCell(atField.traversal().linearCorridorSurfaces().get(atField.traversalCursor()).support());
        harvesting = harvesting.withActorBody(atField.workerId(), workerBody);
        BlockPosition crop = FrontierResourceSitePlan.compile(harvesting.bootstrap()).get(site).cropSlots().get(atField.progress().nextCropSlotIndex());
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:site-harvest-terminal-receipt"), harvesting.bootstrap().worldId(),
                new ResourceSiteHarvestSceneCause(atField.id()), crop, new SimInstant(22_200L), 0L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(atField.workerId(), SceneLease.deterministicEntityId(harvesting.bootstrap().worldId(), atField.workerId()))),
                java.util.Map.of(atField.workerId(), workerBody), java.util.Set.of(atField.workerId()), Optional.empty());
        FrontierWorldState completeWork = completeHarvestWorkHot(harvesting.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT),
                site, atField, lease.id());
        ExactItemStack output = new ExactItemStack(atField.outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, atField.outputSlot());
        FrontierWorldState receiptFirst = completeWork.transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING)
                .transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt(prepared.intent(), atField, output)));
        SceneLeaseReleased released = new SceneLeaseReleased(lease.id(), List.of(new SceneMemberPosition(atField.workerId(), workerBody,
                receiptFirst.actorLocations().get(atField.workerId()).condition().health())));

        assertTrue(FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(receiptFirst, new ResourceSiteHarvestSceneCause(atField.id())));
        assertEquals(List.of(released), FrontierSceneContinuationPlanner.releaseEvents(receiptFirst,
                receiptFirst.sceneLeases().get(lease.id()), 22_220L, released, Optional.empty()).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).toList());
        FrontierWorldState releasedState = receiptFirst.releaseSceneLease(lease.id(), released.members());
        assertEquals(SceneLeaseStatus.CLOSED, releasedState.sceneLeases().get(lease.id()).status());
        assertEquals(ResourceSitePhase.GROWING, releasedState.resourceSites().site(site).phase());
        assertEquals(workerBody, releasedState.actorLocations().get(atField.workerId()).body(),
                "the terminal receipt releases the same observed farmer instead of leaving a draining body at the last crop");
    }

    @Test
    void outputReceiptIsRejectedUntilTheDurablyPreparedCropCursorCompletes() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        PhysicalIntentPrepared prepared = (PhysicalIntentPrepared) planned.get(2).payload();
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, prepared.intent());
        harvesting = harvesting.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ExactItemStack output = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, started.job().outputSlot());
        FrontierWorldState state = harvesting;
        assertThrows(IllegalArgumentException.class, () -> state.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED,
                Optional.of(receipt(prepared.intent(), started.job(), output))));

        harvesting = advanceToCurrentCropStation(harvesting, site, started.job().id());
        harvesting = ResourceSiteHarvestProcess.reduceCropPrepared(harvesting, site, new ResourceSiteHarvestCropPrepared(started.job().id(), 0));
        assertTrue(harvesting.resourceSites().site(site).activeWork().orElseThrow() instanceof ResourceSiteHarvestJob);
        ResourceSiteHarvestJob pending = (ResourceSiteHarvestJob) harvesting.resourceSites().site(site).activeWork().orElseThrow();
        assertTrue(pending.progress().hasPendingCrop());
        ResourceSiteHarvestCropPrepared cropPrepared = new ResourceSiteHarvestCropPrepared(started.job().id(), 0);
        assertEquals(cropPrepared, FrontierWorldRuntimeDefinition.payloadCodecs().decode(cropPrepared.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(cropPrepared)));
        assertEquals(pending, (ResourceSiteHarvestJob) new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(harvesting))
                .resourceSites().site(site).activeWork().orElseThrow());
        harvesting = ResourceSiteHarvestProcess.reduceProgressed(harvesting, site, new ResourceSiteHarvestProgressed(started.job().id(), 1));
        assertEquals(1, ((ResourceSiteHarvestJob) harvesting.resourceSites().site(site).activeWork().orElseThrow()).progress().completedCropSlots());
    }

    @Test
    void deadFarmerConflictsTheExactFieldWorkAndBlocksItsStrategicTask() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        harvesting = harvesting.transitionPhysicalIntent(((PhysicalIntentPrepared) planned.get(2).payload()).intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) harvesting.resourceSites().site(site).activeWork().orElseThrow();
        BlockPosition crop = FrontierResourceSitePlan.compile(harvesting.bootstrap()).get(site).cropSlots().getFirst();
        SceneMember member = new SceneMember(job.workerId(), SceneLease.deterministicEntityId(new WorldId("frontier:resource-site-harvest"), job.workerId()));
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:site-harvest-worker-death"), new WorldId("frontier:resource-site-harvest"),
                new ResourceSiteHarvestSceneCause(job.id()), crop, new SimInstant(22_200L), 0L, SceneLeaseStatus.PREPARED, List.of(member),
                SceneLease.bodiesAboveSupportCells(java.util.Map.of(job.workerId(), harvesting.actorLocations().get(job.workerId()).supportingSurface().support())),
                java.util.Set.of(), Optional.empty());
        harvesting = harvesting.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);

        FrontierWorldState afterDeath = harvesting.recordActorDeath(new ActorDied(lease.id(), job.workerId(), lease.memberPosition(job.workerId()), "test:fall"), 22_201L);

        assertEquals(ResourceSitePhase.CONFLICT, afterDeath.resourceSites().site(site).phase());
        assertEquals(StrategicTaskStatus.BLOCKED, afterDeath.strategicPlans().tasks().get(task.id()).status());
        assertEquals(ActorLifeStatus.DEAD, afterDeath.actorLocations().get(job.workerId()).condition().status());
    }

    @Test
    void playerFieldBreakAdmissionAtomicallyRetiresTheExactHarvestOwner() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob job = hot.job();
        BlockPosition crop = FrontierResourceSitePlan.compile(hot.state().bootstrap()).get(hot.site()).cropSlots().getFirst();
        ResourceSiteConflictObserved observed = new ResourceSiteConflictObserved(hot.site(), crop,
                ResourceSiteConflictReason.PLAYER_REMOVED_MANAGED_CELL, ResourceSiteConflictSource.PLAYER_WORLD_OBSERVATION);
        CommandId commandId = new CommandId("command:site-harvest-player-break");
        FrontierCommand command = new FrontierCommand(1, commandId, hot.state().bootstrap().worldId(), new Revision(1L), new SimInstant(22_302L),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), observed);

        assertTrue(FrontierWorldProcessCatalog.planCommand("resource-sites", hot.state(), command) instanceof CommandPlan.Accepted,
                "the trusted physical owner must durably admit the player observation before vanilla may mutate the cell");
        assertTrue(ResourceSiteProcess.planConflict(hot.state(), observed).stream()
                        .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                        .anyMatch(new ScheduleEffect.Cancelled(ResourceSiteHarvestProcess.coldProgress(job, 0L).id())::equals),
                "the accepted conflict cancels its exact durable continuation rather than leaving a stale due action");
        FrontierWorldState conflicted = ResourceSiteProcess.reduceConflict(hot.state(), hot.site(), observed);

        assertEquals(ResourceSitePhase.CONFLICT, conflicted.resourceSites().site(hot.site()).phase());
        assertEquals(PhysicalIntentStatus.CONFLICTED, conflicted.physicalIntents().get(job.intentId()).status(),
                "a player action reaches terminal owned disposition rather than restart custody");
        assertEquals(ResourceSiteConflictReason.PLAYER_REMOVED_MANAGED_CELL,
                conflicted.resourceSites().site(hot.site()).conflictDisposition().orElseThrow().reason());
        assertEquals(ResourceSiteConflictPolicy.TERMINAL_REPAIR_REQUIRED,
                conflicted.resourceSites().site(hot.site()).conflictDisposition().orElseThrow().policy());
        ConflictIncident incident = conflicted.resourceSites().site(hot.site()).conflictDisposition().orElseThrow().incident();
        assertEquals(ConflictIncidentCategory.PLAYER_WORLD_DISRUPTION, incident.category());
        assertEquals(ResourceSiteConflictSource.PLAYER_WORLD_OBSERVATION.name(), incident.source());
        assertEquals("conflict:incident:resource-site:" + hot.site().value().substring("site:".length()), incident.traceCorrelation(),
                "the harvest's terminal canonical disposition retains the exact player-observation trace join");
        var bindingId = FencedRecoveryPhysicalIntentSupport.bindingId(hot.state().physicalIntents().get(job.intentId()));
        assertFalse(conflicted.fencedRecovery().current().containsKey(bindingId),
                "a direct player action must retire its fence instead of creating restart inspection custody");
        assertEquals(FencedRecoveryDisposition.ABANDON, conflicted.fencedRecovery().tombstones().get(bindingId).disposition());
        assertEquals(conflicted.resourceSites().site(hot.site()).conflictDisposition(),
                new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(conflicted)).resourceSites().site(hot.site()).conflictDisposition(),
                "the precise player disposition must survive canonical recovery rather than becoming generic CONFLICT");
        assertEquals(StrategicTaskStatus.BLOCKED, conflicted.strategicPlans().tasks().get(job.taskId()).status());
        assertEquals(SceneLeaseStatus.DRAINING, conflicted.sceneLeases().get(hot.lease().id()).status(),
                "the former HOT worker has no second physical interpretation after admission");
        assertTrue(ResourceSiteHarvestProcess.planColdProgress(conflicted, ResourceSiteHarvestProcess.coldProgress(job, 22_303L)).isEmpty(),
                "a retained pre-conflict COLD action is consumed rather than resurrecting the conflicted harvest");
        SceneLeaseReleased released = new SceneLeaseReleased(hot.lease().id(), List.of(new SceneMemberPosition(job.workerId(),
                hot.lease().memberPosition(job.workerId()), hot.state().actorLocations().get(job.workerId()).condition().health())));
        assertTrue(FrontierSceneBehaviors.releasePlan(conflicted, conflicted.sceneLeases().get(hot.lease().id()), 22_303L, released)
                        .continuation() instanceof SceneContinuation.None,
                "releasing the drained worker cannot reschedule a harvest after the accepted player conflict");
    }

    @Test
    void carrierFenceAmbiguityRetainsItsOwnRecoveryIncidentRatherThanAPlayerOrTerminalRepairFact() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob job = hot.job();
        BlockPosition crop = FrontierResourceSitePlan.compile(hot.state().bootstrap()).get(hot.site()).cropSlots().getFirst();
        ResourceSiteConflictObserved observed = new ResourceSiteConflictObserved(hot.site(), crop,
                ResourceSiteConflictReason.CARRIER_FENCE_UNRESOLVED, ResourceSiteConflictSource.SCENE_CARRIER_FENCE);

        FrontierWorldState conflicted = ResourceSiteProcess.reduceConflict(hot.state(), hot.site(), observed);

        ResourceSiteConflictDisposition disposition = conflicted.resourceSites().site(hot.site()).conflictDisposition().orElseThrow();
        assertEquals(ResourceSiteConflictReason.CARRIER_FENCE_UNRESOLVED, disposition.reason());
        assertEquals(ResourceSiteConflictPolicy.RECOVERY_INSPECTION_REQUIRED, disposition.policy());
        assertEquals(ConflictIncidentCategory.LAWFUL_LIFECYCLE_LAG, disposition.incident().category());
        assertEquals(ResourceSiteConflictSource.SCENE_CARRIER_FENCE.name(), disposition.incident().source());
        assertEquals(PhysicalIntentStatus.CONFLICTED, conflicted.physicalIntents().get(job.intentId()).status(),
                "the isolated ambiguous field cannot keep an executable harvest intent");
        assertEquals(disposition, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(conflicted))
                .resourceSites().site(hot.site()).conflictDisposition().orElseThrow(), "the typed first incident must survive snapshot recovery");
    }

    @Test
    void playerBreakLeavesAnUnbegunTraversalIntentPrepared() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob job = hot.job();
        BlockPosition crop = FrontierResourceSitePlan.compile(hot.state().bootstrap()).get(hot.site()).cropSlots().getFirst();
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents =
                new java.util.LinkedHashMap<>(hot.state().physicalIntents());
        intents.put(job.intentId(), intents.get(job.intentId()).withStatus(PhysicalIntentStatus.PREPARED, Optional.empty()));
        FrontierWorldState prepared = hot.state().withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents));

        FrontierWorldState conflicted = ResourceSiteProcess.reduceConflict(prepared, hot.site(),
                new ResourceSiteConflictObserved(hot.site(), crop, ResourceSiteConflictReason.PLAYER_REMOVED_MANAGED_CELL));

        assertEquals(PhysicalIntentStatus.CONFLICTED, conflicted.physicalIntents().get(job.intentId()).status(),
                "the traversal-only profile records terminal player disposition without restart custody");
        assertEquals(ResourceSitePhase.CONFLICT, conflicted.resourceSites().site(hot.site()).phase());
    }

    @Test
    void inertPreparedAmbientLeaseCanCloseForAnExclusiveSuccessorWithoutInventingMovement() {
        FrontierWorldState state = initial(); SubjectId worker = new SubjectId("resident:1-1");
        AmbientActorLease prepared = AmbientActorProcess.nextLease(state, worker, new SimInstant(100L));
        state = AmbientLeaseStateProcess.prepare(state, prepared);

        FrontierWorldState draining = AmbientLeaseStateProcess.transition(state, worker, AmbientLeaseStatus.DRAINING);
        FrontierWorldState closed = AmbientLeaseStateProcess.release(draining,
                new AmbientLeaseReleased(worker, prepared.handoffBody(), state.actorLocations().get(worker).condition().health()));

        assertEquals(AmbientLeaseStatus.CLOSED, closed.ambientLeases().get(worker).status());
        assertEquals(prepared.handoffBody(), closed.actorLocations().get(worker).body(),
                "a pre-HOT cancellation retains the sole canonical body rather than creating a successor position");
    }

    @Test
    void fullDepotLeavesReadyFieldWithoutInventingAHarvest() {
        FrontierWorldState state = fullDepot(ready(initial())); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(state, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));

        assertEquals(1, planned.size()); assertEquals(new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED), planned.getFirst().payload());
        assertEquals(ResourceSitePhase.READY, state.resourceSites().site(site).phase());
    }

    @Test
    void physicalHarvestReceiptCannotRedirectTheNamedOutputToAnotherDepotSlot() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"), (StrategicTaskTransition) planned.getFirst().payload());
        FrontierWorldState harvesting = ResourceSiteHarvestProcess.reduceStarted(active, site, started);
        PhysicalIntentPrepared prepared = (PhysicalIntentPrepared) planned.get(2).payload();
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, prepared.intent());
        harvesting = harvesting.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        InventoryCustody.ContainerSlot otherSlot = new InventoryCustody.ContainerSlot(started.job().outputSlot().containerId(),
                started.job().outputSlot().slot() + 1);
        ExactItemStack redirected = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, otherSlot);

        ResourceSiteHarvestObservation redirectedReceipt = receipt(prepared.intent(), started.job(), redirected);
        FrontierWorldState state = harvesting;
        assertThrows(IllegalArgumentException.class, () -> state.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(redirectedReceipt)));
    }

    @Test
    void duplicatePhysicalHarvestReceiptIsRejectedWithoutChangingTheField() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        PhysicalIntentPrepared prepared = (PhysicalIntentPrepared) planned.get(2).payload();
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, prepared.intent());
        harvesting = harvesting.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ExactItemStack output = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, started.job().outputSlot());
        FrontierWorldState withDuplicate = harvesting.withInventory(harvesting.inventory().store(output));
        ResourceSiteHarvestObservation receipt = receipt(prepared.intent(), started.job(), output);
        assertThrows(IllegalArgumentException.class, () -> withDuplicate.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
    }

    @Test
    void securedProvisionReschedulesOnlyItsRetainedPendingReadyFieldTask() {
        SubjectId settlement = new SubjectId("settlement:1"); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = harvestTask(ready(initial()), site, 22_000L);
        StrategicTask task = onlyHarvestTask(state);
        SettlementProvision provision = new SettlementProvision(settlement, 4, 72_600L, 0, 0, List.of(), List.of(), 0,
                SettlementProvisionStatus.SECURE, Optional.empty());
        state = state.withHumanPopulation(state.humanPopulation().withProvision(provision));

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = StrategicObjectiveProcess.planProvisionReconsideration(state,
                StrategicObjectiveProcess.provisionReconsideration(provision, 72_601L));

        assertEquals(1, planned.size());
        ScheduleEffect.Rescheduled rescheduled = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getFirst().payload());
        assertEquals(ResourceSiteHarvestProcess.start(task, 72_602L).id(), rescheduled.scheduleId());
        assertEquals(ResourceSiteHarvestProcess.start(task, 72_602L), rescheduled.replacement());
        assertEquals(task, state.strategicPlans().tasks().get(task.id()), "the wake resumes the retained task without selecting a replacement");
    }

    @Test
    void securedProvisionReplacesTheRetainedStartInTheCanonicalQueueWithoutQuarantine() {
        FrontierWorldState state = harvestTask(ready(initial()), new SubjectId("site:1-wheat-field"), 22_000L);
        StrategicTask task = onlyHarvestTask(state); SubjectId settlement = new SubjectId("settlement:1");
        SettlementProvision provision = new SettlementProvision(settlement, 4, 72_600L, 0, 0, List.of(), List.of(), 0,
                SettlementProvisionStatus.SECURE, Optional.empty());
        state = state.withHumanPopulation(state.humanPopulation().withProvision(provision));
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 125L);
        var configuration = new FrontierEngineConfiguration<>(state.bootstrap().worldId(), state, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(
                StrategicObjectiveProcess.provisionReconsideration(provision, 72_601L), ResourceSiteHarvestProcess.start(task, 73_000L)),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var engine = FrontierEngines.create(configuration);

        var result = engine.advanceTo(new SimInstant(72_601L), new WorkBudget(8, 64));

        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse(""));
        assertEquals(List.of(ResourceSiteHarvestProcess.start(task, 72_602L)), engine.checkpoint().schedules(),
                "the secure-ration wake must advance the retained task action instead of creating a duplicate identity");
    }

    static FrontierWorldState ready(FrontierWorldState state) {
        SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        state = ResourceSiteProcess.reducePrepared(state, site, (ResourceSitePrepared) preparation.get(1).payload());
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle current = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site, new ResourceSiteGrowthAdvanced(site, current.growthEpoch(), current.growthStage()));
        }
        return state;
    }

    static FrontierWorldState fullDepot(FrontierWorldState state) {
        SubjectId settlement = new SubjectId("settlement:1"); SubjectId depot = FrontierWorldState.depotId(settlement);
        for (int ordinal = 0; state.inventory().firstFreeSlot(depot).isPresent(); ordinal++) {
            int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
            state = state.withInventory(state.inventory().store(new ExactItemStack(new SubjectId("item:full-depot-" + ordinal), settlement,
                    "minecraft:cobblestone", 1, new InventoryCustody.ContainerSlot(depot, slot))));
        }
        return state;
    }

    static FrontierWorldState harvestTask(FrontierWorldState state, SubjectId site, long dueAt) {
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), dueAt));
        assertEquals(3, planned.size());
        assertEquals(planned.getFirst().payload(), FrontierWorldRuntimeDefinition.payloadCodecs().decode(planned.getFirst().payload().type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(planned.getFirst().payload())));
        assertEquals(planned.get(1).payload(), FrontierWorldRuntimeDefinition.payloadCodecs().decode(planned.get(1).payload().type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(planned.get(1).payload())));
        FrontierWorldState selected = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) planned.getFirst().payload());
        return StrategicObjectiveProcess.reduceTask(selected, new SubjectId("settlement:1"), (StrategicTaskPlanned) planned.get(1).payload());
    }

    static StrategicTask onlyHarvestTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
    }

    static ResourceSiteHarvestObservation receipt(PhysicalIntent intent, ResourceSiteHarvestJob job, ExactItemStack output) {
        return new ResourceSiteHarvestObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), job.siteId(), job.workerId(), output, 64);
    }

    static FrontierWorldState completeHarvestWork(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job) {
        FrontierWorldState current = state;
        for (int index = 0; index < ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS; index++) {
            current = advanceToCurrentCropStation(current, site, job.id());
            current = ResourceSiteHarvestProcess.reduceCropPrepared(current, site, new ResourceSiteHarvestCropPrepared(job.id(), index));
            current = ResourceSiteHarvestProcess.reduceProgressed(current, site, new ResourceSiteHarvestProgressed(job.id(), index + 1));
        }
        return current;
    }

    /**
     * Test-only completion path for an already admitted scene.  It deliberately emits the same
     * exact observed HOT checkpoint that a loaded executor must submit instead of smuggling
     * scene-owned progress through the COLD reducer.
     */
    static FrontierWorldState completeHarvestWorkHot(FrontierWorldState state, SubjectId site,
                                                             ResourceSiteHarvestJob job, SceneLeaseId leaseId) {
        FrontierWorldState current = state;
        for (int index = 0; index < ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS; index++) {
            current = advanceToCurrentCropStationHot(current, site, job.id(), leaseId);
            current = ResourceSiteHarvestProcess.reduceCropPrepared(current, site, new ResourceSiteHarvestCropPrepared(job.id(), index));
            current = ResourceSiteHarvestProcess.reduceProgressed(current, site, new ResourceSiteHarvestProgressed(job.id(), index + 1));
        }
        return current;
    }

    static FrontierWorldState advanceToCurrentCropStation(FrontierWorldState state, SubjectId site, SubjectId jobId) {
        FrontierWorldState current = state;
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) current.resourceSites().site(site).activeWork().orElseThrow();
        while (job.hasNextTraversalStep()) {
            current = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(current, site,
                    new ResourceSiteHarvestColdTraversalAdvanced(jobId, job.workerId(), job.traversalCursor() + 1));
            job = (ResourceSiteHarvestJob) current.resourceSites().site(site).activeWork().orElseThrow();
        }
        return current;
    }

    static FrontierWorldState advanceToCurrentCropStationHot(FrontierWorldState state, SubjectId site,
                                                                       SubjectId jobId, SceneLeaseId leaseId) {
        FrontierWorldState current = state;
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) current.resourceSites().site(site).activeWork().orElseThrow();
        while (job.hasNextTraversalStep()) {
            BodyPosition observed = job.nextTraversalSurface().standingBody();
            current = ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(current, site,
                    new ResourceSiteHarvestHotTraversalAdvanced(jobId, leaseId, job.workerId(), observed,
                            job.traversalCursor() + 1));
            job = (ResourceSiteHarvestJob) current.resourceSites().site(site).activeWork().orElseThrow();
        }
        return current;
    }

    static HotHarvest hotHarvestAfterColdSteps(int coldSteps) {
        return hotHarvestAfterColdSteps(125L, coldSteps);
    }

    static HotHarvest hotHarvestAfterColdSteps(long seed, int coldSteps) {
        ColdHarvest cold = coldHarvestAfterSteps(seed, coldSteps);
        FrontierWorldState state = cold.state();
        SubjectId site = cold.site();
        ResourceSiteHarvestJob job = cold.job();
        SceneLease lease = newHarvestLease(state, site, job, "hot-checkpoint-" + seed + "-" + coldSteps);
        state = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        return new HotHarvest(state, site, job, state.sceneLeases().get(lease.id()));
    }

    static ColdHarvest coldHarvestAfterSteps(long seed, int coldSteps) {
        FrontierWorldState ready = ready(initial(seed));
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L);
        StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, started);
        state = ResourceSiteHarvestProcess.reducePrepared(state, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        for (int index = 0; index < coldSteps; index++) {
            ResourceSiteHarvestJob current = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
            state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site,
                    new ResourceSiteHarvestColdTraversalAdvanced(current.id(), current.workerId(), current.traversalCursor() + 1));
        }
        return new ColdHarvest(state, site, (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow());
    }

    static SceneLease newHarvestLease(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job, String suffix) {
        BodyPosition current = job.traversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
        BlockPosition crop = FrontierResourceSitePlan.compile(state.bootstrap()).get(site).cropSlots().get(job.progress().nextCropSlotIndex());
        return SceneLease.forCause(new SceneLeaseId("lease:site-harvest-" + suffix), state.bootstrap().worldId(),
                new ResourceSiteHarvestSceneCause(job.id()), crop, new SimInstant(22_300L), 0L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), job.workerId()))),
                java.util.Map.of(job.workerId(), current), java.util.Set.of(job.workerId()), Optional.empty());
    }

    static CommandPlan hotCheckpointPlan(FrontierWorldState state, String suffix,
                                                  ResourceSiteHarvestHotTraversalAdvanced checkpoint) {
        CommandId commandId = new CommandId("command:site-harvest-hot-" + suffix);
        ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state,
                new ResourceSiteHarvestSceneCause(checkpoint.jobId()));
        ScheduledAction action = ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
        FrontierCommand command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, commandId, state.bootstrap().worldId(), new Revision(1L),
                new SimInstant(22_301L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), checkpoint,
                Optional.of(new EngineScheduleBinding(new Revision(1L), action)));
        return FrontierWorldProcessCatalog.planCommand("resource-sites", state, command);
    }

    static void assertRejectedHotCheckpoint(FrontierWorldState state, String suffix,
                                                    ResourceSiteHarvestHotTraversalAdvanced checkpoint) {
        assertTrue(hotCheckpointPlan(state, suffix, checkpoint) instanceof CommandPlan.Rejected,
                "invalid HOT checkpoint must be rejected before event persistence: " + suffix);
    }

    record HotHarvest(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job, SceneLease lease) { }

    record ColdHarvest(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job) { }

    static final class CalibrationFacts {
        private final LinkedHashSet<String> actors = new LinkedHashSet<>(), objects = new LinkedHashSet<>(), stages = new LinkedHashSet<>();
        private final LinkedHashSet<String> topology = new LinkedHashSet<>(), opportunities = new LinkedHashSet<>();
        private final LinkedHashMap<String, Long> claims = new LinkedHashMap<>(), custody = new LinkedHashMap<>(), work = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> recovery = new LinkedHashMap<>();

        void append(FrontierWorldState state, ResourceSiteHarvestJob job, SubjectId site, long seed) {
            String prefix = "seed:" + seed + ":";
            actors.add(prefix + job.workerId().value());
            objects.add(prefix + job.id().value());
            claims.put(prefix + "worker", 1L);
            custody.put(prefix + "crop-slots", (long) job.progress().completedCropSlots());
            custody.put(prefix + "body-x", 4_096L + state.actorLocations().get(job.workerId()).body().x());
            custody.put(prefix + "body-y", 4_096L + state.actorLocations().get(job.workerId()).body().y());
            custody.put(prefix + "body-z", 4_096L + state.actorLocations().get(job.workerId()).body().z());
            stages.add(prefix + state.resourceSites().site(site).phase());
            work.put(prefix + "cursor", (long) job.traversalCursor());
            topology.add(prefix + job.traversal().linearCorridorSurfaces());
            recovery.put(prefix + "intent", state.physicalIntents().get(job.intentId()).status().name());
            recovery.put(prefix + "active-lease", "none");
            opportunities.add(prefix + job.id().value());
        }

        FrontierObserverNeutralityContract.Run run(FrontierObserverNeutralityContract.Declaration declaration) {
            int samples = actors.size();
            return new FrontierObserverNeutralityContract.Run(declaration, actors, objects, claims, custody, stages, work, topology,
                    java.util.Set.of(), recovery, opportunities,
                    new FrontierObserverNeutralityContract.CalibrationSample(samples, samples, 0, samples * 400L));
        }
    }

    static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-harvest"), 125L));
    }

    static FrontierWorldState initial(long seed) {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-harvest-" + seed), seed));
    }

    static long column(int x, int z) {
        return (Integer.toUnsignedLong(x) << 32) | Integer.toUnsignedLong(z);
    }
}
