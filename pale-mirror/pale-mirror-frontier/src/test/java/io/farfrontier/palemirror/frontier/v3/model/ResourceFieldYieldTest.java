package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceFieldYieldTest {
    private static final SubjectId SITE = new SubjectId("site:1-wheat-field");
    private static final SubjectId OWNER = new SubjectId("settlement:1");

    @Test void completedCellOutcomesProduceZeroPartialAndMultiStackLotsWithoutMintingLosses() {
        var zero = ResourceFieldYield.fromCompletedCycle(SITE, OWNER, completed(3, Set.of(0, 1, 2)));
        assertEquals(0, zero.quantity());
        assertTrue(zero.lots().isEmpty(), "zero yield cannot be represented by a fake zero-count lot");

        var one = ResourceFieldYield.fromCompletedCycle(SITE, OWNER, completed(1, Set.of()));
        assertEquals(List.of(1), one.lots().stream().map(ResourceLot::quantity).toList());
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldYield.fromCompletedCycle(
                new SubjectId("site:foreign"), OWNER, completed(1, Set.of())),
                "a geometrically matching field cannot credit another site's yield");

        var partial = ResourceFieldYield.fromCompletedCycle(SITE, OWNER, completed(64, Set.of(0)));
        assertEquals(63, partial.quantity());
        assertEquals(List.of(63), partial.lots().stream().map(ResourceLot::quantity).toList());
        assertTrue(partial.lots().getFirst().provenance().contains("site:1-wheat-field:epoch-1"));

        var full = ResourceFieldYield.fromCompletedCycle(SITE, OWNER, completed(64, Set.of()));
        assertEquals(List.of(64), full.lots().stream().map(ResourceLot::quantity).toList());

        var multi = ResourceFieldYield.fromCompletedCycle(SITE, OWNER, completed(129, Set.of()));
        assertEquals(129, multi.workedCells());
        assertEquals(List.of(64, 64, 1), multi.lots().stream().map(ResourceLot::quantity).toList());
        assertEquals(multi, ResourceFieldYield.fromCompletedCycle(SITE, OWNER, completed(129, Set.of())),
                "recovery of the same accounted epoch must derive the same output identities");
        assertEquals(multi.lots().getFirst().id(), partial.lots().getFirst().id(),
                "competing outcomes of one field epoch must collide on the same output identity");
        assertNotEquals(multi.lots().getFirst(), partial.lots().getFirst(),
                "the collision must expose a changed quantity or provenance, not silently accept it");
        var sameLayoutFull = ResourceFieldYield.fromCompletedCycle(SITE, OWNER, completed(65, Set.of()));
        var sameLayoutPartial = ResourceFieldYield.fromCompletedCycle(SITE, OWNER, completed(65, Set.of(0, 1)));
        assertEquals(sameLayoutFull.lots().getFirst().provenance(), sameLayoutPartial.lots().getFirst().provenance(),
                "a carried lot must not change its source identity as its observed quantity grows");
        assertNotEquals(sameLayoutFull.lots().getFirst(), sameLayoutPartial.lots().getFirst(),
                "different quantities of one identified part remain conflicting results");
        var first = partial.lots().getFirst();
        FungibleResourceLedger credited = FungibleResourceLedger.empty().issue(first,
                new CustodyAccount(new SubjectId("custody:first-field-yield"),
                        new ResourceCustody.Container(new SubjectId("container:first-field-yield")),
                        Map.of(first.id(), first.quantity()), Map.of()));
        var conflicting = multi.lots().getFirst();
        assertThrows(IllegalArgumentException.class, () -> credited.issue(conflicting,
                new CustodyAccount(new SubjectId("custody:second-field-yield"),
                        new ResourceCustody.Container(new SubjectId("container:second-field-yield")),
                        Map.of(conflicting.id(), conflicting.quantity()), Map.of())),
                "a contradictory second positive outcome for the same epoch cannot create another lot");
    }

    @Test void incompleteCycleOrUnresolvedPlayerActionCannotEnterTheYieldBoundary() {
        ResourceFieldCycle unworked = ResourceFieldCycle.seeded(SITE, layout(2), 1);
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldYield.fromCompletedCycle(SITE, OWNER, unworked));
        ResourceFieldCycle complete = completed(1, Set.of());
        var id = complete.layout().cells().getFirst().id();
        ResourceFieldCycle pending = complete.preparePlayerBreak(id, new ResourceFieldCycle.PendingPlayerBreak(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000125"), "player:pending-yield",
                ResourceFieldPhysicalSurface.Condition.of(complete.cell(id))));
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldYield.fromCompletedCycle(SITE, OWNER, pending));
    }

    @Test void boundedLotsCloseAtYieldCountAndMatchTheFinalAccountedCycle() {
        ResourceFieldCycle first = workedPrefix(130, 64, Set.of());
        ResourceLot part0 = ResourceFieldYield.nextReadyLot(SITE, OWNER, first, 64, 0).orElseThrow();
        assertEquals(64, part0.quantity());
        ResourceFieldCycle second = workedPrefix(130, 128, Set.of());
        ResourceLot part1 = ResourceFieldYield.nextReadyLot(SITE, OWNER, second, 128, 64).orElseThrow();
        assertEquals(64, part1.quantity());
        ResourceFieldCycle terminal = workedPrefix(130, 130, Set.of());
        ResourceLot part2 = ResourceFieldYield.nextReadyLot(SITE, OWNER, terminal, 130, 128).orElseThrow();
        assertEquals(2, part2.quantity());
        assertEquals(List.of(part0, part1, part2), ResourceFieldYield.fromCompletedCycle(SITE, OWNER, terminal).lots(),
                "a final review must derive precisely the lots already issued at bounded work prefixes");
        assertTrue(ResourceFieldYield.nextReadyLot(SITE, OWNER, terminal, 130, 130).isEmpty(),
                "a delivered terminal partial part must not reappear on a later review");
        assertThrows(IllegalArgumentException.class,
                () -> ResourceFieldYield.nextReadyLot(SITE, OWNER, terminal, 130, 129),
                "a nonterminal partial issuance cannot skip the retained lot boundary");
        assertThrows(IllegalArgumentException.class,
                () -> ResourceFieldYield.nextReadyLot(SITE, OWNER, terminal, 130, 128 + 64),
                "the API must not silently accept a forged later issuance count");
    }

    @Test void readyColdActorPartMovesOnlyItsExactYieldIntoTheDeclaredDepot() {
        ResourceFieldCycle cycle = completed(65, Set.of(0));
        ResourceLot part = ResourceFieldYield.nextReadyLot(SITE, OWNER, cycle, 65, 0).orElseThrow();
        SubjectId farmer = new SubjectId("resident:field-delivery");
        SubjectId actorAccount = new SubjectId("custody:field-delivery-actor");
        SubjectId depotAccount = new SubjectId("custody:field-delivery-depot");
        SubjectId depot = FrontierWorldState.depotId(OWNER);
        ResourceLot earlier = new ResourceLot(new SubjectId("lot:earlier-field-wheat"), OWNER,
                "minecraft:wheat", 5, "field:earlier:epoch-0", List.of());
        FungibleResourceLedger resources = FungibleResourceLedger.empty()
                .issue(earlier, new CustodyAccount(depotAccount, new ResourceCustody.Container(depot),
                        Map.of(earlier.id(), 5), Map.of()))
                .issue(part, new CustodyAccount(actorAccount, new ResourceCustody.Actor(farmer),
                        Map.of(part.id(), part.quantity()), Map.of()))
                .reserve(new ClaimAllocation(new SubjectId("claim:earlier-field-wheat"),
                        new SubjectId("job:earlier-bread"), OWNER, "minecraft:wheat", 2,
                        Map.of(earlier.id(), 2), ClaimPurpose.PRODUCTION_WORK), depotAccount);
        FungibleResourceLedger delivered = resources.deliverColdActorHarvestPart(cycle, OWNER, 0,
                actorAccount, farmer, depotAccount);
        assertEquals(null, delivered.accounts().get(actorAccount));
        assertEquals(Map.of(earlier.id(), 5, part.id(), 64), delivered.accounts().get(depotAccount).lotQuantities());
        assertEquals(Map.of(new SubjectId("claim:earlier-field-wheat"), 2),
                delivered.accounts().get(depotAccount).claimQuantities(),
                "incoming wheat cannot erase a different job's retained depot reservation");
        assertEquals(69, delivered.totalQuantity(OWNER, "minecraft:wheat"));
        assertThrows(IllegalArgumentException.class, () -> resources.deliverColdActorHarvestPart(cycle,
                OWNER, 0, actorAccount, new SubjectId("resident:foreign"), depotAccount));
        assertThrows(IllegalArgumentException.class, () -> resources.deliverColdActorHarvestPart(cycle,
                OWNER, 1, actorAccount, farmer, depotAccount));
        assertThrows(IllegalArgumentException.class, () -> delivered.deliverColdActorHarvestPart(cycle,
                OWNER, 0, actorAccount, farmer, depotAccount), "one ready part cannot be delivered twice");
        assertThrows(IllegalArgumentException.class, () -> resources.transfer(actorAccount, depotAccount,
                Map.of(part.id(), 64), Map.of()), "generic COLD transfer may not bypass the field owner");
    }

    @Test void readyHotActorPartRequiresItsExactBodyAndOneObservedDepotLayout() {
        ResourceFieldCycle cycle = completed(1, Set.of());
        ResourceLot part = ResourceFieldYield.nextReadyLot(SITE, OWNER, cycle, 1, 0).orElseThrow();
        SubjectId farmer = new SubjectId("resident:hot-field-delivery");
        SubjectId actorAccount = new SubjectId("custody:hot-field-delivery-actor");
        SubjectId depotAccount = new SubjectId("custody:hot-field-delivery-depot");
        SubjectId depot = FrontierWorldState.depotId(OWNER);
        java.util.UUID body = java.util.UUID.fromString("00000000-0000-0000-0000-000000000888");
        FungibleResourceLedger unbound = FungibleResourceLedger.empty().issue(part,
                new CustodyAccount(actorAccount, new ResourceCustody.Actor(farmer),
                        Map.of(part.id(), 1), Map.of()));
        FungibleResourceLedger held = unbound.rebind(actorAccount, 4,
                FungiblePhysicalObservation.bind(unbound, actorAccount, 4, List.of(
                        new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(farmer, body),
                                "minecraft:wheat", 1))));
        var depotBinding = new PhysicalStackBinding(new SubjectId("binding:hot-field-delivery"), depotAccount,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)),
                5, "minecraft:wheat", Map.of(part.id(), 1), Map.of());
        FungibleResourceLedger delivered = held.deliverObservedActorHarvestPart(cycle, OWNER, 0,
                actorAccount, farmer, body, depotAccount, 4, 5, List.of(depotBinding));
        assertEquals(null, delivered.accounts().get(actorAccount));
        assertEquals(Map.of(part.id(), 1), delivered.accounts().get(depotAccount).lotQuantities());
        assertEquals(depotBinding, delivered.bindings().get(depotBinding.id()));
        FungibleResourceLedger observedStacks = held.deliverObservedActorHarvestStacks(cycle, OWNER, 0,
                actorAccount, farmer, body, depotAccount, 4, 5, List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)),
                        "minecraft:wheat", 1)));
        assertEquals(Map.of(part.id(), 1), observedStacks.accounts().get(depotAccount).lotQuantities());
        assertEquals(1, observedStacks.bindings().size());
        assertThrows(IllegalArgumentException.class, () -> held.deliverObservedActorHarvestStacks(cycle, OWNER, 0,
                actorAccount, farmer, body, depotAccount, 4, 5, List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)),
                        "minecraft:wheat", 2))), "invented chest stock cannot clear the farmer's hand");
        assertThrows(IllegalArgumentException.class, () -> held.deliverObservedActorHarvestPart(cycle, OWNER, 0,
                actorAccount, farmer, java.util.UUID.fromString("00000000-0000-0000-0000-000000000889"),
                depotAccount, 4, 5, List.of(depotBinding)));
        assertThrows(IllegalArgumentException.class, () -> held.deliverObservedActorHarvestPart(cycle, OWNER, 0,
                actorAccount, farmer, body, depotAccount, 4, 5, List.of()),
                "a missing chest postcondition cannot release the actor hand");
        assertThrows(IllegalArgumentException.class, () -> held.transferObservedToNewAccount(actorAccount,
                new CustodyAccount(depotAccount, new ResourceCustody.Container(depot), Map.of(part.id(), 1), Map.of()),
                4, 5, Map.of(part.id(), 1), Map.of(), List.of(), List.of(depotBinding)),
                "generic HOT transfer may not bypass the observed field handoff");
    }

    @Test void hotFieldDeliveryPreservesTheExistingFencedDepotLayout() {
        ResourceFieldCycle cycle = completed(1, Set.of());
        ResourceLot part = ResourceFieldYield.nextReadyLot(SITE, OWNER, cycle, 1, 0).orElseThrow();
        ResourceLot earlier = new ResourceLot(new SubjectId("lot:prior-hot-depot-wheat"), OWNER,
                "minecraft:wheat", 5, "field:prior:epoch-0", List.of());
        SubjectId farmer = new SubjectId("resident:hot-depot-return");
        SubjectId actorAccount = new SubjectId("custody:hot-depot-return-actor");
        SubjectId depotAccount = new SubjectId("custody:hot-depot-return-depot");
        SubjectId depot = FrontierWorldState.depotId(OWNER);
        java.util.UUID body = java.util.UUID.fromString("00000000-0000-0000-0000-000000000890");
        FungibleResourceLedger issued = FungibleResourceLedger.empty()
                .issue(earlier, new CustodyAccount(depotAccount, new ResourceCustody.Container(depot),
                        Map.of(earlier.id(), 5), Map.of()))
                .issue(part, new CustodyAccount(actorAccount, new ResourceCustody.Actor(farmer),
                        Map.of(part.id(), 1), Map.of()));
        FungibleResourceLedger depotBound = issued.rebind(depotAccount, 5,
                FungiblePhysicalObservation.bind(issued, depotAccount, 5, List.of(
                        new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                                new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 5))));
        FungibleResourceLedger bothBound = depotBound.rebind(actorAccount, 4,
                FungiblePhysicalObservation.bind(depotBound, actorAccount, 4, List.of(
                        new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(farmer, body),
                                "minecraft:wheat", 1))));
        PhysicalStackBinding retained = bothBound.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(depotAccount)).findFirst().orElseThrow();
        PhysicalStackBinding arrival = new PhysicalStackBinding(new SubjectId("binding:hot-depot-return-arrival"),
                depotAccount, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 1)),
                5, "minecraft:wheat", Map.of(part.id(), 1), Map.of());
        FungibleResourceLedger delivered = bothBound.deliverObservedActorHarvestPart(cycle, OWNER, 0,
                actorAccount, farmer, body, depotAccount, 4, 5, List.of(retained, arrival));
        assertEquals(Map.of(earlier.id(), 5, part.id(), 1), delivered.accounts().get(depotAccount).lotQuantities());
        assertEquals(Map.of(retained.id(), retained, arrival.id(), arrival), delivered.bindings());
    }

    @Test void theCurrentCarriedPartGrowsByObservedYieldWithoutChangingIdentity() {
        ResourceFieldCycle one = workedPrefix(66, 1, Set.of());
        ResourceLot first = ResourceFieldYield.currentCarriedLot(SITE, OWNER, one, 1, 0).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldYield.currentCarriedLot(
                new SubjectId("site:foreign"), OWNER, one, 1, 0));
        ResourceFieldCycle two = workedPrefix(66, 2, Set.of());
        ResourceLot second = ResourceFieldYield.currentCarriedLot(SITE, OWNER, two, 2, 0).orElseThrow();
        assertEquals(first.id(), second.id());
        assertEquals(first.provenance(), second.provenance());
        assertEquals(1, first.quantity());
        assertEquals(2, second.quantity());
        assertTrue(ResourceFieldYield.nextReadyLot(SITE, OWNER, two, 2, 0).isEmpty(),
                "a carried part is not ready for depot delivery after only two actual yields");
        ResourceFieldCycle oneLost = workedPrefix(66, 1, Set.of(0));
        assertTrue(ResourceFieldYield.currentCarriedLot(SITE, OWNER, oneLost, 1, 0).isEmpty(),
                "a lost crop cannot appear in the farmer's canonical cargo");
    }

    @Test void aLostCropDelaysTheFullBatchUntilTheSixtyFourthActualYield() {
        ResourceFieldCycle before = workedPrefix(65, 64, Set.of(0));
        assertTrue(ResourceFieldYield.nextReadyLot(SITE, OWNER, before, 64, 0).isEmpty());
        ResourceFieldCycle after = workedPrefix(65, 65, Set.of(0));
        ResourceLot ready = ResourceFieldYield.nextReadyLot(SITE, OWNER, after, 65, 0).orElseThrow();
        assertEquals(64, ready.quantity());
        assertEquals(ready, ResourceFieldYield.fromCompletedCycle(SITE, OWNER, after).lots().getFirst());
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldYield.nextReadyLot(SITE, OWNER, after, 64, 0),
                "a cursor cannot omit a worked field cell");
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldYield.nextReadyLot(SITE, OWNER, after, 65, 1),
                "only already-receipted full batches may precede the next lot");
        ResourceFieldCycle pending = after.preparePlayerBreak(after.layout().cells().get(0).id(),
                new ResourceFieldCycle.PendingPlayerBreak(
                        java.util.UUID.fromString("00000000-0000-0000-0000-000000000126"), "player:pending-batch",
                        ResourceFieldPhysicalSurface.Condition.of(after.cell(after.layout().cells().get(0).id()))));
        assertThrows(IllegalArgumentException.class, () -> ResourceFieldYield.nextReadyLot(SITE, OWNER, pending, 65, 0));
    }

    @Test void outOfOrderAreaWorkEarnsOnlyItsActualYieldAndSurvivesRecovery() {
        ResourceFieldCycle cycle = ResourceFieldCycle.seeded(SITE, layout(65), 1);
        for (int stage = 0; stage < 7; stage++) cycle = cycle.advanceGrowthStage();
        var first = cycle.layout().cells().getFirst().id();
        var second = cycle.layout().cells().get(1).id();
        cycle = cycle.worked(second);
        assertEquals(0, cycle.accountedPrefixCount());
        ResourceFieldCycle gap = cycle;
        assertEquals(1, ResourceFieldYield.currentCarriedLot(SITE, OWNER, gap, 1, 0).orElseThrow().quantity());
        assertTrue(ResourceFieldYield.nextReadyLot(SITE, OWNER, gap, 1, 0).isEmpty());
        cycle = cycle.worked(first);
        assertEquals(2, cycle.accountedPrefixCount());
        for (int index = 2; index < 64; index++) cycle = cycle.worked(cycle.layout().cells().get(index).id());
        assertEquals(64, cycle.accountedPrefixCount());
        var last = cycle.layout().cells().get(64).id();
        cycle = cycle.preparePlayerBreak(last, new ResourceFieldCycle.PendingPlayerBreak(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000127"), "player:pending-next-batch",
                ResourceFieldPhysicalSurface.Condition.of(cycle.cell(last))));
        ResourceFieldCycle restored = ResourceFieldCycle.restore(SITE, cycle.layout(), cycle.epoch(),
                cycle.cellStates(), cycle.pendingPlayerBreaks());
        assertEquals(64, restored.accountedPrefixCount());
        assertEquals(ResourceFieldYield.nextReadyLot(SITE, OWNER, cycle, 64, 0),
                ResourceFieldYield.nextReadyLot(SITE, OWNER, restored, 64, 0));
        assertEquals(64, ResourceFieldYield.nextReadyLot(SITE, OWNER, restored, 64, 0).orElseThrow().quantity(),
                "a pending action beyond the sealed prefix must not block the already earned batch");
    }

    private static ResourceFieldCycle completed(int count, Set<Integer> removed) {
        return workedPrefix(count, count, removed);
    }

    private static ResourceFieldCycle workedPrefix(int count, int worked, Set<Integer> removed) {
        var cycle = ResourceFieldCycle.seeded(SITE, layout(count), 1);
        for (int stage = 0; stage < 7; stage++) cycle = cycle.advanceGrowthStage();
        for (int index : removed) cycle = cycle.cropRemoved(cycle.layout().cells().get(index).id());
        for (int index = 0; index < worked; index++) cycle = cycle.worked(cycle.layout().cells().get(index).id());
        return cycle;
    }

    private static ResourceFieldLayout layout(int count) {
        var cells = new ArrayList<ResourceFieldLayout.Cell>();
        for (int index = 0; index < count; index++) {
            var station = SurfaceAnchor.at((index % 17) * 2, 63 + index % 3, (index / 17) * 2);
            cells.add(new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(index + 1L),
                    station.support().offset(0, 1, 0), station, station));
        }
        return new ResourceFieldLayout(1, count + 1L, cells, List.of());
    }
}
