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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteHarvestProcessTest {
    @Test
    void readyFieldUsesItsBoundedFacilityLaneWithoutCancellingContainmentWork() {
        FrontierWorldState state = ready(initial()); SubjectId settlement = new SubjectId("settlement:1");
        StrategicObjective strategic = new StrategicObjective(new SubjectId("objective:1-export"), settlement,
                StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(new InfectionCell(0, 0)), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask strategicTask = new StrategicTask(new SubjectId("task:1-export"), strategic.id(), settlement,
                StrategicTaskKind.DECONTAMINATE_INFECTION_CELL, Optional.of(new InfectionCell(0, 0)),
                List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(strategic).addTask(strategicTask));
        SubjectId site = new SubjectId("site:1-wheat-field");

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 22_000L));

        assertEquals(3, planned.size(), "an active strategic objective must not starve an independent farm");
        StrategicObjectiveSelected selected = (StrategicObjectiveSelected) planned.getFirst().payload();
        assertEquals(StrategicObjectiveLane.FACILITY, selected.objective().lane());
        FrontierWorldState withFacility = StrategicObjectiveProcess.reduceObjective(state, settlement, selected);
        withFacility = StrategicObjectiveProcess.reduceTask(withFacility, settlement, (StrategicTaskPlanned) planned.get(1).payload());
        assertEquals(2, withFacility.strategicPlans().objectives().values().stream()
                .filter(objective -> objective.status() == StrategicObjectiveStatus.ACTIVE).count());
        assertEquals(StrategicTaskStatus.PENDING, withFacility.strategicPlans().tasks().get(strategicTask.id()).status(),
                "facility work must neither cancel nor preempt the strategic task");
    }

    @Test
    void exactMatureFieldReservesOneNamedWheatStackUntilItsPhysicalReceipt() {
        FrontierWorldState ready = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(ready, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));

        assertEquals(3, planned.size()); assertEquals(new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE), planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        assertEquals(task.id(), started.job().taskId()); assertFalse(tasked.inventory().items().containsKey(started.job().outputItemId()));
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"), (StrategicTaskTransition) planned.getFirst().payload());
        FrontierWorldState harvesting = ResourceSiteHarvestProcess.reduceStarted(active, site, started);
        ExactItemStack output = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, started.job().outputSlot());
        PhysicalIntentPrepared prepared = (PhysicalIntentPrepared) planned.get(2).payload();
        assertEquals(started.job().intentId(), prepared.intent().id());
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, prepared.intent());
        assertEquals(ResourceSitePhase.HARVESTING, harvesting.resourceSites().site(site).phase());
        assertEquals(PhysicalIntentStatus.PREPARED, harvesting.physicalIntents().get(prepared.intent().id()).status());
        assertFalse(harvesting.inventory().items().containsKey(output.id()), "canonical inventory must wait for Minecraft receipt");
        assertEquals(StrategicTaskStatus.ACTIVE, harvesting.strategicPlans().tasks().get(task.id()).status());
        assertFalse(FrontierSceneAdmission.reservedActors(harvesting).contains(started.job().workerId()),
                "a PREPARED harvest has no observed loaded-world field baseline yet");
        harvesting = harvesting.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        assertFalse(FrontierSceneAdmission.reservedActors(harvesting).contains(started.job().workerId()),
                "a live farmer remains available for the typed ambient-to-scene hand-off until its scene lease exists");
        assertTrue(FrontierSceneAdmission.reservedFromGenericAmbient(harvesting, started.job().workerId()),
                "an unrelated ambient admission may not reopen the worker between the field job and scene hand-off");

        harvesting = completeHarvestWork(harvesting, site, started.job());
        ResourceSiteHarvestObservation receipt = receipt(prepared.intent(), started.job(), output);
        PhysicalIntentTransition confirmed = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> completion = ResourceSiteHarvestProcess.planTransition(harvesting, prepared.intent(), confirmed, 22_200L);
        assertEquals(3, completion.size());
        FrontierWorldState complete = harvesting.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        complete = StrategicObjectiveProcess.reduceTaskTransition(complete, new SubjectId("settlement:1"), (StrategicTaskTransition) completion.get(1).payload());
        assertEquals(ResourceSitePhase.GROWING, complete.resourceSites().site(site).phase());
        assertEquals(output, complete.inventory().items().get(output.id()));
        assertEquals(PhysicalIntentStatus.CONFIRMED, complete.physicalIntents().get(prepared.intent().id()).status());
        assertEquals(receipt, complete.physicalObservations().get(receipt.id()));
        assertEquals(StrategicTaskStatus.COMPLETED, complete.strategicPlans().tasks().get(task.id()).status());
        assertTrue(new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(complete)).inventory().items().containsKey(output.id()));
    }

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

        FrontierWorldState advanced = ResourceSiteHarvestProcess.reduceTraversalAdvanced(rebased, site,
                new ResourceSiteHarvestTraversalAdvanced(job.id(), 1));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.rebaseForAmbientHandoff(advanced, new SubjectId("settlement:1"), handoff),
                "a later hand-off may not erase retained worker progress");
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
        SceneLeaseReleased released = new SceneLeaseReleased(lease.id(), List.of(new SceneMemberPosition(job.workerId(), workerBody,
                draining.actorLocations().get(job.workerId()).condition().health())));

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuation = FrontierSceneContinuationPlanner.releaseEvents(draining, lease, 22_220L, released);

        assertEquals(List.of(new SubjectId("settlement:1")), continuation.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::subject).toList());
        assertEquals(released, continuation.getFirst().payload());
        assertEquals(SceneLeaseStatus.CLOSED, draining.releaseSceneLease(lease.id(), released.members()).sceneLeases().get(lease.id()).status());
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
        FrontierWorldState completeWork = completeHarvestWork(hot, site, atField);
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

    private static FrontierWorldState ready(FrontierWorldState state) {
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

    private static FrontierWorldState fullDepot(FrontierWorldState state) {
        SubjectId settlement = new SubjectId("settlement:1"); SubjectId depot = FrontierWorldState.depotId(settlement);
        for (int ordinal = 0; state.inventory().firstFreeSlot(depot).isPresent(); ordinal++) {
            int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
            state = state.withInventory(state.inventory().store(new ExactItemStack(new SubjectId("item:full-depot-" + ordinal), settlement,
                    "minecraft:cobblestone", 1, new InventoryCustody.ContainerSlot(depot, slot))));
        }
        return state;
    }

    private static FrontierWorldState harvestTask(FrontierWorldState state, SubjectId site, long dueAt) {
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

    private static StrategicTask onlyHarvestTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
    }

    private static ResourceSiteHarvestObservation receipt(PhysicalIntent intent, ResourceSiteHarvestJob job, ExactItemStack output) {
        return new ResourceSiteHarvestObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), job.siteId(), job.workerId(), output, 64);
    }

    private static FrontierWorldState completeHarvestWork(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job) {
        FrontierWorldState current = state;
        for (int index = 0; index < ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS; index++) {
            current = advanceToCurrentCropStation(current, site, job.id());
            current = ResourceSiteHarvestProcess.reduceCropPrepared(current, site, new ResourceSiteHarvestCropPrepared(job.id(), index));
            current = ResourceSiteHarvestProcess.reduceProgressed(current, site, new ResourceSiteHarvestProgressed(job.id(), index + 1));
        }
        return current;
    }

    private static FrontierWorldState advanceToCurrentCropStation(FrontierWorldState state, SubjectId site, SubjectId jobId) {
        FrontierWorldState current = state;
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) current.resourceSites().site(site).activeWork().orElseThrow();
        while (job.hasNextTraversalStep()) {
            current = ResourceSiteHarvestProcess.reduceTraversalAdvanced(current, site,
                    new ResourceSiteHarvestTraversalAdvanced(jobId, job.traversalCursor() + 1));
            job = (ResourceSiteHarvestJob) current.resourceSites().site(site).activeWork().orElseThrow();
        }
        return current;
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-harvest"), 125L));
    }
}
