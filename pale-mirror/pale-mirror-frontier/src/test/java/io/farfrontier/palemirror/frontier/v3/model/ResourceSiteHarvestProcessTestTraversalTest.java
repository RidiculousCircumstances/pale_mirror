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


class ResourceSiteHarvestTraversalTest extends ResourceSiteHarvestProcessTest {
    @Test
    void idleFarmerKeepsItsRetainedCanonicalStationRatherThanInventingAFieldDeparture() {
        FrontierWorldState state = initial();
        SubjectId farmer = new SubjectId("resident:1-1");
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(new SubjectId("site:1-wheat-field"));

        AmbientActorProcess.AmbientGoal goal = AmbientActorProcess.goalFor(state, farmer);

        assertEquals(AmbientGoalKind.PATROL, goal.kind());
        assertEquals(state.actorLocations().get(farmer).body().supportingSurface().support(), goal.position(),
                "a profession-only lease must materialize at the exact retained canonical station");
        assertFalse(goal.position().equals(ResourceSiteHarvestTraversal.workReturnSurface(state.bootstrap(), site).support()),
                "an idle farmer has no retained field-return traversal to execute on first ingress");
    }

    @Test
    void pendingHarvestWaitsForItsFarmerToCloseAnOlderAmbientLeaseBeforeItCapturesTheCursor() {
        FrontierWorldState state = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState tasked = harvestTask(state, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        SubjectId farmer = FrontierWorldStateSupport.availableFieldResident(tasked, new SubjectId("settlement:1"),
                ResidentProfession.AGRICULTURAL_WORKER).orElseThrow().id();
        AmbientActorLease olderLease = AmbientActorProcess.nextLease(tasked, farmer, new SimInstant(22_050L));
        FrontierWorldState leased = AmbientLeaseStateProcess.prepare(tasked, olderLease);
        ScheduledAction start = ResourceSiteHarvestProcess.start(task, 22_100L);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(leased, start);

        assertEquals(1, planned.size());
        ScheduleEffect.Rescheduled retry = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getFirst().payload());
        assertEquals(start.id(), retry.scheduleId());
        assertEquals(ResourceSiteHarvestProcess.start(task, 22_100L + tasked.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()), retry.replacement());
        assertEquals(StrategicTaskStatus.PENDING, leased.strategicPlans().tasks().get(task.id()).status());
        assertEquals(ResourceSitePhase.READY, leased.resourceSites().site(site).phase());

        FrontierWorldState closed = AmbientLeaseStateProcess.release(
                AmbientLeaseStateProcess.transition(leased, farmer, AmbientLeaseStatus.DRAINING),
                new AmbientLeaseReleased(farmer, olderLease.handoffBody(), leased.actorLocations().get(farmer).condition().health()));
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> resumed = ResourceSiteHarvestProcess.plan(closed, retry.replacement());
        ResourceSiteHarvestStarted started = assertInstanceOf(ResourceSiteHarvestStarted.class, resumed.get(1).payload());
        assertEquals(farmer, started.job().workerId());
        assertEquals(closed.actorLocations().get(farmer).body(), started.job().traversal().linearCorridorSurfaces().getFirst().standingBody());
    }

