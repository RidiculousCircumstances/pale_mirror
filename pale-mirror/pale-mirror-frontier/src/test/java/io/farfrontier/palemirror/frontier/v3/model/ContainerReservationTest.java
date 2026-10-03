package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContainerReservationTest {
    @Test
    void inputPickupProtectsItsFreedCapacityUntilExactOutputDeliveryAcrossRecovery() {
        var state = ProductionProcessTest.productionTask(FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:inbound-capacity"), 41L)), StrategicTaskStatus.PENDING);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var depot = FrontierWorldState.depotId(task.ownerId());
        var inventory = state.inventory();
        while (inventory.firstFreeSlot(depot).isPresent()) {
            int slot = inventory.firstFreeSlot(depot).orElseThrow();
            inventory = inventory.store(new ExactItemStack(new SubjectId("item:inbound-block-" + slot),
                    task.ownerId(), "minecraft:stone", 1, new InventoryCustody.ContainerSlot(depot, slot)));
        }
        state = state.withInventory(inventory);
        assertTrue(ProductionOutputCapacity.canAdmitBreadBatch(state, task.ownerId()));
        var started = io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.planStart(state,
                io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.start(task, 200L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess.reduceTaskTransition(state,
                task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.reduceStarted(state, task.ownerId(), started);
        long due = 300L;
        while (state.productionJobs().get(started.job().id()).bakeryWork().orElseThrow().phase()
                == BakeryWorkState.Phase.DEPOT_PICKUP && due < 10_000L) {
            var job = state.productionJobs().get(started.job().id());
            var step = io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.planCompletion(state,
                    io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.complete(job, due)).stream()
                    .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                    .filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            due += 20L;
        }
        assertEquals(BakeryWorkState.Phase.STATION_LOAD, state.productionJobs().get(started.job().id()).bakeryWork().orElseThrow().phase());
        assertTrue(state.inventory().firstFreeSlot(depot).isPresent(), "input really left storage");
        assertTrue(state.firstFreeContainerSlot(depot).isEmpty(), "new output reservations cannot steal that space");
        assertTrue(state.firstFreeContainerSlotForOutput(depot, started.job().id()).isPresent(), "the owner may deliver into its own space");
        assertThrows(IllegalArgumentException.class, () -> ContainerInboundCapacity.incoming(
                List.of(new ContainerInboundCapacity.Demand(started.job().id(), depot, "minecraft:bread", 64)),
                depot, java.util.Optional.of(new SubjectId("job:foreign-output"))));
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(Map.of("minecraft:bread", 64L), recovered.pendingContainerInbound(depot));
        assertTrue(recovered.firstFreeContainerSlot(depot).isEmpty());
        assertTrue(recovered.firstFreeContainerSlotForOutput(depot, started.job().id()).isPresent());
    }
    @Test
    void activeHarvestReservationConsumesRealCapacityForOtherProducers() {
        FrontierWorldState state = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0).state();
        ResourceSiteHarvestJob harvest = (ResourceSiteHarvestJob) state.resourceSites()
                .site(new SubjectId("site:1-wheat-field")).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        SubjectId depot = harvest.outputSlot().containerId();
        int added = 0;
        while (state.firstFreeContainerSlot(depot).isPresent()) {
            int slot = state.firstFreeContainerSlot(depot).orElseThrow();
            state = state.withInventory(state.inventory().store(new ExactItemStack(
                    new SubjectId("item:reservation-capacity-" + added++), harvestOwner(state, depot),
                    "minecraft:stone", 1, new InventoryCustody.ContainerSlot(depot, slot))));
        }
        assertFalse(state.canReceiveFungible(depot, "minecraft:bread", 64));
        assertTrue(state.inventory().canReceiveFungible(depot, "minecraft:bread", 64),
                "the unreserved inventory alone still sees the last slot; the world must fence it");
        assertTrue(ReferenceContainerCustody.expectedFungibleSlot(state, depot, harvest.outputSlot().slot()).isEmpty());
    }

    @Test
    void bakeryStockUsesAnotherSlotWithoutStealingActiveHarvestOutput() {
        FrontierWorldState state = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0).state();
        ResourceSiteHarvestJob harvest = (ResourceSiteHarvestJob) state.resourceSites()
                .site(new SubjectId("site:1-wheat-field")).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        SubjectId depot = harvest.outputSlot().containerId();
        SubjectId accountId = ReferenceContainerCustody.scopeId(depot);
        SubjectId wheat = new SubjectId("lot:bootstrap-1-wheat");
        SubjectId firstBread = new SubjectId("lot:reservation-first-bread");
        SubjectId bakeryBread = new SubjectId("lot:reservation-bakery-bread");
        assertEquals(1, harvest.outputSlot().slot());

        FungibleResourceLedger first = state.inventory().fungibleResources().transformCold(accountId,
                Map.of(wheat, 64), Map.of(), new ResourceLot(firstBread, harvestOwner(state, depot),
                        "minecraft:bread", 64, "test:prior-batch", List.of(wheat)));
        state = state.withInventory(state.inventory().withFungibleResources(first));
        assertTrue(state.canReceiveFungible(depot, "minecraft:bread", 64));

        // A second completed batch needs a second bread stack. The old packing algorithm
        // put it in slot 1 and quarantined the still-active field despite ample free space.
        Map<SubjectId, ResourceLot> lots = new HashMap<>(first.lots());
        lots.put(bakeryBread, new ResourceLot(bakeryBread, harvestOwner(state, depot),
                "minecraft:bread", 64, "test:bakery-output", List.of()));
        Map<SubjectId, CustodyAccount> accounts = new HashMap<>(first.accounts());
        CustodyAccount prior = accounts.get(accountId);
        accounts.put(accountId, new CustodyAccount(accountId, prior.custody(),
                Map.of(firstBread, 64, bakeryBread, 64), prior.claimQuantities()));
        FrontierWorldState completed = state.withInventory(state.inventory().withFungibleResources(
                new FungibleResourceLedger(lots, first.claims(), accounts, first.bindings())));
        assertTrue(ReferenceContainerCustody.expectedFungibleSlot(completed, depot, 1).isEmpty());
        assertEquals("minecraft:bread", ReferenceContainerCustody.expectedFungibleSlot(completed, depot, 2)
                .orElseThrow().itemKind());
        assertFalse(completed.containerSlotAvailable(harvest.outputSlot()));
        assertThrows(IllegalArgumentException.class, () -> ReferenceContainerCustody.expectedFungibleSlot(completed, depot, -1));
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(completed));
        assertEquals(ReferenceContainerCustody.canonicalFingerprint(completed, depot),
                ReferenceContainerCustody.canonicalFingerprint(recovered, depot));
    }

    private static SubjectId harvestOwner(FrontierWorldState state, SubjectId depot) {
        return state.inventory().containers().get(depot).ownerId();
    }
}
