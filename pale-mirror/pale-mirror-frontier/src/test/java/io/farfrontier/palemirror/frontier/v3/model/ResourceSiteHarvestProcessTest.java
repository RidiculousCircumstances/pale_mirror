package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Goal-navigation regression and shared fixture; obsolete route-cursor tests are archived. */
class ResourceSiteHarvestProcessTest {
    static FrontierWorldState initial() { return initial(125L); }
    static FrontierWorldState initial(long seed) {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-harvest-" + seed), seed));
    }
    static FrontierWorldState ready(FrontierWorldState state) {
        SubjectId site = new SubjectId("site:1-wheat-field");
        var preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        state = ResourceSiteProcess.reducePrepared(state, site, (ResourceSitePrepared) preparation.get(1).payload());
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle current = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site, new ResourceSiteGrowthAdvanced(site, current.growthEpoch(), current.growthStage()));
        }
        return state;
    }
    static ColdHarvest coldHarvestAfterSteps(long seed, int coldSteps) {
        FrontierWorldState state = ready(initial(seed));
        SubjectId site = new SubjectId("site:1-wheat-field");
        var opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 22_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(value -> value.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        var start = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 22_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) start.getFirst().payload());
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, (ResourceSiteHarvestStarted) start.get(1).payload());
        state = ResourceSiteHarvestProcess.reducePrepared(state, site, ((PhysicalIntentPrepared) start.get(2).payload()).intent());
        ScheduledAction due = start.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).findFirst().orElseThrow();
        for (int index = 0; index < coldSteps; index++) {
            var step = stepCold(state, site, due);
            state = step.state(); due = step.next();
        }
        return new ColdHarvest(state, site, (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow());
    }
    static HotHarvest hotHarvestAfterColdSteps(int coldSteps) { return hotHarvestAfterColdSteps(125L, coldSteps); }
    static HotHarvest hotHarvestAfterColdSteps(long seed, int coldSteps) {
        ColdHarvest cold = coldHarvestAfterSteps(seed, coldSteps);
        SceneLease lease = newHarvestLease(cold.state(), cold.site(), cold.job(), "hot-goal-" + seed + "-" + coldSteps);
        FrontierWorldState state = cold.state().prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        return new HotHarvest(state, cold.site(), cold.job(), state.sceneLeases().get(lease.id()));
    }
    static SceneLease newHarvestLease(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job, String suffix) {
        BodyPosition current = state.actorLocations().get(job.workerId()).body();
        BlockPosition anchor = FrontierResourceSitePlan.compile(state.bootstrap()).get(site).cropSlots()
                .get(Math.min(job.progress().completedCropSlots(), job.progress().totalCropSlots() - 1));
        return SceneLease.forCause(new SceneLeaseId("lease:site-harvest-" + suffix), state.bootstrap().worldId(),
                new ResourceSiteHarvestSceneCause(site, job.id()), anchor, new SimInstant(22_300L), 1L,
                SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), job.workerId()))),
                Map.of(job.workerId(), current), java.util.Set.of(job.workerId()), Optional.empty());
    }
    static FrontierWorldState completeHarvestWorkHot(FrontierWorldState state, SubjectId site,
                                                    ResourceSiteHarvestJob job, SceneLeaseId leaseId) {
        for (int slot = job.progress().completedCropSlots(); slot < job.progress().totalCropSlots(); slot++) {
            ResourceSiteHarvestJob current = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
            ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, current);
            BodyPosition station = goal.representative().standingBody();
            if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, current))
                state = ResourceSiteHarvestProcess.reduceHotGoalArrived(state, site,
                        new ResourceSiteHarvestHotGoalArrived(current.id(), leaseId, current.workerId(),
                                goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(), station));
            state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site,
                    new ResourceSiteHarvestCropPrepared(current.id(), slot));
            ResourceFieldCycle field = state.resourceSites().cycle(site);
            ResourceFieldLayout.CellId cell = field.layout().cells().get(slot).id();
            ScheduledAction due = ResourceSiteHarvestProcess.coldProgress(current, 22_301L);
            SceneLease lease = state.sceneLeases().get(leaseId);
            int hand = field.harvestedCount() - current.deliveredYieldQuantity() +
                    (field.expectedWorkOutcome(cell) == ResourceFieldCycle.WorkOutcome.HARVESTED ? 1 : 0);
            state = ResourceSiteHarvestProcess.reduceProgressed(state, site,
                    new ResourceSiteHarvestProgressed(site, field.epoch(), current.id(), slot + 1,
                            field.layout().revision(), cell, field.expectedWorkOutcome(cell), due.id(), due.dueAt().ticks(),
                            Optional.of(new ResourceSiteHarvestProgressed.HandObservation(
                                    new PhysicalStackAddress.ActorHand(current.workerId(), lease.members().getFirst().entityId()),
                                    lease.revision(), hand))));
        }
        return state;
    }
    private static Step stepCold(FrontierWorldState state, SubjectId site, ScheduledAction due) {
        ScheduledAction next = null;
        for (ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, due)) {
            switch (event.payload()) {
                case ResourceSiteHarvestColdGoalAdvanced advanced -> state = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, site, advanced);
                case ResourceSiteHarvestCropPrepared prepared -> state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, prepared);
                case ResourceSiteHarvestProgressed progressed -> state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                case ResourceSiteHarvestReturned returned -> state = ResourceSiteHarvestProcess.reduceReturned(state, site, returned);
                case ResourceSiteHarvestColdGoalHeld held -> state = ResourceSiteHarvestProcess.reduceColdGoalHeld(state, site, held);
                case ResourceSiteHarvestBlockedCellSkipped skipped -> state = ResourceSiteHarvestProcess.reduceBlockedCellSkipped(state, site, skipped);
                case ScheduleEffect.Rescheduled scheduled -> next = scheduled.replacement();
                default -> { }
            }
        }
        return new Step(state, next);
    }
    @Test void coldWorkerReachesSemanticCellWithoutRouteCursorAndSurvivesSnapshot() {
        ColdHarvest start = coldHarvestAfterSteps(125L, 0);
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(start.state(), start.job());
        assertEquals(ResourceSiteHarvestGoal.Kind.WORK_CELL, goal.kind());
        FrontierWorldState state = start.state();
        ScheduledAction due = ResourceSiteHarvestProcess.coldProgress(start.job(), 22_301L);
        for (int turn = 0; turn < 256 && !ResourceSiteHarvestGoal.actorAtWorkCell(state,
                (ResourceSiteHarvestJob) state.resourceSites().site(start.site()).activeWork().orElseThrow()); turn++) {
            Step step = stepCold(state, start.site(), due); state = step.state(); due = step.next();
        }
        ResourceSiteHarvestJob current = (ResourceSiteHarvestJob) state.resourceSites().site(start.site()).activeWork().orElseThrow();
        assertTrue(ResourceSiteHarvestGoal.actorAtWorkCell(state, current));
        assertEquals(0, current.progress().completedCropSlots());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void hotFieldReceiptsCoverEveryCellWithoutMovingTheWorkGoalByWaypoints() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob start = hot.job();
        FrontierWorldState worked = completeHarvestWorkHot(hot.state(), hot.site(), start, hot.lease().id());
        ResourceSiteHarvestJob completed = (ResourceSiteHarvestJob) worked.resourceSites().site(hot.site())
                .activeWork().orElseThrow();
        assertTrue(completed.progress().complete());
        assertEquals(completed.progress().totalCropSlots(), worked.resourceSites().cycle(hot.site()).accountedPrefixCount());
        assertEquals(ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE,
                ResourceSiteHarvestGoal.current(worked, completed).kind());
        assertEquals(start.workerId(), completed.workerId());
        assertEquals(worked, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(worked)));
    }
    @Test void interruptedHotTravelKeepsActualBodyAcrossColdHandoff() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob job = hot.job();
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(hot.state(), job);
        BodyPosition next = ResourceSiteHarvestKnownNavigation.path(hot.state(), job).get(1).standingBody();
        FrontierWorldState observed = ResourceSiteHarvestProcess.reduceHotTransitObserved(hot.state(), hot.site(),
                new ResourceSiteHarvestHotTransitObserved(job.id(), hot.lease().id(), job.workerId(),
                        goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(), next));
        assertEquals(job.progress(), ((ResourceSiteHarvestJob) observed.resourceSites().site(hot.site())
                .activeWork().orElseThrow()).progress());
        FrontierWorldState draining = observed.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING);
        FrontierWorldState released = draining.releaseSceneLease(hot.lease().id(),
                List.of(new SceneMemberPosition(job.workerId(), next,
                        draining.actorLocations().get(job.workerId()).condition().health())));
        assertEquals(next, released.actorLocations().get(job.workerId()).body());
        assertEquals(next.supportingSurface(), ResourceSiteHarvestKnownNavigation.path(released, job).getFirst());
        assertEquals(released, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released)));
    }
    record HotHarvest(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job, SceneLease lease) { }
    record ColdHarvest(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job) { }
    private record Step(FrontierWorldState state, ScheduledAction next) { }
}