    @Test
    void retainedSuccessorMayDeclareItsExactHotFarmerForSceneHandoff() {
        FrontierWorldState state = ready(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        SubjectId settlement = new SubjectId("settlement:1");
        SubjectId farmer = FrontierWorldStateSupport.availableFieldResident(state, settlement,
                ResidentProfession.AGRICULTURAL_WORKER).orElseThrow().id();
        ResourceSiteHarvestLineage lineage = new ResourceSiteHarvestLineage(new SubjectId("job:site-harvest-1-wheat-field-1"),
                new SubjectId("task:settlement-1-settlement_harvest_resource_site-21002"), farmer,
                new SubjectId("item:site-harvest-1-wheat-field-1-wheat"), 1L,
                state.actorLocations().get(farmer).body(),
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:site-harvest-1-wheat-field-1"),
                new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 0), true, Optional.empty(), Optional.empty());
        state = state.withResourceSites(state.resourceSites().replace(new ResourceSiteLifecycle(site, ResourceSitePhase.READY, 2L,
                ResourceSiteLifecycle.MATURE_STAGE, Optional.empty(), Optional.empty(), Optional.of(lineage))));
        FrontierWorldState tasked = harvestTask(state, site, 22_000L); StrategicTask task = onlyHarvestTask(tasked);
        BodyPosition body = tasked.actorLocations().get(farmer).body();
        AmbientActorLease lease = AmbientActorProcess.nextLease(tasked, farmer, new SimInstant(22_050L));
        FrontierWorldState hot = AmbientLeaseStateProcess.prepare(tasked, lease);
        hot = AmbientLeaseStateProcess.transition(hot, farmer, AmbientLeaseStatus.HOT);
        hot = AmbientLeaseStateProcess.retarget(hot, farmer, AmbientGoalKind.PATROL, body);

        FrontierWorldState guarded = AmbientLeaseStateProcess.retarget(hot, farmer, AmbientGoalKind.GUARD, body);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> guardedPlan = ResourceSiteHarvestProcess.plan(guarded,
                ResourceSiteHarvestProcess.start(task, 22_100L));
        assertEquals(1, guardedPlan.size(), "a foreign HOT purpose must remain owner-local pending work, not be captured as a farm successor");
        assertInstanceOf(ScheduleEffect.Rescheduled.class, guardedPlan.getFirst().payload());

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.plan(hot,
                ResourceSiteHarvestProcess.start(task, 22_100L));

        assertEquals(4, planned.size(), "the ordinary retained PATROL return must create the scene handoff candidate, not retry forever");
        ResourceSiteHarvestStarted started = assertInstanceOf(ResourceSiteHarvestStarted.class, planned.get(1).payload());
        assertEquals(farmer, started.job().workerId());
        assertEquals(body, started.job().traversal().linearCorridorSurfaces().getFirst().standingBody());
        assertTrue(planned.stream().noneMatch(event -> event.payload() instanceof ScheduleEffect.Rescheduled),
                "the exact retained handoff must not consume its start turn as an ambient retry");
        FrontierWorldState active = StrategicObjectiveProcess.reduceTaskTransition(hot, settlement,
                (StrategicTaskTransition) planned.getFirst().payload());
        FrontierWorldState harvesting = ResourceSiteHarvestProcess.reduceStarted(active, site, started);
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site,
                ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        assertEquals(AmbientLeaseStatus.HOT, harvesting.ambientLeases().get(farmer).status(),
                "declaring the successor must retain the already-observed body for the typed scene handoff");
        assertFalse(FrontierSceneAdmission.available(harvesting, List.of(farmer)),
                "COLD cannot progress while the exact HOT body still owns the handoff boundary");
        assertEquals(farmer, FrontierResourceSiteHarvestSceneSupport.candidate(harvesting, started.job()).orElseThrow().workerId(),
                "the declared job is immediately the one bounded scene candidate that captures this exact worker");
    }

    @Test
    void activeHarvestMakesAnyAmbientFallbackRetainItsExactColdCursor() {
        ColdHarvest cold = coldHarvestAfterSteps(125L, 3);

        AmbientActorProcess.AmbientGoal goal = AmbientActorProcess.goalFor(cold.state(), cold.job().workerId());

        assertEquals(AmbientGoalKind.WORK, goal.kind());
        assertEquals(cold.job().traversal().linearCorridorSurfaces().get(cold.job().traversalCursor()).support(), goal.position());
        assertFalse(goal.position().equals(ResourceSiteHarvestTraversal.workReturnSurface(cold.state().bootstrap(),
                FrontierResourceSitePlan.compile(cold.state().bootstrap()).get(cold.site())).support()),
                "an active job must not inherit its prior terminal departure goal");
    }

