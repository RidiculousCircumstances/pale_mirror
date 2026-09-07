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
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteHarvestProcessTest {
    @Test
    void f0vReferenceRejectsIrreversibleCropAdmissionUntilF0v2OwnsTheCounterpart() {
        assertFalse(ResourceSiteHarvestProcess.irreversibleCropEffectsAdmitted(),
                "the F0.V/F0.1 reference must not let HOT observation complete a crop before COLD has an owned counterpart");
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        CommandId commandId = new CommandId("command:site-harvest-f0v2-gate");
        FrontierCommand command = new FrontierCommand(1, commandId, hot.state().bootstrap().worldId(), new Revision(1L),
                new SimInstant(22_302L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new ResourceSiteHarvestProgressed(hot.job().id(), 1));
        assertTrue(FrontierWorldProcessCatalog.planCommand("resource-sites", hot.state(), command) instanceof CommandPlan.Rejected,
                "a forged HOT crop observation must fail before event/WAL admission while the F0.2 counterpart is unavailable");
    }

    @Test
    void fieldApproachRetainsFarmlandSupportRatherThanThePassThroughCropLayer() {
        FrontierWorldState state = initial();
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(new SubjectId("site:1-wheat-field"));
        SubjectId workerId = new SubjectId("resident:1-1");
        TraversalTopology traversal = ResourceSiteHarvestTraversal.compile(state.bootstrap(), site,
                state.actorLocations().get(workerId), new SubjectId("job:field-support-contract"));

        java.util.Map<Long, BlockPosition> farmlandByColumn = new java.util.HashMap<>();
        for (BlockPosition crop : site.cropSlots()) {
            farmlandByColumn.put(column(crop.x(), crop.z()), crop.offset(0, -1, 0));
        }
        assertTrue(traversal.linearCorridorSurfaces().stream()
                        .filter(surface -> farmlandByColumn.containsKey(column(surface.x(), surface.z())))
                        .allMatch(surface -> surface.support().equals(farmlandByColumn.get(column(surface.x(), surface.z())))),
                "every retained field-column cursor stands on its exact farmland support, never the non-supporting crop layer");
        assertTrue(traversal.linearCorridorSurfaces().stream().skip(1)
                        .filter(surface -> !farmlandByColumn.containsKey(column(surface.x(), surface.z())))
                        .allMatch(surface -> surface.y() == state.bootstrap().terrain().supportYAt(surface.x(), surface.z())),
                "the field approach retains real terrain support outside crop columns rather than a virtual air layer");
        assertEquals(site.cropSlots().stream().map(crop -> new SurfaceAnchor(crop.offset(0, -1, 0))).toList(),
                traversal.linearCorridorSurfaces().subList(traversal.linearCorridorSurfaces().size() - site.cropSlots().size(),
                        traversal.linearCorridorSurfaces().size()),
                "the immutable work order keeps all 64 crop stations at their physical farmland supports");
    }

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

        assertEquals(4, planned.size()); assertEquals(new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE), planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        assertEquals(task.id(), started.job().taskId()); assertFalse(tasked.inventory().items().containsKey(started.job().outputItemId()));
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"), (StrategicTaskTransition) planned.getFirst().payload());
        FrontierWorldState harvesting = ResourceSiteHarvestProcess.reduceStarted(active, site, started);
        ExactItemStack output = new ExactItemStack(started.job().outputItemId(), new SubjectId("settlement:1"), "minecraft:wheat", 64, started.job().outputSlot());
        PhysicalIntentPrepared prepared = (PhysicalIntentPrepared) planned.get(2).payload();
        assertEquals(started.job().intentId(), prepared.intent().id());
        assertEquals(ResourceSiteHarvestProcess.coldProgress(started.job(), 22_100L
                        + tasked.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()),
                ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created) planned.get(3).payload()).action());
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
        assertTrue(FrontierSceneAdmission.permitsPreLeaseAmbientHandoff(harvesting, started.job().workerId()),
                "the exact ready field worker may obtain only the inert precursor body required for the typed hand-off");
        FrontierSceneAdmission.GenericAmbientAdmission genericAdmission = FrontierSceneAdmission.genericAmbientAdmission(harvesting);
        assertTrue(genericAdmission.reserves(started.job().workerId()),
                "one immutable generic-ambient decision retains the exact field worker");
        assertEquals(Optional.of(SceneCauseKind.RESOURCE_SITE_HARVEST), genericAdmission.preLeaseSceneCause(started.job().workerId()),
                "the same decision names the only registered physical provider allowed to receive the inert precursor");
        assertTrue(genericAdmission.preLeaseSceneCause(new SubjectId("resident:1-2")).isEmpty(),
                "an unrelated resident cannot borrow the crop-foot exception from the named farmer");

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
    void coldHarvestDriverAdvancesTheSameFarmerCursorButNeverRemovesAnUnloadedCrop() {
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

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> step = ResourceSiteHarvestProcess.planColdProgress(harvesting, cold);

        assertEquals(2, step.size());
        ResourceSiteHarvestColdTraversalAdvanced advanced = (ResourceSiteHarvestColdTraversalAdvanced) step.getFirst().payload();
        assertEquals(started.job().id(), advanced.jobId());
        assertEquals(started.job().workerId(), advanced.workerId());
        assertTrue(step.get(1).payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled);
        FrontierWorldState after = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(harvesting, site, advanced);
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) after.resourceSites().site(site).activeWork().orElseThrow();
        assertEquals(1, job.traversalCursor());
        assertEquals(job.traversal().linearCorridorSurfaces().get(1).standingBody(), after.actorLocations().get(job.workerId()).body());
        assertEquals(0, job.progress().completedCropSlots());
        assertEquals(PhysicalIntentStatus.PREPARED, after.physicalIntents().get(job.intentId()).status(),
                "COLD approach must not claim an unobserved crop effect");
    }

    @Test
    void coldHarvestDriverDefersAndItsReducerRejectsWhileExactAmbientHandoffAuthorityExists() {
        FrontierWorldState tasked = harvestTask(ready(initial()), new SubjectId("site:1-wheat-field"), 22_000L);
        StrategicTask task = onlyHarvestTask(tasked);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(tasked,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        SubjectId site = started.job().siteId();
        FrontierWorldState harvesting = StrategicObjectiveProcess.reduceTaskTransition(tasked, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        harvesting = ResourceSiteHarvestProcess.reduceStarted(harvesting, site, started);
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent())
                .transitionPhysicalIntent(started.job().intentId(), PhysicalIntentStatus.RUNNING, Optional.empty());
        AmbientActorLease precursor = AmbientActorProcess.nextLease(harvesting, started.job().workerId(), new SimInstant(22_101L));
        harvesting = AmbientLeaseStateProcess.prepare(harvesting, precursor);
        io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction cold =
                ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created) planned.get(3).payload()).action();

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> deferred = ResourceSiteHarvestProcess.planColdProgress(harvesting, cold);

        assertEquals(1, deferred.size(), "the same durable COLD schedule remains, but it cannot race an ambient-to-HOT hand-off");
        assertTrue(deferred.getFirst().payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled);
        ResourceSiteHarvestColdTraversalAdvanced forged = new ResourceSiteHarvestColdTraversalAdvanced(started.job().id(), started.job().workerId(), 1);
        FrontierWorldState stable = harvesting;
        assertThrows(IllegalArgumentException.class,
                () -> ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(stable, site, forged),
                "event replay must retain the same authority fence even if a stale driver emits an advance");
    }

    @Test
    void hotLeaseRetainsItsExactEngineContinuationUntilObservedCheckpointOrRelease() {
        HotHarvest hot = hotHarvestAfterColdSteps(1);
        ScheduledAction retained = ResourceSiteHarvestProcess.coldProgress(hot.job(), 22_301L);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> deferred =
                ResourceSiteHarvestProcess.planColdProgress(hot.state(), retained);

        assertEquals(1, deferred.size(), "a HOT owner may fence COLD but must not create a second deadline");
        ScheduleEffect.Rescheduled rescheduled = assertInstanceOf(ScheduleEffect.Rescheduled.class, deferred.getFirst().payload());
        assertEquals(retained.id(), rescheduled.scheduleId());
        assertEquals(retained, rescheduled.replacement(),
                "HOT arrival must bind the current due action; only its observed semantic checkpoint may apply cadence");
    }

    @Test
    void hotCheckpointAtomicallyPersistsTheExactJobLeaseAndFarmerBodyThroughSnapshotAndWalReplay() {
        HotHarvest hot = hotHarvestAfterColdSteps(2);
        ResourceSiteHarvestJob before = hot.job();
        BodyPosition observed = before.nextTraversalSurface().standingBody();
        ResourceSiteHarvestHotTraversalAdvanced checkpoint = new ResourceSiteHarvestHotTraversalAdvanced(before.id(), hot.lease().id(),
                before.workerId(), observed, before.traversalCursor() + 1);

        assertEquals(checkpoint, FrontierWorldRuntimeDefinition.payloadCodecs().decode(checkpoint.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(checkpoint)), "the WAL checkpoint has one stable typed codec");
        CommandPlan.Accepted accepted = assertInstanceOf(CommandPlan.Accepted.class, hotCheckpointPlan(hot.state(), "accepted", checkpoint),
                "command admission validates the complete exact HOT causal checkpoint before it persists an event");
        ScheduleEffect.Rescheduled continued = assertInstanceOf(ScheduleEffect.Rescheduled.class, accepted.events().get(1).payload());
        assertEquals(ResourceSiteHarvestProcess.coldProgress(before, 22_301L).id(), continued.scheduleId());
        assertEquals(22_301L + hot.state().bootstrap().ruleset().cadence().resourceHarvestRetryInterval(),
                continued.replacement().dueAt().ticks(), "HOT uses the same bound COLD cadence, never observation time");

        FrontierWorldState snapshot = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(hot.state()));
        FrontierEvent event = new FrontierEvent(1, new EventId("event:site-harvest-hot-checkpoint"),
                new TransactionId("transaction:site-harvest-hot-checkpoint"), snapshot.bootstrap().worldId(), new Revision(1L),
                new SimInstant(22_301L), hot.site(), CauseChain.root(new CommandId("command:site-harvest-hot-checkpoint")), checkpoint);
        FrontierWorldState replayed = FrontierWorldProcessCatalog.reduce("resource-sites", snapshot, event);
        ResourceSiteHarvestJob after = (ResourceSiteHarvestJob) replayed.resourceSites().site(hot.site()).activeWork().orElseThrow();
        SceneLease recoveredLease = replayed.sceneLeases().get(hot.lease().id());

        assertEquals(before.traversalCursor() + 1, after.traversalCursor());
        assertEquals(observed, replayed.actorLocations().get(after.workerId()).body(), "actor location is the same checkpoint");
        assertEquals(observed, recoveredLease.memberPosition(after.workerId()), "lease recovery is the same checkpoint");
        assertEquals(List.of(after.workerId()), recoveredLease.members().stream().map(SceneMember::actorId).toList());
        FrontierWorldState recoveredSnapshot = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(replayed));
        ResourceSiteHarvestJob recoveredJob = (ResourceSiteHarvestJob) recoveredSnapshot.resourceSites().site(hot.site()).activeWork().orElseThrow();
        assertEquals(after.traversalCursor(), recoveredJob.traversalCursor());
        assertEquals(recoveredSnapshot.actorLocations().get(after.workerId()).body(),
                recoveredSnapshot.sceneLeases().get(hot.lease().id()).memberPosition(after.workerId()),
                "partial HOT checkpoint recovery cannot retain a stale lease anchor");
    }

    @Test
    void hotCheckpointPlannerRejectsStaleForeignWrongBodyWrongLeaseAndSkippedCursorEvidence() {
        HotHarvest hot = hotHarvestAfterColdSteps(1);
        ResourceSiteHarvestJob job = hot.job();
        BodyPosition expected = job.nextTraversalSurface().standingBody();
        ResourceSiteHarvestHotTraversalAdvanced valid = new ResourceSiteHarvestHotTraversalAdvanced(job.id(), hot.lease().id(),
                job.workerId(), expected, job.traversalCursor() + 1);

        assertRejectedHotCheckpoint(hot.state(), "foreign-farmer", new ResourceSiteHarvestHotTraversalAdvanced(job.id(), hot.lease().id(),
                new SubjectId("resident:1-999"), expected, job.traversalCursor() + 1));
        assertRejectedHotCheckpoint(hot.state(), "wrong-body", new ResourceSiteHarvestHotTraversalAdvanced(job.id(), hot.lease().id(),
                job.workerId(), new BodyPosition(expected.x() + 1, expected.y(), expected.z()), job.traversalCursor() + 1));
        assertRejectedHotCheckpoint(hot.state(), "wrong-lease", new ResourceSiteHarvestHotTraversalAdvanced(job.id(),
                new SceneLeaseId("lease:site-harvest-foreign"), job.workerId(), expected, job.traversalCursor() + 1));
        assertRejectedHotCheckpoint(hot.state(), "skip-cursor", new ResourceSiteHarvestHotTraversalAdvanced(job.id(), hot.lease().id(),
                job.workerId(), expected, job.traversalCursor() + 2));

        FrontierWorldState advanced = ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(hot.state(), hot.site(), valid);
        assertRejectedHotCheckpoint(advanced, "stale", valid);
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(advanced, hot.site(), valid));
    }

    @Test
    void sameFarmerContinuesHotToColdToHotWithoutReplayOrCropOutputMutation() {
        HotHarvest firstHot = hotHarvestAfterColdSteps(1);
        ResourceSiteHarvestJob before = firstHot.job();
        BodyPosition firstObserved = before.nextTraversalSurface().standingBody();
        FrontierWorldState checkpointed = ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(firstHot.state(), firstHot.site(),
                new ResourceSiteHarvestHotTraversalAdvanced(before.id(), firstHot.lease().id(), before.workerId(), firstObserved,
                        before.traversalCursor() + 1));
        ResourceSiteHarvestJob afterHot = (ResourceSiteHarvestJob) checkpointed.resourceSites().site(firstHot.site()).activeWork().orElseThrow();
        FrontierWorldState draining = checkpointed.transitionSceneLease(firstHot.lease().id(), SceneLeaseStatus.DRAINING);
        SceneLeaseReleased released = new SceneLeaseReleased(firstHot.lease().id(), List.of(new SceneMemberPosition(afterHot.workerId(), firstObserved,
                draining.actorLocations().get(afterHot.workerId()).condition().health())));
        ScheduledAction retained = ResourceSiteHarvestProcess.coldProgress(afterHot, 22_350L);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuation = FrontierSceneContinuationPlanner.releaseEvents(draining,
                draining.sceneLeases().get(firstHot.lease().id()), 22_350L, released, Optional.of(retained));
        assertEquals(List.of(released), continuation.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).toList(),
                "release has no semantic step and must leave the exact engine action untouched");
        assertThrows(IllegalArgumentException.class, () -> FrontierSceneContinuationPlanner.releaseEvents(draining,
                draining.sceneLeases().get(firstHot.lease().id()), 22_350L, released, Optional.empty()),
                "missing engine binding must fail closed instead of recreating a release-time deadline");
        FrontierWorldState releasedState = draining.releaseSceneLease(firstHot.lease().id(), released.members());
        ResourceSiteHarvestJob coldJob = (ResourceSiteHarvestJob) releasedState.resourceSites().site(firstHot.site()).activeWork().orElseThrow();
        assertEquals(firstObserved, releasedState.actorLocations().get(coldJob.workerId()).body());
        assertEquals(firstObserved, releasedState.sceneLeases().get(firstHot.lease().id()).memberPosition(coldJob.workerId()));

        FrontierWorldState coldAdvanced = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(releasedState, firstHot.site(),
                new ResourceSiteHarvestColdTraversalAdvanced(coldJob.id(), coldJob.workerId(), coldJob.traversalCursor() + 1));
        ResourceSiteHarvestJob afterCold = (ResourceSiteHarvestJob) coldAdvanced.resourceSites().site(firstHot.site()).activeWork().orElseThrow();
        SceneLease secondLease = newHarvestLease(coldAdvanced, firstHot.site(), afterCold, "return");
        FrontierWorldState secondHot = coldAdvanced.prepareSceneLease(secondLease).transitionSceneLease(secondLease.id(), SceneLeaseStatus.HOT);
        BodyPosition secondObserved = afterCold.nextTraversalSurface().standingBody();
        FrontierWorldState returned = ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(secondHot, firstHot.site(),
                new ResourceSiteHarvestHotTraversalAdvanced(afterCold.id(), secondLease.id(), afterCold.workerId(), secondObserved,
                        afterCold.traversalCursor() + 1));
        ResourceSiteHarvestJob finalJob = (ResourceSiteHarvestJob) returned.resourceSites().site(firstHot.site()).activeWork().orElseThrow();

        assertEquals(before.workerId(), finalJob.workerId());
        assertEquals(SceneLease.deterministicEntityId(returned.bootstrap().worldId(), finalJob.workerId()),
                returned.sceneLeases().get(secondLease.id()).members().getFirst().entityId());
        assertEquals(secondObserved, returned.actorLocations().get(finalJob.workerId()).body());
        assertEquals(secondObserved, returned.sceneLeases().get(secondLease.id()).memberPosition(finalJob.workerId()));
        assertEquals(0, finalJob.progress().completedCropSlots(), "F0.1 COLD/HOT travel never removes unloaded crop blocks");
        assertFalse(returned.inventory().items().containsKey(finalJob.outputItemId()), "F0.1 travel never mints the field output");
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
        ResourceSiteConflictObserved observed = new ResourceSiteConflictObserved(hot.site(), crop, "player:contract");
        CommandId commandId = new CommandId("command:site-harvest-player-break");
        FrontierCommand command = new FrontierCommand(1, commandId, hot.state().bootstrap().worldId(), new Revision(1L), new SimInstant(22_302L),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), observed);

        assertTrue(FrontierWorldProcessCatalog.planCommand("resource-sites", hot.state(), command) instanceof CommandPlan.Accepted,
                "the trusted physical owner must durably admit the player observation before vanilla may mutate the cell");
        FrontierWorldState conflicted = ResourceSiteProcess.reduceConflict(hot.state(), hot.site(), observed);

        assertEquals(ResourceSitePhase.CONFLICT, conflicted.resourceSites().site(hot.site()).phase());
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, conflicted.physicalIntents().get(job.intentId()).status(),
                "the retired harvest cannot retain a nonterminal canonical-subject claim");
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

    /**
     * Test-only completion path for an already admitted scene.  It deliberately emits the same
     * exact observed HOT checkpoint that a loaded executor must submit instead of smuggling
     * scene-owned progress through the COLD reducer.
     */
    private static FrontierWorldState completeHarvestWorkHot(FrontierWorldState state, SubjectId site,
                                                             ResourceSiteHarvestJob job, SceneLeaseId leaseId) {
        FrontierWorldState current = state;
        for (int index = 0; index < ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS; index++) {
            current = advanceToCurrentCropStationHot(current, site, job.id(), leaseId);
            current = ResourceSiteHarvestProcess.reduceCropPrepared(current, site, new ResourceSiteHarvestCropPrepared(job.id(), index));
            current = ResourceSiteHarvestProcess.reduceProgressed(current, site, new ResourceSiteHarvestProgressed(job.id(), index + 1));
        }
        return current;
    }

    private static FrontierWorldState advanceToCurrentCropStation(FrontierWorldState state, SubjectId site, SubjectId jobId) {
        FrontierWorldState current = state;
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) current.resourceSites().site(site).activeWork().orElseThrow();
        while (job.hasNextTraversalStep()) {
            current = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(current, site,
                    new ResourceSiteHarvestColdTraversalAdvanced(jobId, job.workerId(), job.traversalCursor() + 1));
            job = (ResourceSiteHarvestJob) current.resourceSites().site(site).activeWork().orElseThrow();
        }
        return current;
    }

    private static FrontierWorldState advanceToCurrentCropStationHot(FrontierWorldState state, SubjectId site,
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

    private static HotHarvest hotHarvestAfterColdSteps(int coldSteps) {
        FrontierWorldState ready = ready(initial());
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
        state = state.transitionPhysicalIntent(started.job().intentId(), PhysicalIntentStatus.RUNNING, Optional.empty());
        for (int index = 0; index < coldSteps; index++) {
            ResourceSiteHarvestJob current = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
            state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site,
                    new ResourceSiteHarvestColdTraversalAdvanced(current.id(), current.workerId(), current.traversalCursor() + 1));
        }
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
        SceneLease lease = newHarvestLease(state, site, job, "hot-checkpoint-" + coldSteps);
        state = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        return new HotHarvest(state, site, job, state.sceneLeases().get(lease.id()));
    }

    private static SceneLease newHarvestLease(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job, String suffix) {
        BodyPosition current = job.traversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
        BlockPosition crop = FrontierResourceSitePlan.compile(state.bootstrap()).get(site).cropSlots().get(job.progress().nextCropSlotIndex());
        return SceneLease.forCause(new SceneLeaseId("lease:site-harvest-" + suffix), state.bootstrap().worldId(),
                new ResourceSiteHarvestSceneCause(job.id()), crop, new SimInstant(22_300L), 0L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), job.workerId()))),
                java.util.Map.of(job.workerId(), current), java.util.Set.of(job.workerId()), Optional.empty());
    }

    private static CommandPlan hotCheckpointPlan(FrontierWorldState state, String suffix,
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

    private static void assertRejectedHotCheckpoint(FrontierWorldState state, String suffix,
                                                    ResourceSiteHarvestHotTraversalAdvanced checkpoint) {
        assertTrue(hotCheckpointPlan(state, suffix, checkpoint) instanceof CommandPlan.Rejected,
                "invalid HOT checkpoint must be rejected before event persistence: " + suffix);
    }

    private record HotHarvest(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job, SceneLease lease) { }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-harvest"), 125L));
    }

    private static long column(int x, int z) {
        return (Integer.toUnsignedLong(x) << 32) | Integer.toUnsignedLong(z);
    }
}
