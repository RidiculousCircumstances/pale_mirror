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
        for (var event : CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(settlement, 1, 4_000L))) {
            if (event.payload() instanceof CompanyRegistered registered) state = CompanyFoundationProcess.reduce(state, settlement, registered);
            if (event.payload() instanceof EmploymentContractOpened opened) state = CompanyFoundationProcess.reduceEmployment(state, settlement, opened);
        }
        return ProductionProcessTest.productionTask(state, StrategicTaskStatus.PENDING);
    }
}