    @Test
    void restartedPreLeaseHarvestReturnsTheSameWorkerToItsRetainedColdCursor() {
        ColdHarvest cold = coldHarvestAfterSteps(125L, 3);
        BodyPosition cursor = cold.job().traversal().linearCorridorSurfaces().get(cold.job().traversalCursor()).standingBody();
        AmbientActorLease lease = AmbientActorProcess.nextLease(cold.state(), cold.job().workerId(), new SimInstant(22_500L));

        FrontierWorldState prepared = AmbientLeaseStateProcess.prepare(cold.state(), lease);
        FrontierWorldState unknown = AmbientLeaseStateProcess.transition(
                AmbientLeaseStateProcess.transition(prepared, cold.job().workerId(), AmbientLeaseStatus.HOT),
                cold.job().workerId(), AmbientLeaseStatus.UNKNOWN_AFTER_RESTART);
        FrontierWorldState hot = AmbientLeaseStateProcess.transition(unknown, cold.job().workerId(), AmbientLeaseStatus.HOT);
        FrontierWorldState draining = AmbientLeaseStateProcess.transition(hot, cold.job().workerId(), AmbientLeaseStatus.DRAINING);
        assertThrows(IllegalArgumentException.class, () -> AmbientLeaseStateProcess.release(draining,
                new AmbientLeaseReleased(cold.job().workerId(), new BodyPosition(cursor.x(), cursor.y() - 1, cursor.z()),
                        cold.state().actorLocations().get(cold.job().workerId()).condition().health())),
                "a transient loaded feet cell must not replace an admitted harvest cursor before COLD resumes");
        FrontierWorldState released = AmbientLeaseStateProcess.release(draining,
                new AmbientLeaseReleased(cold.job().workerId(), cursor, cold.state().actorLocations().get(cold.job().workerId()).condition().health()));

        assertEquals(cursor, lease.goalBody(), "the recovery lease must not retain an earlier post-harvest return target");
        assertEquals(cursor, released.actorLocations().get(cold.job().workerId()).body());
        assertEquals(AmbientLeaseStatus.CLOSED, released.ambientLeases().get(cold.job().workerId()).status());
        assertEquals(cold.job().traversalCursor() + 1, ((ResourceSiteHarvestJob) ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(released,
                cold.site(), new ResourceSiteHarvestColdTraversalAdvanced(cold.job().id(), cold.job().workerId(),
                        cold.job().traversalCursor() + 1)).resourceSites().site(cold.site()).activeWork().orElseThrow()).traversalCursor());
    }

