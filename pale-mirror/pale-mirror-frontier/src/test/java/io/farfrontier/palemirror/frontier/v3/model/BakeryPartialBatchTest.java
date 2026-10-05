package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BakeryPartialBatchTest {
    @ParameterizedTest @ValueSource(ints = {1, 60, 64, 80})
    void availableMultiLotBatchCompletesThroughStationAndRecovery(int stock) {
        var state = fixture(stock);
        var task = state.strategicPlans().tasks().values().iterator().next();
        int quantity = Math.min(64, stock);
        var started = start(state, task);
        var job = started.job();
        assertEquals(quantity, job.outputCount());
        assertEquals(quantity, job.inputQuantities().values().stream().mapToInt(Integer::intValue).sum());
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        assertEquals(stock - quantity, state.inventory().fungibleResources().unclaimedQuantity(
                job.bakeryWork().orElseThrow().sourceAccountId(), task.ownerId(), "minecraft:wheat"));
        var codec = new FrontierWorldStateCodec();
        EnumSet<BakeryColdStep.Action> seen = EnumSet.noneOf(BakeryColdStep.Action.class);
        for (long due = 300L, steps = 0; state.productionJobs().containsKey(job.id()) && steps < 700; due += 20L, steps++) {
            var step = ProductionProcess.planCompletion(state, ProductionProcess.complete(
                    state.productionJobs().get(job.id()), due)).stream().map(ProposedEvent::payload)
                    .filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            if (seen.add(step.action())) state = codec.decode(codec.encode(state));
        }
        assertFalse(state.productionJobs().containsKey(job.id()), "partial batch must deliver, not just be admitted");
        assertTrue(seen.containsAll(EnumSet.of(BakeryColdStep.Action.PICKUP, BakeryColdStep.Action.LOAD,
                BakeryColdStep.Action.RECIPE, BakeryColdStep.Action.UNLOAD, BakeryColdStep.Action.DELIVER)));
        assertEquals(quantity, state.inventory().fungibleResources().totalQuantity(task.ownerId(), "minecraft:bread"));
        assertEquals(stock - quantity, state.inventory().fungibleResources().totalQuantity(task.ownerId(), "minecraft:wheat"));
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(task.id()).status());
    }

    @Test void boundPartialStacksUseTheSameAllocationAndRejectMismatchedHandQuantity() {
        var state = fixture(60);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var depot = FrontierWorldState.depotId(task.ownerId());
        var account = ReferenceContainerCustody.scopeId(depot);
        var resources = state.inventory().fungibleResources();
        var observed = List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(depot, 3)), "minecraft:wheat", 30),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(depot, 8)), "minecraft:wheat", 30));
        resources = resources.rebind(account, 1L, FungiblePhysicalObservation.bind(resources, account, 1L, observed));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(
                state.withInventory(state.inventory().withFungibleResources(resources)), depot);
        var started = start(state, task);
        var job = started.job();
        assertEquals(60, job.outputCount());
        assertInstanceOf(ProductionInputHold.FungibleBound.class, job.inputHold());
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        var hold = (ProductionInputHold.FungibleBound) job.inputHold();
        var order = new ActorContainerItemOrder(job.id(), job.workerId(), ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(account, new ResourceCustody.Container(depot),
                        job.bakeryWork().orElseThrow().actorAccountId(), new ResourceCustody.Actor(job.workerId()),
                        Optional.of(hold.claimId()), "minecraft:wheat", job.inputQuantities()),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(depot),
                BakeryWorkGoal.current(state, job).station(), ActorContainerItemOrder.Hand.MAIN, 1L, 1L);
        var handAddress = new PhysicalStackAddress.ActorHand(job.workerId(), UUID.fromString(
                "00000000-0000-0000-0000-000000000111"), ActorContainerItemOrder.Hand.MAIN);
        var before = state.inventory().fungibleResources();
        assertThrows(IllegalArgumentException.class, () -> before.transferActorOrderObservedStacks(order, 1L, 1L,
                List.of(), List.of(new FungiblePhysicalObservation.Stack(handAddress, "minecraft:wheat", 59))));
        var taken = before.transferActorOrderObservedStacks(order, 1L, 1L, List.of(),
                List.of(new FungiblePhysicalObservation.Stack(handAddress, "minecraft:wheat", 60)));
        assertEquals(job.inputQuantities(), taken.accounts().get(job.bakeryWork().orElseThrow().actorAccountId()).lotQuantities());
        assertFalse(taken.accounts().containsKey(account));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void emptyInputCannotCreateABatch() {
        var state = fixture(0);
        var owner = state.bootstrap().settlements().getFirst().id();
        assertTrue(BakeryBatchSelection.available(state, owner).isEmpty());
        assertTrue(BakeryBatchSelection.admissible(state, owner).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ProductionStationRecipe.breadOutputQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> ProductionStationRecipe.breadOutputQuantity(65));
    }

    @Test void outputCapacityLimitsTheBatchWithoutConsumingAnotherOwnersReservation() {
        var state = fixture(64);
        var owner = state.bootstrap().settlements().getFirst().id();
        var depot = FrontierWorldState.depotId(owner);
        var accountId = ReferenceContainerCustody.scopeId(depot);
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(accountId);
        var bread = new SubjectId("lot:partial-capacity-bread");
        var lots = new LinkedHashMap<>(resources.lots());
        lots.put(bread, new ResourceLot(bread, owner, "minecraft:bread", 60, "test:stock", List.of()));
        var quantities = new LinkedHashMap<>(account.lotQuantities()); quantities.put(bread, 60);
        var accounts = new LinkedHashMap<>(resources.accounts());
        accounts.put(accountId, new CustodyAccount(accountId, account.custody(), quantities, Map.of()));
        resources = new FungibleResourceLedger(lots, resources.claims(), accounts, resources.bindings());
        var claim = new ClaimAllocation(new SubjectId("claim:external-wheat"), new SubjectId("work:external"),
                owner, "minecraft:wheat", 4, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION);
        resources = resources.reserve(claim, accountId);
        var inventory = state.inventory().withFungibleResources(resources);
        while (inventory.firstFreeSlot(depot).isPresent()) {
            int slot = inventory.firstFreeSlot(depot).orElseThrow();
            inventory = inventory.store(new ExactItemStack(new SubjectId("item:partial-capacity-" + slot), owner,
                    "minecraft:stone", 1, new InventoryCustody.ContainerSlot(depot, slot)));
        }
        state = state.withInventory(inventory);
        assertEquals(60, BakeryBatchSelection.available(state, owner).orElseThrow().quantity());
        assertEquals(4, BakeryBatchSelection.admissible(state, owner).orElseThrow().quantity(),
                "only four bread fit; the other owner's wheat cannot free the input slot");
        assertEquals(resources.claims(), state.inventory().fungibleResources().claims(), "selection is read-only");
    }

    private static ProductionStarted start(FrontierWorldState state, StrategicTask task) {
        return ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream().map(ProposedEvent::payload)
                .filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow();
    }

    static FrontierWorldState fixture(int stock) {
        var state = ProductionProcessTest.productionTask(FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:partial-bakery"), 41L)), StrategicTaskStatus.PENDING);
        var owner = state.bootstrap().settlements().getFirst().id();
        var depot = FrontierWorldState.depotId(owner);
        var accountId = ReferenceContainerCustody.scopeId(depot);
        var prior = state.inventory().fungibleResources();
        var lots = new LinkedHashMap<>(prior.lots());
        var accounts = new LinkedHashMap<>(prior.accounts());
        var account = accounts.remove(accountId);
        account.lotQuantities().keySet().forEach(lots::remove);
        var quantities = new LinkedHashMap<SubjectId, Integer>();
        for (int remaining = stock, part = 0; remaining > 0; part++) {
            int count = Math.min(30, remaining);
            var id = new SubjectId("lot:partial-wheat-" + part);
            lots.put(id, new ResourceLot(id, owner, "minecraft:wheat", count, "test:partial-stock", List.of()));
            quantities.put(id, count); remaining -= count;
        }
        if (stock > 0) accounts.put(accountId, new CustodyAccount(accountId,
                new ResourceCustody.Container(depot), quantities, Map.of()));
        return state.withInventory(state.inventory().withFungibleResources(
                new FungibleResourceLedger(lots, prior.claims(), accounts, prior.bindings())));
    }
}
