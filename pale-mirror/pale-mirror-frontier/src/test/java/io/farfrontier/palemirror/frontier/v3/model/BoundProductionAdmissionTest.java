package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BoundProductionAdmissionTest {
    @Test
    void exactPermissionsSurviveSnapshotAndDoNotCancelAnAlreadyAdmittedExecution() {
        var state = twoBakerFixture();
        var owner = new SubjectId("settlement:1");
        var bakers = SettlementWorkforce.candidates(state, owner, ResidentWorkKind.BAKING, HumanCapability.INDUSTRY);
        var selected = bakers.get(1).id();
        var permissions = new ResidentWorkPermissions(Map.of(ResidentWorkKind.BAKING, java.util.Set.of(selected)));
        state = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(owner, permissions));
        var codec = new FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        assertEquals(permissions, SettlementWorkPolicy.permissions(state, owner));
        var task = state.strategicPlans().tasks().values().iterator().next();
        var planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L));
        var started = planned.stream().map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        assertEquals(selected, started.job().workerId(), "capability alone must not authorize the preferred baker");
        state = StrategicObjectiveProcess.reduceTaskTransition(state, owner,
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, owner, started);
        var presentation = ResidentPresentation.from(state, selected);
        assertEquals("Baker", presentation.role());
        assertEquals(state.humanPopulation().resident(selected).name(), presentation.name());
        state = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(owner, ResidentWorkPermissions.none()));
        var restored = codec.decode(codec.encode(state));
        assertEquals(started.job(), restored.productionJobs().get(started.job().id()));
        assertTrue(ResidentWorkComposition.SELECTION.eligible(restored, owner, ResidentWorkKind.BAKING,
                HumanCapability.INDUSTRY, 201L).isEmpty());
        assertFalse(HumanAssignmentProjection.compile(restored).idle(selected));
    }

    @Test
    void settlementCannotAuthorizeAForeignResident() {
        var state = twoBakerFixture();
        var owner = new SubjectId("settlement:1");
        var foreign = state.humanPopulation().residents().values().stream()
                .filter(resident -> !resident.settlementId().equals(owner)).findFirst().orElseThrow().id();
        var plans = state.strategicPlans().withWorkPermissions(owner,
                new ResidentWorkPermissions(Map.of(ResidentWorkKind.BAKING, java.util.Set.of(foreign))));
        assertThrows(IllegalArgumentException.class, () -> state.withStrategicPlans(plans));
    }

    @Test
    void unavailablePreferredBakerDoesNotPreventAnotherBakerTakingTheWork() {
        var state = twoBakerFixture();
        var settlement = new SubjectId("settlement:1");
        var companyWorker = SettlementWorkPolicy.permissions(state, settlement).workers(ResidentWorkKind.BAKING)
                .stream().sorted().findFirst().orElseThrow();
        var company = new Company(CompanyFoundationProcess.companyId(settlement), settlement, companyWorker,
                CompanyPurpose.WORKS, CompanyStatus.ACTIVE, 0);
        state = CompanyFoundationProcess.reduce(state, settlement, new CompanyRegistered(company));
        for (var worker : SettlementWorkPolicy.permissions(state, settlement).workers(ResidentWorkKind.BAKING))
            state = SettlementEmploymentProcess.reduceEmployment(state, settlement, new EmploymentContractOpened(
                    SettlementEmploymentProcess.agreement(state, WorkEmployer.company(company), worker, 0)));
        var candidates = SettlementWorkforce.candidates(state, settlement, ResidentWorkKind.BAKING, HumanCapability.INDUSTRY);
        assertEquals(2, candidates.size());
        var preferred = candidates.getFirst();
        var body = state.actorLocations().get(preferred.id()).body();
        state = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(preferred.id(),
                new AmbientActorLease(preferred.id(), body, SimInstant.ZERO, 1L,
                        AmbientLeaseStatus.UNKNOWN_AFTER_RESTART, AmbientGoalKind.WORK, body))));
        var task = state.strategicPlans().tasks().values().iterator().next();
        var demand = MarketClearingProcess.foodDemand(state, task, 100L);
        state = MarketClearingProcess.reduceOpened(state, task.ownerId(), new MarketDemandOpened(demand));
        var world = state.bootstrap().worldId();
        var base = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var config = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(world, state, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(),
                base.limits(), List.of(MarketClearingProcess.clear(demand, 1, 200L)), base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(config);
        engine.advanceTo(new SimInstant(200L), new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(100, 100));
        var after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(1, after.productionJobs().size());
        var job = after.productionJobs().values().iterator().next();
        assertEquals(candidates.get(1).id(), job.workerId());
        assertTrue(after.companies().market().acceptedForJob(job.id()).isPresent());
        assertEquals(job.workerId(), ProductionCommercialProcess.contractFor(after, job).orElseThrow().residentId());
        assertTrue(HumanAssignmentProjection.compile(after).idle(preferred.id()));
        assertFalse(HumanAssignmentProjection.compile(after).idle(job.workerId()));
        assertEquals(AmbientLeaseStatus.UNKNOWN_AFTER_RESTART, after.ambientLeases().get(preferred.id()).status());
    }

    @Test
    void occupiedBakeryRetainsTheSettlementRequestWithoutAssigningTheOtherBaker() {
        var state = twoBakerFixture();
        var task = state.strategicPlans().tasks().values().iterator().next();
        var planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L));
        var started = assertInstanceOf(ProductionStarted.class, planned.get(1).payload());
        var active = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        state = ProductionProcess.reduceStarted(active, task.ownerId(), started);
        var another = new StrategicTask(new SubjectId("task:second-bakery-request"),
                task.objectiveId(),
                task.ownerId(),
                task.kind(),
                task.infectionTarget(),
                task.resourceSiteTarget(),
                task.requirements(),
                task.dependencies(),
                StrategicTaskStatus.PENDING,
                task.authorityId(),
                task.authorityEpoch());
        state = state.withStrategicPlans(state.strategicPlans().addTask(another));
        var jobs = state.productionJobs();
        var stock = state.inventory();
        var next = ProductionProcess.planStart(state, ProductionProcess.start(another, 201L));
        assertEquals(1, next.size());
        assertInstanceOf(ScheduleEffect.Rescheduled.class, next.getFirst().payload());
        assertSame(jobs, state.productionJobs());
        assertSame(stock, state.inventory());
        assertEquals(1, state.productionJobs().size());
        var assignments = HumanAssignmentProjection.compile(state);
        var authorizedBakers = SettlementWorkPolicy.permissions(state, task.ownerId()).workers(ResidentWorkKind.BAKING);
        assertEquals(1, state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(task.ownerId())
                        && authorizedBakers.contains(resident.id()))
                .filter(resident -> assignments.idle(resident.id())).count());
    }

    private static FrontierWorldState twoBakerFixture() {
        var state = coldFixture();
        var settlement = new SubjectId("settlement:1");
        assertEquals(2, SettlementWorkPolicy.permissions(state, settlement).workers(ResidentWorkKind.BAKING).size());
        return state;
    }

    @Test
    void registeredSchedulerStartsABoundJobAndRetainsItsWorkContinuation() {
        var state = fixture();
        var task = state.strategicPlans().tasks().values().iterator().next();
        var world = state.bootstrap().worldId();
        var base = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var config = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(world, state, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(),
                base.limits(), List.of(ProductionProcess.start(task, 200L)), base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(config);
        engine.advanceTo(new SimInstant(200L), new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(100, 100));
        var after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(1, after.productionJobs().size());
        var job = after.productionJobs().values().iterator().next();
        assertInstanceOf(ProductionInputHold.FungibleBound.class, job.inputHold());
        assertEquals(List.of(ProductionProcess.complete(job, 300L)), engine.checkpoint().schedules());
        assertEquals(StrategicTaskStatus.ACTIVE, after.strategicPlans().tasks().get(task.id()).status());
    }

    @Test
    void normalStartReservesCurrentPhysicalStockWithoutDroppingItToCold() {
        var state = fixture();
        var task = state.strategicPlans().tasks().values().iterator().next();
        var planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L));
        var started = assertInstanceOf(ProductionStarted.class, planned.get(1).payload());
        var bound = assertInstanceOf(ProductionInputHold.FungibleBound.class, started.job().inputHold());
        assertEquals(1L, bound.authorityEpoch());
        var active = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        var after = ProductionProcess.reduceStarted(active, task.ownerId(), started);
        assertEquals(started.job(), after.productionJobs().get(started.job().id()));
        assertEquals(state.inventory().fungibleResources().lots(), after.inventory().fungibleResources().lots());
        assertEquals(64, after.inventory().fungibleResources().claims().get(bound.claimId()).quantity());
        assertTrue(FrontierProductionWorkSceneSupport.hasPhysicalInput(after, started.job()));
        assertEquals(ProductionWorkProgress.notStarted(), started.job().workProgress());
        var codec = new FrontierWorldStateCodec();
        assertEquals(after.productionJobs(), codec.decode(codec.encode(after)).productionJobs());
        var stale = started.job().withInputHold(new ProductionInputHold.FungibleBound(bound.itemId(), bound.accountId(), bound.claimId(), 2L));
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceStarted(active, task.ownerId(), new ProductionStarted(stale, stale.consumedItemId())));
        assertThrows(IllegalArgumentException.class, () -> active.startFungibleProductionJob(stale));
        var cold = started.job().withInputHold(new ProductionInputHold.FungibleCold(bound.itemId(), bound.accountId(), bound.claimId()));
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceStarted(active, task.ownerId(), new ProductionStarted(cold, cold.consumedItemId())));
        assertThrows(IllegalArgumentException.class, () -> active.startFungibleProductionJob(cold));
    }

    @Test
    void observedChestWithoutItsResourceLayoutRetainsPendingStartInsteadOfFailingTheTask() {
        var state = fixture();
        var account = new SubjectId("custody:container-1-depot");
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources().releaseBindings(account, 1L)));
        var task = state.strategicPlans().tasks().values().iterator().next();
        var planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L));
        assertEquals(1, planned.size());
        var rescheduled = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getFirst().payload());
        assertEquals(ProductionProcess.start(task, 220L), rescheduled.replacement());
        assertTrue(state.productionJobs().isEmpty());
        assertEquals(StrategicTaskStatus.PENDING, task.status());
    }

    @Test
    void alreadyReservedBoundWheatDoesNotPublishAJobThatTheResourceOwnerWouldReject() {
        var state = fixture();
        var settlement = new SubjectId("settlement:1");
        var account = new SubjectId("custody:container-1-depot");
        var existing = new ClaimAllocation(new SubjectId("claim:other-work"), settlement, settlement,
                "minecraft:wheat", 64, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION);
        var resources = state.inventory().fungibleResources().reserveBound(existing, account, 1L);
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var task = state.strategicPlans().tasks().values().iterator().next();
        assertEquals(0, resources.unclaimedQuantity(account, settlement, "minecraft:wheat"));
        var planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L));
        assertEquals(1, planned.size());
        var rescheduled = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getFirst().payload());
        assertEquals(ProductionProcess.start(task, 220L), rescheduled.replacement());
        assertTrue(state.productionJobs().isEmpty());
    }

    @Test
    void twoThirtyTwoWheatLotsStartOnePinnedColdJob() {
        var state = twoLotFixture(coldFixture(), false);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L));
        var started = assertInstanceOf(ProductionStarted.class, planned.get(1).payload());
        var hold = assertInstanceOf(ProductionInputHold.FungibleCold.class, started.job().inputHold());
        assertEquals(twoLots(), hold.inputLots());
        var active = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        var after = ProductionProcess.reduceStarted(active, task.ownerId(), started);
        assertEquals(twoLots(), after.inventory().fungibleResources().claims().get(hold.claimId()).lotQuantities());
        var codec = new FrontierWorldStateCodec();
        assertEquals(after.productionJobs(), codec.decode(codec.encode(after)).productionJobs());
    }

    @Test
    void twoThirtyTwoWheatStacksStartOnePinnedBoundJob() {
        var state = twoLotFixture(fixture(), true);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L));
        var started = assertInstanceOf(ProductionStarted.class, planned.get(1).payload());
        var hold = assertInstanceOf(ProductionInputHold.FungibleBound.class, started.job().inputHold());
        assertEquals(twoLots(), hold.inputLots());
        var active = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                assertInstanceOf(StrategicTaskTransition.class, planned.getFirst().payload()));
        var after = ProductionProcess.reduceStarted(active, task.ownerId(), started);
        var resources = after.inventory().fungibleResources();
        assertEquals(twoLots(), resources.claims().get(hold.claimId()).lotQuantities());
        assertEquals(2, resources.bindings().values().stream().filter(binding -> binding.claimQuantities().containsKey(hold.claimId())).count());
        assertTrue(FrontierProductionWorkSceneSupport.hasPhysicalInput(after, started.job()));
    }

    private static Map<SubjectId, Integer> twoLots() {
        return Map.of(new SubjectId("lot:two-field-first"), 32, new SubjectId("lot:two-field-second"), 32);
    }

    private static FrontierWorldState twoLotFixture(FrontierWorldState state, boolean bound) {
        var owner = new SubjectId("settlement:1");
        var depot = FrontierWorldState.depotId(owner);
        var accountId = new SubjectId("custody:container-1-depot");
        var resources = state.inventory().fungibleResources();
        if (bound) resources = resources.releaseBindings(accountId, 1L);
        var account = resources.accounts().get(accountId);
        var oldId = account.lotQuantities().keySet().iterator().next();
        var lots = new HashMap<>(resources.lots());
        lots.remove(oldId);
        twoLots().forEach((id, quantity) -> lots.put(id, new ResourceLot(id, owner, "minecraft:wheat", quantity, "harvest:two-field", List.of())));
        var accounts = new HashMap<>(resources.accounts());
        accounts.put(accountId, new CustodyAccount(accountId, account.custody(), twoLots(), Map.of()));
        resources = new FungibleResourceLedger(lots, resources.claims(), accounts, resources.bindings());
        if (bound) {
            var stacks = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                            new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 32),
                    new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                            new InventoryCustody.ContainerSlot(depot, 1)), "minecraft:wheat", 32));
            resources = resources.rebind(accountId, 1L, FungiblePhysicalObservation.bind(resources, accountId, 1L, stacks));
        }
        return state.withInventory(state.inventory().withFungibleResources(resources));
    }

    private static FrontierWorldState fixture() {
        var state = coldFixture();
        var settlement = new SubjectId("settlement:1");
        var depot = FrontierWorldState.depotId(settlement);
        var account = new SubjectId("custody:container-1-depot");
        var resources = state.inventory().fungibleResources();
        var stacks = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64));
        resources = resources.rebind(account, 1L, FungiblePhysicalObservation.bind(resources, account, 1L, stacks));
        state = state.withInventory(state.inventory().withFungibleResources(resources).withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
        return ReferenceContainerCustodyFixtures.observedAndHeld(state, depot);
    }

    static FrontierWorldState coldFixture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:bound-production-admission"), 91L));
        var settlement = new SubjectId("settlement:1");
        return ProductionProcessTest.productionTask(state, StrategicTaskStatus.PENDING);
    }
}