    @Test
    void everyFreshWorldFieldHasOneBoundedClearPostHarvestDepartureStation() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:post-harvest-return-all-sites"), 91L);
        for (ResourceSite site : FrontierResourceSitePlan.compile(bootstrap).values()) {
            SurfaceAnchor station = ResourceSiteHarvestTraversal.workReturnSurface(bootstrap, site);
            assertFalse(site.managedSlots().contains(station.support()), "return station must stay outside field ownership: " + site.id());
            assertEquals(4, Math.abs(station.x() - site.cropSlots().getLast().x()) + Math.abs(station.z() - site.cropSlots().getLast().z()),
                    "return station must retain one four-cell ordinary departure: " + site.id());
        }
    }

    @Test
    void cropExecutionRequiresItsExactHotFieldSceneRatherThanAStandaloneReceiptOwner() {
        assertTrue(ResourceSiteHarvestProcess.irreversibleCropEffectsAdmitted(),
                "the current physical-effect owner must admit observed HOT crop work");
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        CommandId commandId = new CommandId("command:site-harvest-f0v2-gate");
        FrontierCommand command = new FrontierCommand(1, commandId, hot.state().bootstrap().worldId(), new Revision(1L),
                new SimInstant(22_302L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new ResourceSiteHarvestProgressed(hot.job().id(), 1));
        assertTrue(FrontierWorldProcessCatalog.planCommand("resource-sites", hot.state(), command) instanceof CommandPlan.Rejected,
                "a forged crop observation must fail before event/WAL admission without its exact HOT field scene");
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
        state = state.withStrategicPlans(state.strategicPlans().addObjective(strategic).addTask(strategicTask));
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
    void exactMatureFieldReservesOneNamedWheatStackUntilItsHotSceneReachesRunning() {
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
                        + tasked.bootstrap().ruleset().cadence().resourceHarvestTraversalInterval()),
                ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created) planned.get(3).payload()).action());
        harvesting = ResourceSiteHarvestProcess.reducePrepared(harvesting, site, prepared.intent());
        assertEquals(ResourceSitePhase.HARVESTING, harvesting.resourceSites().site(site).phase());
        assertEquals(PhysicalIntentStatus.PREPARED, harvesting.physicalIntents().get(prepared.intent().id()).status());
        assertFalse(harvesting.inventory().items().containsKey(output.id()), "canonical inventory must wait for Minecraft receipt");
        assertEquals(StrategicTaskStatus.ACTIVE, harvesting.strategicPlans().tasks().get(task.id()).status());
        FrontierResourceSiteHarvestSceneSupport.Candidate candidate = FrontierResourceSiteHarvestSceneSupport.candidate(harvesting, started.job()).orElseThrow();
        assertEquals(candidate.cropSlot(), FrontierSceneAdmission.genericAmbientAdmission(harvesting)
                        .preLeaseDemandAnchor(started.job().workerId()).orElseThrow(),
                "the retained field candidate, not the farmer's historical cursor, owns HOT hand-off demand");
        assertTrue(FrontierResourceSiteHarvestSceneSupport.candidate(harvesting, started.job()).isPresent(),
                "the traversal-only profile may acquire a HOT cursor lease without beginning its crop effect");
        assertTrue(FrontierSceneAdmission.reservedFromGenericAmbient(harvesting, started.job().workerId()),
                "the PREPARED traversal candidate keeps its exact worker out of unrelated ambient admission");

        harvesting = completeHarvestWork(harvesting, site, started.job());
        FrontierWorldState completedTraversal = harvesting;
        ResourceSiteHarvestObservation receipt = receipt(prepared.intent(), started.job(), output);
        PhysicalIntentTransition confirmed = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.planTransition(completedTraversal, prepared.intent(), confirmed, 22_200L),
                "a complete crop receipt cannot skip the durable RUNNING boundary");
        assertEquals(output, harvesting.inventory().items().get(output.id()),
                "a complete COLD cursor owns its one exact output before the loaded physical receipt");
        assertFalse(harvesting.physicalIntents().containsKey(prepared.intent().id()),
                "terminal COLD composition retires its already-canonical physical request");
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
    void coldHarvestDriverReplaysEveryRetainedTraversalAndCropReceiptWithoutAStaleReducerTransition() {
        ColdHarvest cold = coldHarvestAfterSteps(125L, 0);
        FrontierWorldState state = cold.state();
        ScheduledAction action = ResourceSiteHarvestProcess.coldProgress(cold.job(), 22_101L);

        for (int step = 0; step < 256 && state.resourceSites().site(cold.site()).phase() == ResourceSitePhase.HARVESTING; step++) {
            ResourceSiteHarvestJob before = (ResourceSiteHarvestJob) state.resourceSites().site(cold.site()).activeWork().orElseThrow();
            List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteHarvestProcess.planColdProgress(state, action);
            assertFalse(planned.isEmpty(), "the retained COLD action must make a durable disposition at step " + step);
            for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : planned) {
                switch (event.payload()) {
                    case ResourceSiteHarvestColdTraversalAdvanced advanced ->
                            state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, cold.site(), advanced);
                    case ResourceSiteHarvestCropPrepared prepared ->
                            state = ResourceSiteHarvestProcess.reduceCropPrepared(state, cold.site(), prepared);
                    case ResourceSiteHarvestProgressed progressed ->
                            state = ResourceSiteHarvestProcess.reduceProgressed(state, cold.site(), progressed);
                    case PhysicalIntentTransition transition ->
                            state = state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
                    case StrategicTaskTransition transition ->
                            state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"), transition);
                    case ScheduleEffect.Rescheduled rescheduled -> action = rescheduled.replacement();
                    case ScheduleEffect.Cancelled ignored -> { }
                    case ScheduleEffect.Created ignored -> { }
                    default -> throw new AssertionError("unexpected COLD event: " + event.payload().type());
                }
            }
        }
        ResourceSiteHarvestLineage after = state.resourceSites().site(cold.site()).harvestLineage().orElseThrow();
        assertEquals(ResourceSitePhase.GROWING, state.resourceSites().site(cold.site()).phase());
        assertEquals(64, state.inventory().items().get(after.outputItemId()).count());
        assertFalse(state.physicalIntents().containsKey(after.predecessorIntentId()),
                "terminal COLD composition cannot retain an unowned PREPARED intent into a later harvest epoch");
        assertTrue(state.inventory().items().containsKey(after.outputItemId()),
                "the final COLD cursor has one canonical output without a HOT-only boundary");
    }

    @Test
    void scheduledColdHotLeaseIntentObservationReconciliationAndRestartRetainOneExactReceiptAuthority() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob job = hot.job();
        CommandId runningCommandId = new CommandId("command:site-harvest-running");
        PhysicalIntentTransition runningTransition = new PhysicalIntentTransition(job.intentId(), PhysicalIntentStatus.RUNNING, Optional.empty());
        FrontierCommand runningCommand = new FrontierCommand(1, runningCommandId, hot.state().bootstrap().worldId(), new Revision(1L),
                new SimInstant(22_300L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(runningCommandId), runningTransition);
        CommandPlan.Accepted runningPlan = assertInstanceOf(CommandPlan.Accepted.class,
                FrontierWorldRuntimeDefinition.planCommand(hot.state(), runningCommand),
                "the scheduled HOT lease admits its exact physical intent through the canonical planner");
        assertEquals(1, runningPlan.events().size());
        FrontierWorldState released = reduceCanonical(hot.state(), "running", 1L, runningCommandId, runningPlan.events().getFirst())
                .transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING)
                .releaseSceneLease(hot.lease().id(), List.of(new SceneMemberPosition(job.workerId(),
                        hot.state().actorLocations().get(job.workerId()).body(),
                        hot.state().actorLocations().get(job.workerId()).condition().health())));
        ScheduledAction action = ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
        long coldRevision = 2L;

        for (int step = 0; step < 256 && released.resourceSites().site(hot.site()).phase() == ResourceSitePhase.HARVESTING; step++) {
            for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(released, action)) {
                if (event.payload() instanceof ScheduleEffect.Rescheduled rescheduled) {
                    action = rescheduled.replacement();
                } else if (!(event.payload() instanceof ScheduleEffect.Cancelled) && !(event.payload() instanceof ScheduleEffect.Created)) {
                    released = reduceCanonical(released, "cold-" + step + "-" + coldRevision, coldRevision,
                            new CommandId("command:site-harvest-cold-" + coldRevision), event);
                    coldRevision++;
                }
            }
        }

        ResourceSiteHarvestLineage lineage = released.resourceSites().site(hot.site()).harvestLineage().orElseThrow();
        assertEquals(ResourceSitePhase.GROWING, released.resourceSites().site(hot.site()).phase());
        assertTrue(lineage.receiptPending(), "a HOT-started physical effect remains one exact receipt owner after COLD closes semantic work");
        ResourceSiteHarvestCausality pendingTrace = lineage.causality();
        assertEquals(action.id().value(), pendingTrace.coldScheduleId(), "the terminal lineage retains the actual admitted COLD action, not a reconstructed schedule");
        assertEquals(action.dueAt().ticks(), pendingTrace.coldDueAt());
        assertEquals(List.of(hot.lease().id()), pendingTrace.hotLeaseIds(), "the retained HOT lease is linked by its typed job cause");
        assertEquals(job.intentId(), pendingTrace.intentId());
        assertTrue(pendingTrace.expectedPhysical().contains(job.outputItemId().value()));
        assertEquals("not_observed", pendingTrace.observedPhysical());
        assertEquals("pending_exact_physical_receipt", pendingTrace.reconciliation());
        assertEquals(PhysicalIntentStatus.RUNNING, released.physicalIntents().get(job.intentId()).status());
        assertEquals(64, released.inventory().items().get(job.outputItemId()).count());
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released));
        assertEquals(lineage, restored.resourceSites().site(hot.site()).harvestLineage().orElseThrow(),
                "restart retains the pending exact HOT receipt rather than replaying or dropping it");
        PhysicalIntent running = restored.physicalIntents().get(job.intentId());
        ExactItemStack output = restored.inventory().items().get(job.outputItemId());
        CommandId receiptCommandId = new CommandId("command:site-harvest-reconciled-receipt");
        PhysicalIntentTransition receiptTransition = new PhysicalIntentTransition(running.id(), PhysicalIntentStatus.CONFIRMED,
                Optional.of(receipt(running, job, output)));
        FrontierCommand receiptCommand = new FrontierCommand(1, receiptCommandId, restored.bootstrap().worldId(), new Revision(400L),
                new SimInstant(22_700L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(receiptCommandId), receiptTransition);
        CommandPlan receiptCandidate = FrontierWorldRuntimeDefinition.planCommand(restored, receiptCommand);
        assertTrue(receiptCandidate instanceof CommandPlan.Accepted,
                () -> "the retained restart receipt is reconciled only by its exact owner-local transition: " + receiptCandidate);
        CommandPlan.Accepted receiptPlan = (CommandPlan.Accepted) receiptCandidate;
        assertEquals(1, receiptPlan.events().size());
        FrontierWorldState confirmed = reduceCanonical(restored, "reconciled-receipt", 400L, receiptCommandId, receiptPlan.events().getFirst());
        assertFalse(confirmed.resourceSites().site(hot.site()).harvestLineage().orElseThrow().receiptPending(),
                "the exact late receipt resolves only its retained HOT-started lineage");
        ResourceSiteHarvestCausality confirmedTrace = confirmed.resourceSites().site(hot.site()).harvestLineage().orElseThrow().causality();
        assertTrue(confirmedTrace.completeForColdHotReceipt(), "one retained identity covers COLD schedule, HOT lease, intent, observation and reconciliation after restart");
        assertEquals("confirmed:" + receipt(running, job, output).id().value(), confirmedTrace.observedPhysical());
        assertEquals("confirmed_exact_physical_receipt", confirmedTrace.reconciliation());
        assertEquals(output, confirmed.inventory().items().get(output.id()),
                "late physical evidence confirms canonical custody without replaying wheat");
    }

    private static FrontierWorldState reduceCanonical(FrontierWorldState state, String phase, long revision,
                                                       CommandId commandId, io.farfrontier.palemirror.frontier.v3.api.ProposedEvent proposed) {
        return FrontierWorldRuntimeDefinition.reduce(state, new FrontierEvent(1,
                new EventId("event:site-harvest-" + phase + "-r" + revision),
                new TransactionId("transaction:site-harvest-" + phase + "-r" + revision), state.bootstrap().worldId(),
                new Revision(revision), new SimInstant(22_300L + revision), proposed.subject(), CauseChain.root(commandId), proposed.payload()));
    }

    @Test
    void successorTerminalBoundsAnOlderUnobservedHotReceiptWithoutOrphaningIt() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob first = hot.job();
        FrontierWorldState state = hot.state()
                .transitionPhysicalIntent(first.intentId(), PhysicalIntentStatus.RUNNING, Optional.empty())
                .transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING)
                .releaseSceneLease(hot.lease().id(), List.of(new SceneMemberPosition(first.workerId(),
                        hot.state().actorLocations().get(first.workerId()).body(),
                        hot.state().actorLocations().get(first.workerId()).condition().health())));
        ScheduledAction action = ResourceSiteHarvestProcess.coldProgress(first, 22_301L);
        state = completeColdTerminal(state, hot.site(), action);
        ResourceSiteHarvestLineage firstTerminal = state.resourceSites().site(hot.site()).harvestLineage().orElseThrow();
        assertTrue(firstTerminal.receiptPending());

        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(hot.site());
            state = ResourceSiteProcess.reduceGrowth(state, hot.site(),
                    new ResourceSiteGrowthAdvanced(hot.site(), lifecycle.growthEpoch(), lifecycle.growthStage()));
        }
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(hot.site()), 40_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"), (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"), (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask successorTask = state.strategicPlans().tasks().values().stream().filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE
                && candidate.status() == StrategicTaskStatus.PENDING).findFirst().orElseThrow();
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> successorPlan = ResourceSiteHarvestProcess.plan(state,
                ResourceSiteHarvestProcess.start(successorTask, 40_100L));
        ResourceSiteHarvestStarted successor = successorPlan.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ResourceSiteHarvestStarted.class::isInstance).map(ResourceSiteHarvestStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) successorPlan.getFirst().payload());
        state = ResourceSiteHarvestProcess.reduceStarted(state, hot.site(), successor);
        state = ResourceSiteHarvestProcess.reducePrepared(state, hot.site(), successorPlan.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow().intent());
        ScheduledAction successorAction = successorPlan.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ScheduleEffect.Created.class::isInstance).map(ScheduleEffect.Created.class::cast).findFirst().orElseThrow().action();
        state = completeColdTerminal(state, hot.site(), successorAction);

        assertFalse(state.physicalIntents().containsKey(first.intentId()),
                "a later renewable terminal cannot leave the old RUNNING receipt as an orphan");
        assertEquals(FencedRecoveryDisposition.ABANDON, state.fencedRecovery().tombstones().get(
                FencedRecoveryPhysicalIntentSupport.bindingId(hot.state().physicalIntents().get(first.intentId()))).disposition(),
                "the old physical replica is isolated as a bounded local disposition, not replay authority");
        assertFalse(state.resourceSites().site(hot.site()).harvestLineage().orElseThrow().receiptPending(),
                "the untouched successor composes only its own physical request");
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)),
                "the bounded rollover survives restart without reintroducing the old receipt");
    }

    private static FrontierWorldState completeColdTerminal(FrontierWorldState state, SubjectId site, ScheduledAction action) {
        for (int step = 0; step < 256 && state.resourceSites().site(site).phase() == ResourceSitePhase.HARVESTING; step++) {
            for (io.farfrontier.palemirror.frontier.v3.api.ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, action)) {
                switch (event.payload()) {
                    case ResourceSiteHarvestColdTraversalAdvanced advanced -> state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, advanced);
                    case ResourceSiteHarvestCropPrepared prepared -> state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, prepared);
                    case ResourceSiteHarvestProgressed progressed -> state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                    case StrategicTaskTransition transition -> state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"), transition);
                    case ScheduleEffect.Rescheduled rescheduled -> action = rescheduled.replacement();
                    case ScheduleEffect.Cancelled ignored -> { }
                    case ScheduleEffect.Created ignored -> { }
                    default -> throw new AssertionError("unexpected renewable COLD terminal event: " + event.payload().type());
                }
            }
        }
        return state;
    }

    @Test
    void terminalColdCompositionCancelsItsExactContinuationAndARecoveredObsoleteActionIsConsumed() {
        ColdHarvest cold = coldHarvestAfterSteps(125L, 0);
        ResourceSiteHarvestJob job = cold.job();
        FrontierWorldState completed = completeHarvestWork(cold.state(), cold.site(), job);
        assertFalse(completed.physicalIntents().containsKey(job.intentId()),
                "the canonical COLD output fences its old physical intent before the successor lifecycle can replace it");

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> obsolete = ResourceSiteHarvestProcess.planColdProgress(
                completed,
                ResourceSiteHarvestProcess.coldProgress(job, 22_201L));
        assertEquals(List.of(new ScheduleEffect.Consumed(ResourceSiteHarvestProcess.coldProgress(job, 0L).id())),
                obsolete.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).toList(),
                "an obsolete recovered tail must complete deterministically without reopening field work");
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
        assertEquals(1, accepted.events().size(),
                "a physical HOT checkpoint retains the engine-owned due action byte-for-byte; only semantic crop work consumes it");

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
    void fixedSeedQuietCalibrationKeepsEightHotColdHandOffsSemanticallyNeutral() {
        FrontierObserverNeutralityContract.Declaration declaration = FrontierObserverNeutralityContract.declaration(
                FrontierDurationProcessDriverRegistry.Family.RESOURCE_SITE_HARVEST);
        CalibrationFacts coldFacts = new CalibrationFacts(), hotColdFacts = new CalibrationFacts();

        for (long seed = 125L; seed < 133L; seed++) {
            ColdHarvest cold = coldHarvestAfterSteps(seed, 2);
            HotHarvest hot = hotHarvestAfterColdSteps(seed, 0);
            ResourceSiteHarvestJob beforeHot = hot.job();
            BodyPosition firstObserved = beforeHot.nextTraversalSurface().standingBody();
            FrontierWorldState checkpointed = ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(hot.state(), hot.site(),
                    new ResourceSiteHarvestHotTraversalAdvanced(beforeHot.id(), hot.lease().id(), beforeHot.workerId(), firstObserved,
                            beforeHot.traversalCursor() + 1));
            FrontierWorldState released = checkpointed.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING)
                    .releaseSceneLease(hot.lease().id(), List.of(new SceneMemberPosition(beforeHot.workerId(), firstObserved,
                            checkpointed.actorLocations().get(beforeHot.workerId()).condition().health())));
            ResourceSiteHarvestJob afterRelease = (ResourceSiteHarvestJob) released.resourceSites().site(hot.site()).activeWork().orElseThrow();
            FrontierWorldState switched = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(released, hot.site(),
                    new ResourceSiteHarvestColdTraversalAdvanced(afterRelease.id(), afterRelease.workerId(), afterRelease.traversalCursor() + 1));
            ResourceSiteHarvestJob switchedJob = (ResourceSiteHarvestJob) switched.resourceSites().site(hot.site()).activeWork().orElseThrow();
            ResourceSiteHarvestJob coldJob = cold.job();

            assertEquals(coldJob.traversalCursor(), switchedJob.traversalCursor(), "HOT/COLD hand-off keeps the fixed-seed cursor");
            assertEquals(cold.state().actorLocations().get(coldJob.workerId()).body(), switched.actorLocations().get(switchedJob.workerId()).body(),
                    "HOT observation and COLD advancement keep the same retained worker body");
            assertEquals(coldJob.traversal(), switchedJob.traversal(), "HOT has no replacement route or cursor topology");
            assertEquals(cold.state().physicalIntents().get(coldJob.intentId()).status(), switched.physicalIntents().get(switchedJob.intentId()).status(),
                    "the unchanged traversal effect retains the same recovery classification");
            coldFacts.append(cold.state(), coldJob, cold.site(), seed);
            hotColdFacts.append(switched, switchedJob, hot.site(), seed);
        }

        FrontierObserverNeutralityContract.Run coldBaseline = coldFacts.run(declaration);
        FrontierObserverNeutralityContract.Run hotCold = hotColdFacts.run(declaration);
        FrontierObserverNeutralityContract.requireComparable(coldBaseline, hotCold);

        LinkedHashMap<String, Long> alteredCustody = new LinkedHashMap<>(hotCold.custody());
        alteredCustody.put("seed:125:crop-slots", 1L);
        assertThrows(IllegalArgumentException.class, () -> FrontierObserverNeutralityContract.requireComparable(coldBaseline,
                new FrontierObserverNeutralityContract.Run(declaration, hotCold.actorIds(), hotCold.objectIds(), hotCold.claims(), alteredCustody,
                        hotCold.completedStages(), hotCold.retainedWork(), hotCold.legalTopology(), hotCold.confirmedEffects(),
                        hotCold.recoveryDiscriminators(), hotCold.randomOpportunityKeys(), hotCold.calibration())),
                "quiet calibration must fence one lost or minted crop rather than averaging it away");
    }

    @Test
    void repeatedHotColdSwitchesKeepOneFarmerWorkCursorAndUnstartedCropCustody() {
        ColdHarvest initial = coldHarvestAfterSteps(125L, 0);
        FrontierWorldState state = initial.state();
        SubjectId site = initial.site();
        SubjectId worker = initial.job().workerId();
        SubjectId jobId = initial.job().id();
        for (int cycle = 0; cycle < 8; cycle++) {
            ResourceSiteHarvestJob before = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
            SceneLease lease = newHarvestLease(state, site, before, "neutral-switch-" + cycle);
            FrontierWorldState hot = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
            BodyPosition observed = before.nextTraversalSurface().standingBody();
            FrontierWorldState checkpointed = ResourceSiteHarvestProcess.reduceHotTraversalAdvanced(hot, site,
                    new ResourceSiteHarvestHotTraversalAdvanced(before.id(), lease.id(), worker, observed, before.traversalCursor() + 1));
            FrontierWorldState draining = checkpointed.transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
            state = draining.releaseSceneLease(lease.id(), List.of(new SceneMemberPosition(worker, observed,
                    draining.actorLocations().get(worker).condition().health())));
            ResourceSiteHarvestJob released = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
            state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site,
                    new ResourceSiteHarvestColdTraversalAdvanced(released.id(), worker, released.traversalCursor() + 1));
            if (cycle == 3) state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        }

        ResourceSiteHarvestJob after = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
        assertEquals(jobId, after.id());
        assertEquals(worker, after.workerId());
        assertEquals(16, after.traversalCursor(), "each switch has exactly one observed HOT and one COLD topology edge");
        assertEquals(after.traversal().linearCorridorSurfaces().get(after.traversalCursor()).standingBody(),
                state.actorLocations().get(worker).body());
        assertEquals(0, after.progress().completedCropSlots(), "neutral transition cannot begin or replay crop custody");
        assertFalse(state.inventory().items().containsKey(after.outputItemId()));
        assertEquals(PhysicalIntentStatus.PREPARED, state.physicalIntents().get(after.intentId()).status());
    }

}
