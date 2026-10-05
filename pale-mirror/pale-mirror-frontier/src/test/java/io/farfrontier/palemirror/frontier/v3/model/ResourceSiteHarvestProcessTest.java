package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestRetargeting;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Goal-navigation regression and shared fixture; obsolete route-cursor tests are archived. */
class ResourceSiteHarvestProcessTest {
    @Test void hungryFarmerStartsReadyHarvestWhenDepotHasNoBread() {
        FrontierWorldState state = ready(initial(126L));
        SubjectId site = new SubjectId("site:1-wheat-field");
        SubjectId settlement = new SubjectId("settlement:1");
        ResidentProfile farmer = FrontierWorldStateSupport.availableWorkResident(state, settlement,
                ResidentWorkKind.AGRICULTURE, HumanCapability.AGRICULTURE).orElseThrow();
        assertTrue(ResidentMealOpportunity.find(state, farmer.id()).isEmpty());
        assertTrue(ResidentActivityCoordinator.mayStartOrdinaryWork(state, farmer.id(), 27_000L));
        var opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 27_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, settlement,
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, settlement,
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(value -> value.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();

        var planned = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 27_085L));
        assertTrue(planned.stream().map(ProposedEvent::payload).anyMatch(ResourceSiteHarvestStarted.class::isInstance),
                "an empty food depot must not prevent the food-producing farmer from beginning the real job");
    }

    @Test void travellingMealDoesNotHideAvailableBreadFromAnotherHungryFarmer() {
        FrontierWorldState state = ready(initial());
        SubjectId site = new SubjectId("site:1-wheat-field");
        SubjectId settlement = new SubjectId("settlement:1");
        SubjectId depot = FrontierWorldState.depotId(settlement);
        var ledger = state.inventory().fungibleResources().transformCold(
                ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:harvest-meal-bread"), settlement,
                        "minecraft:bread", 64, "test", List.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        ResidentProfile farmer = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement)
                        && resident.profession() == ResidentProfession.AGRICULTURAL_WORKER)
                .findFirst().orElseThrow();
        var meal = ResidentMealProcess.selectSourceAtYield(state, farmer.id(), 27_000L).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, farmer.id(), meal);
        assertEquals(1, state.humanPopulation().meals().size());
        var opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 27_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, settlement,
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, settlement,
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(value -> value.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        ScheduledAction due = ResourceSiteHarvestProcess.start(task, 27_085L);
        var planned = ResourceSiteHarvestProcess.plan(state, due);
        assertTrue(planned.stream().map(ProposedEvent::payload)
                .noneMatch(ResourceSiteHarvestStarted.class::isInstance),
                "a second hungry farmer with available bread should eat before taking a new job");
        assertTrue(planned.stream().map(ProposedEvent::payload)
                .anyMatch(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class::isInstance),
                "the field task remains pending until an eligible farmer can take it");
        assertEquals(StrategicTaskStatus.PENDING, state.strategicPlans().tasks().get(task.id()).status());
    }

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
        return coldHarvestAfterSteps(seed, coldSteps, true);
    }
    private static ColdHarvest coldHarvestAfterSteps(long seed, int coldSteps, boolean small) {
        // Single-batch helpers keep an explicit 64-cell fixture; live bootstrap size is independent.
        WorldId world = new WorldId("frontier:resource-site-harvest-" + seed);
        FrontierWorldState state = ready(FrontierWorldState.initial(small
                ? FrontierResourceSiteHarvestFixture.smallFieldBootstrap(world, seed)
                : FrontierBootstrapper.create(world, seed)));
        SubjectId site = new SubjectId("site:1-wheat-field");
        // These older harvest fixtures exercise the 22k work instant. Keep that
        // instant within a declared WORK window instead of bypassing activity policy.
        state = state.withHumanPopulation(state.humanPopulation().withSchedule(new SubjectId("settlement:1"),
                new SettlementDailySchedule(24_000, List.of(
                        new SettlementDailySchedule.Segment(0, 23_999, SettlementDailySchedule.Window.WORK),
                        new SettlementDailySchedule.Segment(23_999, 24_000, SettlementDailySchedule.Window.FREE)))));
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
        return new ColdHarvest(state, site, (ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow());
    }
    static ColdHarvest fullBatchAtDepotWithoutFutureCapacity() {
        var start = coldHarvestAfterSteps(125L, 0, false);
        FrontierWorldState state = start.state();
        ScheduledAction due = ResourceSiteHarvestProcess.coldProgress(start.job(), 22_301L);
        ResourceSiteHarvestJob job = start.job();
        for (int step = 0; step < 1024 && !job.returningForBatch(); step++) {
            Step next = stepCold(state, start.site(), due);
            state = next.state(); due = next.next();
            job = state.resourceSites().site(start.site()).harvestJob(job.id()).orElseThrow();
        }
        assertTrue(job.returningForBatch());
        assertEquals(64, job.undeliveredYieldQuantity());
        state = state.withActorBody(job.workerId(), ResourceSiteHarvestGoal.depotPort(state, job).stations().getFirst().standingBody());
        var depot = job.outputSlot().containerId();
        for (int stack = 0; stack < 54 && state.firstFreeContainerSlot(depot).isPresent(); stack++) {
            var resources = state.inventory().fungibleResources();
            var lots = new java.util.LinkedHashMap<>(resources.lots());
            var id = new SubjectId("lot:capacity-turnover-bread-" + stack);
            lots.put(id, new ResourceLot(id, new SubjectId("settlement:1"), "minecraft:bread", 64, "test", List.of()));
            var accounts = new java.util.LinkedHashMap<>(resources.accounts());
            var account = accounts.get(job.depotAccountId());
            var quantities = new java.util.LinkedHashMap<>(account.lotQuantities()); quantities.put(id, 64);
            accounts.put(account.id(), new CustodyAccount(account.id(), account.custody(), quantities, account.claimQuantities()));
            state = state.withInventory(state.inventory().withFungibleResources(new FungibleResourceLedger(
                    lots, resources.claims(), accounts, resources.bindings())));
        }
        assertTrue(state.firstFreeContainerSlot(depot).isEmpty());
        return new ColdHarvest(state, start.site(), job);
    }

    @Test void currentBatchDeliveryDoesNotRequireFutureCapacityOrCreditUnworkedCells() {
        var fixture = fullBatchAtDepotWithoutFutureCapacity();
        var state = fixture.state();
        var job = fixture.job();
        var field = state.resourceSites().cycle(job.siteId());
        var depot = job.outputSlot().containerId();
        assertTrue(ResourceSiteHarvestGoal.actorAtDepot(state, job));
        var candidate = FrontierResourceSiteHarvestSceneSupport.candidate(state, job).orElseThrow();
        assertEquals(state.actorLocations().get(job.workerId()).supportingSurface().support(),
                candidate.memberPositions().get(job.workerId()),
                "a returned COLD worker must be admissible at its actual station for a HOT delivery");
        assertEquals(state.actorLocations().get(job.workerId()).body().x(), candidate.cropSlot().x(),
                "delivery demand follows the retained worker, not the distant last harvested crop");
        assertTrue(ResourceSiteHarvestCargo.deliveryCapacityAvailable(state, job));
        assertFalse(ActivityExecutionCapabilities.waitingForServiceResource(state,
                HumanAssignmentProjection.compile(state).assignment(job.workerId())));
        var due = ResourceSiteHarvestProcess.coldProgress(job, 27_000L);
        var returned = new ResourceSiteHarvestReturned(job.id(), job.workerId(), due.id(), due.dueAt().ticks());
        var after = ResourceSiteHarvestProcess.reduceReturned(state, job.siteId(), returned);
        var settled = after.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow();
        assertEquals(64, settled.deliveredYieldQuantity());
        assertEquals(64, settled.progress().completedCropSlots());
        assertTrue(settled.progress().complete(), "only this offer ends when future capacity is unavailable");
        assertEquals(field, after.resourceSites().cycle(job.siteId()), "remaining plants are not marked harvested");
        assertFalse(after.inventory().fungibleResources().accounts().containsKey(job.actorAccountId()));
        assertFalse(after.harvestOutputReserves(job.outputSlot()));
        assertFalse(after.reservedContainerSlots(depot).contains(job.outputSlot().slot()));
        assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceReturned(after, job.siteId(),
                new ResourceSiteHarvestReturned(job.id(), new SubjectId("resident:1-999"), due.id(), due.dueAt().ticks())));
        var retired = ResourceSiteHarvestProcess.reduceReturned(after, job.siteId(), returned);
        assertTrue(retired.resourceSites().site(job.siteId()).harvestJob(job.id()).isEmpty());
        assertEquals(field, retired.resourceSites().cycle(job.siteId()));
    }
    static HotHarvest hotHarvestAfterColdSteps(int coldSteps) { return hotHarvestAfterColdSteps(125L, coldSteps); }
    static ColdHarvest coldHarvestWithCargo(long seed) {
        ColdHarvest start = coldHarvestAfterSteps(seed, 0);
        FrontierWorldState state = start.state();
        ScheduledAction due = ResourceSiteHarvestProcess.coldProgress(start.job(), 22_301L);
        for (int turn = 0; turn < 256 && !state.inventory().fungibleResources().accounts()
                .containsKey(start.job().actorAccountId()); turn++) {
            Step step = stepCold(state, start.site(), due); state = step.state(); due = step.next();
        }
        assertTrue(state.inventory().fungibleResources().accounts().containsKey(start.job().actorAccountId()),
                "fixture must stop on an actual harvested cargo receipt, not an estimated number of travel steps");
        return new ColdHarvest(state, start.site(), (ResourceSiteHarvestJob) state.resourceSites().site(start.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow());
    }
    static HotHarvest hotHarvestAfterColdSteps(long seed, int coldSteps) {
        ColdHarvest cold = coldHarvestAfterSteps(seed, coldSteps);
        SceneLease lease = newHarvestLease(cold.state(), cold.site(), cold.job(), "hot-goal-" + seed + "-" + coldSteps);
        FrontierWorldState state = confirmedPhysicalParticipants(cold.state().prepareSceneLease(lease), lease)
                .transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        return new HotHarvest(state, cold.site(), cold.job(), state.sceneLeases().get(lease.id()));
    }
    /** Modeled physical evidence, not native acceptance or a scene-owned body transition. */
    static FrontierWorldState confirmedPhysicalParticipants(FrontierWorldState state, SceneLease lease) {
        for (var member : lease.members()) {
            var body = ActorBodyAuthority.current(state, member.actorId());
            if (ActorBodyAuthority.require(state, body).phase() == FencedRecoveryPhase.RUNNING) continue;
            var location = state.actorLocations().get(member.actorId());
            state = ActorBodyAuthority.present(state, new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent(
                    body, location.body(), location.condition().health(), location.body(), location.condition().health()));
        }
        return state;
    }
    static SceneLease newHarvestLease(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job, String suffix) {
        BodyPosition current = state.actorLocations().get(job.workerId()).body();
        BlockPosition anchor = FrontierResourceSitePlan.compile(state.bootstrap()).get(site).cropSlots()
                .get(job.progress().complete() ? job.progress().lastCompletedCropSlotIndex()
                        : job.progress().nextCropSlotIndex());
        return SceneLease.forCause(new SceneLeaseId("lease:site-harvest-" + suffix), state.bootstrap().worldId(),
                new ResourceSiteHarvestSceneCause(site, job.id()), anchor, new SimInstant(22_300L), 1L,
                SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), job.workerId()))), java.util.Set.of(job.workerId()), Optional.empty());
    }
    static FrontierWorldState completeHarvestWorkHot(FrontierWorldState state, SubjectId site,
                                                    ResourceSiteHarvestJob job, SceneLeaseId leaseId) {
        for (int slot = job.progress().completedCropSlots(); slot < job.progress().totalCropSlots(); slot++) {
            ResourceSiteHarvestJob current = (ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
            ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, current);
            BodyPosition station = goal.representative().standingBody();
            if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, current))
                state = inspectGoal(state, site, current, leaseId);
            state = completeLabourHot(state, site, leaseId);
            current = (ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
            int selected = current.progress().nextCropSlotIndex();
            state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site,
                    new ResourceSiteHarvestCropPrepared(current.id(), selected, current.target().generation()));
            ResourceFieldCycle field = state.resourceSites().cycle(site);
            ResourceFieldLayout.CellId cell = field.layout().cells().get(selected).id();
            ScheduledAction due = ResourceSiteHarvestProcess.coldProgress(current, 22_301L);
            SceneLease lease = state.sceneLeases().get(leaseId);
            int hand = field.harvestedCount() - current.deliveredYieldQuantity() +
                    (field.expectedWorkOutcome(cell) == ResourceFieldCycle.WorkOutcome.HARVESTED ? 1 : 0);
            state = ResourceSiteHarvestProcess.reduceProgressed(state, site,
                    new ResourceSiteHarvestProgressed(site, field.epoch(), current.id(), current.progress().completedCropSlots() + 1,
                            field.layout().revision(), cell, current.target().generation(), field.expectedWorkOutcome(cell), due.id(), due.dueAt().ticks(),
                            Optional.of(new ResourceSiteHarvestProgressed.HandObservation(
                                    new PhysicalStackAddress.ActorHand(current.workerId(), lease.members().getFirst().entityId()),
                                    lease.revision(), hand))));
            var accepted = state.resourceSites().site(site).harvestJob(current.id()).orElseThrow().progress().acceptance();
            if (accepted.isPresent()) state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestAcceptanceProcess
                    .acknowledge(state, site, new ResourceSiteHarvestWorkAcknowledged(accepted.orElseThrow()));
        }
        return state;
    }
    static FrontierWorldState completeLabourHot(FrontierWorldState state, SubjectId site, SceneLeaseId lease) {
        var job = (ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        long tick = job.progress().work().map(WorkProgress::evaluatedAtTick).orElse(22_301L);
        for (int interval = 0; interval < 8 && !job.progress().work().map(WorkProgress::complete).orElse(false); interval++) {
            boolean running = job.progress().work().map(WorkProgress::running).orElse(false);
            if (running) tick = job.progress().work().orElseThrow().activeUntilTick();
            else if (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.workerId(), tick))
                tick = ResidentActivityCoordinator.nextOrdinaryWorkCheck(state, job.workerId(), tick);
            var changed = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess.change(
                    state, job, tick, !running, ResourceSiteHarvestProcess.coldProgress(job, tick), Optional.of(lease));
            state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess.reduce(state, site, changed);
            job = (ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        }
        assertTrue(job.progress().work().orElseThrow().complete());
        return state;
    }
    private static Step stepCold(FrontierWorldState state, SubjectId site, ScheduledAction due) {
        ScheduledAction next = null;
        for (ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, due)) {
            switch (event.payload()) {
                case ResourceSiteHarvestColdGoalAdvanced advanced -> state = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, site, advanced);
                case ResourceSiteHarvestWorkChanged changed -> state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess.reduce(state, site, changed);
                case ResourceSiteHarvestImmatureCellSkipped skipped -> state = ResourceSiteHarvestProcess.reduceCellSkipped(state, site, skipped);
                case ResourceSiteHarvestCropPrepared prepared -> state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, prepared);
                case ResourceSiteHarvestProgressed progressed -> state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                case ResourceSiteHarvestReturned returned -> state = ResourceSiteHarvestProcess.reduceReturned(state, site, returned);
                case ResourceSiteHarvestColdGoalHeld held -> state = ResourceSiteHarvestProcess.reduceColdGoalHeld(state, site, held);
                case ResourceSiteHarvestBlockedCellSkipped skipped -> state = ResourceSiteHarvestProcess.reduceBlockedCellSkipped(state, site, skipped);
                case ResourceSiteHarvestTargetRetargeted retargeted -> state = ResourceSiteHarvestRetargeting.reduceTargetRetargeted(state, site, retargeted);
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
                (ResourceSiteHarvestJob) state.resourceSites().site(start.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow()); turn++) {
            Step step = stepCold(state, start.site(), due); state = step.state(); due = step.next();
        }
        ResourceSiteHarvestJob current = (ResourceSiteHarvestJob) state.resourceSites().site(start.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertTrue(ResourceSiteHarvestGoal.actorAtWorkCell(state, current));
        assertEquals(0, current.progress().completedCropSlots());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void overdueHotHandoffCannotReplayFastColdFieldSteps() {
        ColdHarvest start = coldHarvestAfterSteps(125L, 0);
        ScheduledAction due = ResourceSiteHarvestProcess.coldProgress(start.job(), 22_301L);
        long releasedAt = 22_700L;
        assertEquals(1L, start.state().bootstrap().ruleset().cadence().resourceHarvestTraversalInterval());
        assertEquals(20L, start.state().bootstrap().ruleset().resourceHarvestColdTravelTicksPerEdge());

        var planned = ResourceSiteHarvestProcess.planColdProgress(start.state(), due, releasedAt);
        assertEquals(1L, planned.stream().map(ProposedEvent::payload)
                .filter(ResourceSiteHarvestColdGoalAdvanced.class::isInstance).count());
        assertTrue(planned.stream().map(ProposedEvent::payload)
                .noneMatch(ResourceSiteHarvestProgressed.class::isInstance));
        ScheduledAction next = planned.stream().map(ProposedEvent::payload)
                .filter(ScheduleEffect.Rescheduled.class::isInstance)
                .map(ScheduleEffect.Rescheduled.class::cast).findFirst().orElseThrow().replacement();
        assertEquals(releasedAt + 20L, next.dueAt().ticks());
    }

    @Test void bodyFreeObstructedPreparationRetiresWithoutLosingTheExactFarmerOrColdContinuation() {
        ColdHarvest start = coldHarvestAfterSteps(125L, 0);
        FrontierWorldState state = start.state();
        ResourceSite site = state.resourceSite(start.site());
        BlockPosition crop = site.cropSlots().getFirst();
        // The persisted feet cell has become the farmland support cell after projection.
        BodyPosition staleBody = new BodyPosition(crop.x(), crop.y() - 1, crop.z());
        var actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(start.job().workerId(), actors.get(start.job().workerId()).withBody(staleBody));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        SceneLease lease = newHarvestLease(state, start.site(), start.job(), "obstructed-preparation")
                .withAmbientHandoff(java.util.Set.of());
        state = state.prepareSceneLease(lease);
        ScheduledAction due = ResourceSiteHarvestProcess.coldProgress(start.job(), 22_301L);
        assertTrue(ResourceSiteHarvestProcess.coldProgressHeld(state, due));

        var aborted = new ResourceSiteHarvestScenePreparationAborted(lease.id(), start.site(), start.job().id(),
                ActorBodyAuthority.current(state, start.job().workerId()));
        assertEquals(aborted, FrontierWorldRuntimeDefinition.payloadCodecs().decode(aborted.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(aborted)));
        CommandId commandId = new CommandId("command:field-preparation-aborted");
        FrontierCommand command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, commandId,
                state.bootstrap().worldId(), Revision.ZERO, new SimInstant(22_300L),
                start.job().workerId(), CauseChain.root(commandId), aborted);
        assertInstanceOf(CommandPlan.Rejected.class, FrontierWorldProcessCatalog.planCommand("resource-sites", state,
                new FrontierCommand(FrontierCommand.SCHEMA_VERSION, commandId, state.bootstrap().worldId(),
                        Revision.ZERO, new SimInstant(22_300L), start.job().workerId(), CauseChain.root(commandId),
                        new ResourceSiteHarvestScenePreparationAborted(lease.id(), start.site(),
                                new SubjectId("job:site-harvest-foreign"), aborted.body()))));
        ProposedEvent planned = assertInstanceOf(CommandPlan.Accepted.class,
                FrontierWorldProcessCatalog.planCommand("resource-sites", state, command)).events().getFirst();
        FrontierEvent event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:field-preparation-aborted"),
                new TransactionId("transaction:field-preparation-aborted"), state.bootstrap().worldId(),
                Revision.ZERO, new SimInstant(22_300L), planned.subject(), CauseChain.root(commandId), planned.payload());
        FrontierWorldState resumed = FrontierWorldProcessCatalog.reduce("resource-sites", state, event);
        assertEquals(SceneLeaseStatus.CLOSED, resumed.sceneLeases().get(lease.id()).status());
        assertEquals(staleBody, resumed.actorLocations().get(start.job().workerId()).body());
        assertTrue(ResourceSiteHarvestProcess.coldProgressHeld(resumed, due),
                "aborting scene admission cannot invent physical absence");
        resumed = ActorBodyAuthority.released(resumed, ActorBodyAuthority.current(resumed, start.job().workerId()));
        assertFalse(ResourceSiteHarvestProcess.coldProgressHeld(resumed, due));
        assertTrue(ResourceSiteHarvestProcess.planColdProgress(resumed, due).stream()
                .anyMatch(step -> step.payload() instanceof ResourceSiteHarvestColdGoalAdvanced));

        FrontierWorldState restarted = state.transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        ProposedEvent restartPlan = assertInstanceOf(CommandPlan.Accepted.class,
                FrontierWorldProcessCatalog.planCommand("resource-sites", restarted, command)).events().getFirst();
        FrontierEvent restartEvent = new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                new EventId("event:field-preparation-restart-aborted"),
                new TransactionId("transaction:field-preparation-restart-aborted"), restarted.bootstrap().worldId(),
                Revision.ZERO, new SimInstant(22_301L), restartPlan.subject(), CauseChain.root(commandId), restartPlan.payload());
        FrontierWorldState restartResumed = FrontierWorldProcessCatalog.reduce("resource-sites", restarted, restartEvent);
        assertEquals(SceneLeaseStatus.CLOSED, restartResumed.sceneLeases().get(lease.id()).status());
        assertTrue(ResourceSiteHarvestProcess.coldProgressHeld(restartResumed, due));
        restartResumed = ActorBodyAuthority.released(restartResumed,
                ActorBodyAuthority.current(restartResumed, start.job().workerId()));
        assertFalse(ResourceSiteHarvestProcess.coldProgressHeld(restartResumed, due));

        FrontierWorldState attempted = confirmedPhysicalParticipants(state, lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT)
                .transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        assertInstanceOf(CommandPlan.Rejected.class,
                FrontierWorldProcessCatalog.planCommand("resource-sites", attempted, command),
                "an attempted physical admission cannot be retired as a body-free preparation");
    }
    @Test void releasedHotPathFailureResumesOnlyThroughTheNextKnownColdStep() {
        ColdHarvest start = coldHarvestAfterSteps(125L, 0);
        ResourceSiteHarvestJob job = start.job();
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(start.state(), job);
        var block = new ResourceSiteHarvestNavigationBlock(goal.representative(), goal.layoutRevision(),
                ResourceSiteHarvestNavigationBlock.Reason.PATH_UNAVAILABLE);
        FrontierWorldState held = start.state().withResourceSites(start.state().resourceSites().replace(
                start.state().resourceSites().site(start.site()).blockHarvestRoute(job, block)));
        ScheduledAction due = ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
        assertFalse(ResourceSiteHarvestProcess.coldProgressHeld(held, due));
        BodyPosition before = held.actorLocations().get(job.workerId()).body();
        var planned = ResourceSiteHarvestProcess.planColdProgress(held, due);
        var step = planned.stream().map(ProposedEvent::payload)
                .filter(ResourceSiteHarvestColdGoalAdvanced.class::isInstance)
                .map(ResourceSiteHarvestColdGoalAdvanced.class::cast).findFirst().orElseThrow();
        FrontierWorldState resumed = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(held, start.site(), step);
        ResourceSiteHarvestJob current = (ResourceSiteHarvestJob) resumed.resourceSites().site(start.site())
                .harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertTrue(current.navigationBlock().isEmpty());
        assertNotEquals(before, resumed.actorLocations().get(job.workerId()).body());
        assertEquals(step.nextBody(), resumed.actorLocations().get(job.workerId()).body());
        assertEquals(0, current.progress().completedCropSlots());

        FrontierWorldState targetBlocked = held.recordPhysicalDelta(new PhysicalDelta(
                goal.representative().support().offset(0, 2, 0), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:farmer-target-head-block"));
        var alternative = ResourceSiteHarvestProcess.planColdProgress(targetBlocked, due).stream()
                .map(ProposedEvent::payload).filter(ResourceSiteHarvestTargetRetargeted.class::isInstance)
                .map(ResourceSiteHarvestTargetRetargeted.class::cast).findFirst().orElseThrow();
        FrontierWorldState retargeted = ResourceSiteHarvestRetargeting.reduceTargetRetargeted(
                targetBlocked, start.site(), alternative);
        ResourceSiteHarvestJob alternateJob = (ResourceSiteHarvestJob) retargeted.resourceSites()
                .site(start.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertTrue(alternateJob.navigationBlock().isEmpty());
        assertNotEquals(job.progress().nextCropSlotIndex(), alternateJob.progress().nextCropSlotIndex());
        assertEquals(0, alternateJob.progress().completedCropSlots());

        FrontierWorldState walled = held;
        for (BlockPosition neighbour : List.of(before.supportingSurface().support().offset(1, 1, 0),
                before.supportingSurface().support().offset(-1, 1, 0),
                before.supportingSurface().support().offset(0, 1, 1),
                before.supportingSurface().support().offset(0, 1, -1))) {
            walled = walled.recordPhysicalDelta(new PhysicalDelta(neighbour, PhysicalDeltaKind.UNKNOWN_SCAR,
                    Optional.empty(), Optional.empty(), "test:farmer-route-wall"));
        }
        var unavailable = ResourceSiteHarvestProcess.planColdProgress(walled, due);
        assertTrue(unavailable.stream().map(ProposedEvent::payload)
                .noneMatch(ResourceSiteHarvestColdGoalAdvanced.class::isInstance));
        assertTrue(unavailable.stream().map(ProposedEvent::payload)
                .noneMatch(ResourceSiteHarvestColdGoalHeld.class::isInstance));
    }
    @Test void blockedFinalCellCanBeSkippedWithoutRequiringItsWorkStationAsATransitNode() {
        var fixture = FrontierResourceSiteHarvestFixture.createWithOneExtraCellAfterColdPart(
                new WorldId("frontier:blocked-final-goal"), 125L);
        FrontierWorldState state = fixture.state();
        ResourceFieldCycle cycle = state.resourceSites().cycle(fixture.siteId());
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) state.resourceSites()
                .site(fixture.siteId()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        ResourceFieldLayout.CellId lastCell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        ResourceFieldCycle obstructed = cycle.observedWorkAccess(lastCell, true);
        state = state.withResourceSites(state.resourceSites().replace(
                state.resourceSites().site(fixture.siteId()), obstructed));
        ScheduledAction due = fixture.schedules().getFirst();
        FrontierWorldState skipped = ResourceSiteHarvestProcess.reduceBlockedCellSkipped(state, fixture.siteId(),
                new ResourceSiteHarvestBlockedCellSkipped(fixture.siteId(), job.id(), job.workerId(),
                        cycle.layout().revision(), List.of(lastCell), due.id(), due.dueAt().ticks(), Optional.empty()));
        assertEquals(65, skipped.resourceSites().cycle(fixture.siteId()).accountedCount());
        assertEquals(cycle.harvestedCount(), skipped.resourceSites().cycle(fixture.siteId()).harvestedCount());
        assertTrue(((ResourceSiteHarvestJob) skipped.resourceSites().site(fixture.siteId()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow())
                .progress().complete());
    }
    @Test void immatureFinalCellIsExcludedWithoutTravelYieldOrLosingCarriedHarvest() {
        var fixture = FrontierResourceSiteHarvestFixture.createWithOneExtraCellAfterColdPart(
                new WorldId("frontier:immature-final-goal"), 125L);
        FrontierWorldState original = fixture.state();
        ResourceFieldCycle cycle = original.resourceSites().cycle(fixture.siteId());
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) original.resourceSites()
                .site(fixture.siteId()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        var last = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        var replanted = cycle.cropReplanted(last);
        FrontierWorldState state = original.withResourceSites(original.resourceSites().replace(
                original.resourceSites().site(fixture.siteId()).bindHarvestTarget(job, replanted), replanted));
        var due = fixture.schedules().getFirst();
        var exclusion = new ResourceSiteHarvestImmatureCellSkipped(fixture.siteId(), job.id(), job.workerId(),
                cycle.layout().revision(), List.of(last), due.id(), due.dueAt().ticks(), Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> ResourceSiteHarvestProcess.reduceCellSkipped(original, fixture.siteId(), exclusion));
        var planned = ResourceSiteHarvestProcess.planColdProgress(state, due);
        assertTrue(planned.stream().map(ProposedEvent::payload)
                .anyMatch(ResourceSiteHarvestImmatureCellSkipped.class::isInstance));
        assertTrue(planned.stream().map(ProposedEvent::payload)
                .noneMatch(ResourceSiteHarvestColdGoalAdvanced.class::isInstance));
        FrontierWorldState skipped = ResourceSiteHarvestProcess.reduceCellSkipped(state, fixture.siteId(), exclusion);
        var active = (ResourceSiteHarvestJob) skipped.resourceSites().site(fixture.siteId()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertTrue(active.progress().complete());
        assertEquals(cycle.harvestedCount(), skipped.resourceSites().cycle(fixture.siteId()).harvestedCount());
        assertEquals(state.actorLocations(), skipped.actorLocations());
        assertEquals(state.inventory(), skipped.inventory());
        assertEquals(ResourceFieldCycle.Crop.GROWING, skipped.resourceSites().cycle(fixture.siteId()).cell(last).crop());
    }

    @Test void hotImmatureExclusionDoesNotRequireArrivalAtExcludedCell() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        var cycle = hot.state().resourceSites().cycle(hot.site());
        var job = hot.job();
        var id = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        var replanted = cycle.cropReplanted(id);
        var state = hot.state().withResourceSites(hot.state().resourceSites().replace(
                hot.state().resourceSites().site(hot.site()).bindHarvestTarget(job, replanted), replanted));
        var due = ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
        var skipped = ResourceSiteHarvestProcess.reduceCellSkipped(state, hot.site(),
                new ResourceSiteHarvestImmatureCellSkipped(hot.site(), job.id(), job.workerId(),
                        cycle.layout().revision(), List.of(id), due.id(), due.dueAt().ticks(), Optional.of(hot.lease().id())));
        assertEquals(state.actorLocations(), skipped.actorLocations());
        assertEquals(state.inventory(), skipped.inventory());
        assertEquals(1, skipped.resourceSites().cycle(hot.site()).accountedCount());
        assertEquals(0, skipped.resourceSites().cycle(hot.site()).harvestedCount());
    }

    @Test void unreachableHotTargetCanBeDeferredWithoutCreditingOrLosingItsCell() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob job = hot.job();
        ResourceFieldCycle field = hot.state().resourceSites().cycle(hot.site());
        int from = job.progress().nextCropSlotIndex();
        int to = field.reachableWorkSlotAfter(from, index -> true).orElseThrow();
        ScheduledAction due = ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
        var event = new ResourceSiteHarvestTargetRetargeted(hot.site(), job.id(), job.workerId(),
                field.layout().revision(), from, to, due.id(), due.dueAt().ticks(), Optional.of(hot.lease().id()));
        FrontierWorldState redirected = ResourceSiteHarvestRetargeting.reduceTargetRetargeted(
                hot.state(), hot.site(), event);
        ResourceSiteHarvestJob active = (ResourceSiteHarvestJob) redirected.resourceSites().site(hot.site())
                .harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertEquals(to, active.progress().nextCropSlotIndex());
        assertEquals(0, active.progress().completedCropSlots());
        assertFalse(redirected.resourceSites().cycle(hot.site()).cell(field.layout().cells().get(from).id()).accounted());
        assertEquals(redirected, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(redirected)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestRetargeting.reduceTargetRetargeted(
                redirected, hot.site(), event));

        for (var reason : List.of(ResourceSiteHarvestNavigationBlock.Reason.PATH_UNAVAILABLE,
                ResourceSiteHarvestNavigationBlock.Reason.PATH_STALLED)) {
            var routeBlock = new ResourceSiteHarvestNavigationBlock(
                    ResourceSiteHarvestGoal.current(hot.state(), job).representative(), field.layout().revision(), reason);
            FrontierWorldState held = hot.state().withResourceSites(hot.state().resourceSites().replace(
                    hot.state().resourceSites().site(hot.site()).blockHarvestRoute(job, routeBlock)));
            FrontierWorldState resumedAtAlternate = ResourceSiteHarvestRetargeting.reduceTargetRetargeted(
                    held, hot.site(), event);
            var resumedJob = (ResourceSiteHarvestJob) resumedAtAlternate.resourceSites().site(hot.site())
                    .harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
            assertEquals(to, resumedJob.progress().nextCropSlotIndex());
            assertTrue(resumedJob.navigationBlock().isEmpty());
        }
    }
    @Test void displacedPreparedCropRetainsItsEffectThroughRouteHoldAndObservedReturn() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        var job = hot.job();
        var state = inspectGoal(hot.state(), hot.site(), job, hot.lease().id());
        state = completeLabourHot(state, hot.site(), hot.lease().id());
        state = ResourceSiteHarvestProcess.reduceCropPrepared(state, hot.site(),
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation()));
        var prepared = state.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow();
        var goal = ResourceSiteHarvestGoal.current(state, prepared);
        state = ModeledActorBodyFacts.inspected(state, job.workerId(),
                new SurfaceAnchor(goal.representative().support().offset(1, 0, 0)).standingBody());
        var due = ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
        var block = new ResourceSiteHarvestNavigationBlock(goal.representative(), goal.layoutRevision(),
                ResourceSiteHarvestNavigationBlock.Reason.PATH_UNAVAILABLE);
        var blocked = new ResourceSiteHarvestRouteBlocked(hot.site(), job.id(), job.workerId(), block,
                hot.lease().id(), due.id(), due.dueAt().ticks());
        var held = ResourceSiteHarvestProcess.reduceRouteBlocked(state, hot.site(), blocked);
        var retained = held.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow();
        assertEquals(prepared.progress(), retained.progress());
        assertEquals(prepared.target(), retained.target());
        assertTrue(retained.progress().hasPendingCrop());
        assertEquals(state.inventory(), held.inventory());
        assertEquals(state.actorLocations(), held.actorLocations());
        assertFalse(ResourceSiteHarvestGoal.actorAtWorkCell(held, retained));
        assertEquals(held, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(held)));
        assertTrue(ResourceSiteHarvestProcess.coldProgressHeld(held, due));
        assertThrows(IllegalArgumentException.class, () -> retained.retargetTo(goal.nextWorkSlot() + 1));
        assertThrows(IllegalArgumentException.class, retained::withFullBatchReturn);
        assertThrows(IllegalArgumentException.class, () -> retained.withNavigationBlock(block));

        var resumed = inspectGoal(held, hot.site(), retained, hot.lease().id());
        var returned = resumed.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow();
        assertTrue(returned.navigationBlock().isEmpty());
        assertEquals(prepared.progress(), returned.progress());
        assertEquals(prepared.target(), returned.target());
        assertEquals(held.inventory(), resumed.inventory());
        assertTrue(ResourceSiteHarvestGoal.actorAtWorkCell(resumed, returned));
        var field = resumed.resourceSites().cycle(hot.site());
        var receipt = new ResourceSiteHarvestProgressed(hot.site(), field.epoch(), job.id(), 1,
                field.layout().revision(), job.target().cellId(), job.target().generation(),
                ResourceFieldCycle.WorkOutcome.HARVESTED, due.id(), due.dueAt().ticks(), Optional.of(
                    new ResourceSiteHarvestProgressed.HandObservation(new PhysicalStackAddress.ActorHand(job.workerId(),
                            hot.lease().members().getFirst().entityId()), hot.lease().revision(), 1)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceProgressed(held, hot.site(), receipt));
        var finished = ResourceSiteHarvestProcess.reduceProgressed(resumed, hot.site(), receipt);
        assertEquals(1, finished.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow().harvestedYieldQuantity());
        assertFalse(finished.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow().progress().hasPendingCrop());
        var acceptedJob = finished.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow();
        var acceptance = acceptedJob.progress().acceptance().orElseThrow();
        assertTrue(acceptedJob.progress().hasPendingPhysicalWork(), "accepted canonical progress still owns physical retirement");
        assertThrows(IllegalStateException.class, acceptedJob.progress()::prepareNextCrop);
        assertEquals(ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT, HarvestActivityCapability.checkpointStatus(finished, acceptedJob));
        var codec = new FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(finished));
        assertEquals(acceptance, restored.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow().progress().acceptance().orElseThrow());
        var acknowledged = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestAcceptanceProcess.acknowledge(
                restored, hot.site(), new ResourceSiteHarvestWorkAcknowledged(acceptance));
        assertFalse(acknowledged.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow().progress().hasPendingPhysicalWork());
        assertEquals(restored.inventory(), acknowledged.inventory(), "retirement cannot repeat wheat accrual");
        assertEquals(restored.actorLocations(), acknowledged.actorLocations(), "retirement has no body authority");
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestAcceptanceProcess
                .acknowledge(acknowledged, hot.site(), new ResourceSiteHarvestWorkAcknowledged(acceptance)));
        assertEquals(resumed.actorLocations(), finished.actorLocations());
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceProgressed(finished, hot.site(), receipt));
    }

    @Test void hotFieldReceiptsCoverEveryCellWithoutMovingTheWorkGoalByWaypoints() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob start = hot.job();
        FrontierWorldState worked = completeHarvestWorkHot(hot.state(), hot.site(), start, hot.lease().id());
        ResourceSiteHarvestJob completed = (ResourceSiteHarvestJob) worked.resourceSites().site(hot.site())
                .harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertTrue(completed.progress().complete());
        assertEquals(completed.progress().totalCropSlots(), worked.resourceSites().cycle(hot.site()).accountedPrefixCount());
        assertEquals(ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE,
                ResourceSiteHarvestGoal.current(worked, completed).kind());
        assertEquals(start.workerId(), completed.workerId());
        assertEquals(worked, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(worked)));
        // The actual reported boundary: all cells collected, wheat retained, FREE starts before delivery.
        worked = worked.withHumanPopulation(worked.humanPopulation().withSchedule(new SubjectId("settlement:1"),
                SettlementDailySchedule.initial()));
        assertEquals(SettlementDailySchedule.Window.FREE,
                worked.humanPopulation().schedule(new SubjectId("settlement:1")).windowAt(22_301L));
        assertFalse(ResidentActivityCoordinator.requestsYield(worked, completed.workerId(), 22_301L));
        assertTrue(ResidentActivityCoordinator.ordinaryWorkPermitted(worked, completed.workerId(), 22_301L));
        var body = worked.actorLocations().get(completed.workerId());
        var draining = worked.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING);
        var released = ResourceSiteHarvestProcess.reduceHandRelease(draining, worked.resourceSite(hot.site()).settlementId(),
                new ResourceSiteHarvestHandRelease(hot.site(), completed.id(), completed.actorAccountId(),
                        hot.lease().revision(), new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ActorHand(completed.workerId(), hot.lease().members().getFirst().entityId()),
                        "minecraft:wheat", completed.progress().totalCropSlots()),
                        new SceneLeaseReleased(hot.lease().id(), List.of(new SceneMemberPosition(
                                completed.workerId(), body.body(), body.condition().health())))));
        assertFalse(ActorExecutionCoordinator.coldAvailable(released, completed.workerId()),
                "process release alone is not physical body absence");
        released = ActorBodyAuthority.released(released, ActorBodyAuthority.current(released, completed.workerId()));
        var delivery = ResourceSiteHarvestProcess.planColdProgress(released,
                ResourceSiteHarvestProcess.coldProgress(completed, 22_301L));
        assertTrue(delivery.stream().map(ProposedEvent::payload)
                .anyMatch(ResourceSiteHarvestColdGoalAdvanced.class::isInstance),
                "FREE must admit actual delivery travel, not defer the batch until next WORK");
    }
    @Test void interruptedHotTravelKeepsActualBodyAcrossColdHandoff() {
        HotHarvest hot = hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestJob job = hot.job();
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(hot.state(), job);
        BodyPosition next = ResourceSiteHarvestKnownNavigation.path(hot.state(), job).get(1).standingBody();
        FrontierWorldState observed = ModeledActorBodyFacts.inspected(hot.state(), job.workerId(), next);
        assertEquals(job.progress(), ((ResourceSiteHarvestJob) observed.resourceSites().site(hot.site())
                .harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow()).progress());
        FrontierWorldState draining = observed.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING);
        FrontierWorldState released = draining.releaseSceneLease(hot.lease().id(),
                List.of(new SceneMemberPosition(job.workerId(), next,
                        draining.actorLocations().get(job.workerId()).condition().health())));
        assertEquals(next, released.actorLocations().get(job.workerId()).body());
        assertEquals(next.supportingSurface(), ResourceSiteHarvestKnownNavigation.path(released, job).getFirst());
        assertEquals(released, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released)));
    }
    /** Independent modeled physical fact; the field receipt only clears its own blocked goal. */
    static FrontierWorldState inspectGoal(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job,
                                          SceneLeaseId leaseId) {
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        var execution = state.actorExecutions().current(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST).get(job.workerId());
        var witness = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation(
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(
                        ActorBodyAuthority.current(state, job.workerId()), execution), state.sceneLeases().get(leaseId).revision());
        state = ModeledActorBodyFacts.inspected(state, job.workerId(), goal.representative().standingBody());
        return job.navigationBlock().isEmpty() ? state : ResourceSiteHarvestProcess.reduceHotGoalArrived(state, site,
                new ResourceSiteHarvestHotGoalArrived(job.id(), leaseId, job.workerId(), goal.layoutRevision(),
                        goal.nextWorkSlot(), goal.kind(), goal.representative().standingBody(), job.target().generation(), witness));
    }
    record HotHarvest(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job, SceneLease lease) { }
    record ColdHarvest(FrontierWorldState state, SubjectId site, ResourceSiteHarvestJob job) { }
    private record Step(FrontierWorldState state, ScheduledAction next) { }
}
