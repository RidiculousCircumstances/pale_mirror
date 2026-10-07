package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialSourceSelectionTest {
    private static final SubjectId OWNER = new SubjectId("settlement:selection");
    private static final SubjectId CONTAINER = new SubjectId("container:selection");
    private static final SubjectId ACCOUNT = new SubjectId("custody:selection");
    private static final SubjectId LOT = new SubjectId("lot:selection");
    private static final SubjectId CLAIM = new SubjectId("claim:selection");

    @Test
    void claimedBatchFollowsCurrentSplitBindingsInsteadOfOneSavedSlot() {
        ResourceLot lot = new ResourceLot(LOT, OWNER, "minecraft:wheat", 64, "field", List.of());
        ClaimAllocation claim = new ClaimAllocation(CLAIM, new SubjectId("job:selection"), OWNER,
                lot.itemKind(), 32, Map.of(LOT, 32), ClaimPurpose.PRODUCTION_WORK);
        FungibleResourceLedger cold = new FungibleResourceLedger(Map.of(LOT, lot), Map.of(CLAIM, claim),
                Map.of(ACCOUNT, new CustodyAccount(ACCOUNT, new ResourceCustody.Container(CONTAINER),
                        Map.of(LOT, 64), Map.of(CLAIM, 32))), Map.of());
        FungibleResourceLedger hot = cold.rebind(ACCOUNT, 1L, FungiblePhysicalObservation.bind(cold, ACCOUNT, 1L,
                List.of(stack(12, 24), stack(3, 40))));

        List<MaterialSourceSelection.Slice> selected = MaterialSourceSelection.select(hot, ACCOUNT,
                lot.itemKind(), 32, Optional.of(CLAIM));
        assertEquals(32, selected.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum());
        assertEquals(2, selected.size());
        assertEquals(java.util.Set.of(3, 12), selected.stream().map(slice ->
                ((PhysicalStackAddress.ContainerSlot) slice.address()).slot().slot()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1L, selected.getFirst().epoch());
        assertThrows(IllegalArgumentException.class, () -> MaterialSourceSelection.select(hot, ACCOUNT,
                lot.itemKind(), 33, Optional.of(CLAIM)));
        assertThrows(IllegalArgumentException.class, () -> MaterialSourceSelection.select(hot, ACCOUNT,
                "minecraft:bread", 32, Optional.of(CLAIM)));
    }

    @Test
    void unclaimedOrderSelectsOnlyItsDeclaredLotsFromLargerRearrangedStock() {
        SubjectId otherLot = new SubjectId("lot:selection-other");
        ResourceLot wheat = new ResourceLot(LOT, OWNER, "minecraft:wheat", 40, "field", List.of());
        ResourceLot other = new ResourceLot(otherLot, OWNER, "minecraft:wheat", 24, "field", List.of());
        FungibleResourceLedger cold = new FungibleResourceLedger(Map.of(LOT, wheat, otherLot, other), Map.of(),
                Map.of(ACCOUNT, new CustodyAccount(ACCOUNT, new ResourceCustody.Container(CONTAINER),
                        Map.of(LOT, 40, otherLot, 24), Map.of())), Map.of());
        FungibleResourceLedger hot = cold.rebind(ACCOUNT, 1L, FungiblePhysicalObservation.bind(cold, ACCOUNT, 1L,
                List.of(stack(12, 24), stack(3, 40))));
        SubjectId actor = new SubjectId("resident:selection");
        ActorContainerItemOrder order = new ActorContainerItemOrder(new SubjectId("job:selection"), actor,
                ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(ACCOUNT, new ResourceCustody.Container(CONTAINER),
                        new SubjectId("custody:selection-hand"), new ResourceCustody.Actor(actor), Optional.empty(),
                        wheat.itemKind(), Map.of(LOT, 20)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(CONTAINER),
                SurfaceAnchor.at(0, 64, 0), ActorContainerItemOrder.Hand.MAIN, 1, 1);
        List<MaterialSourceSelection.Slice> selected = MaterialSourceSelection.select(hot, order);
        assertEquals(20, selected.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum());
        assertEquals(1, selected.size());
        assertEquals(24, selected.getFirst().before());
        assertEquals(20, selected.getFirst().moved());
    }

    private static FungiblePhysicalObservation.Stack stack(int slot, int quantity) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(CONTAINER, slot)), "minecraft:wheat", quantity);
    }

    @Test
    void newEarlierPinnedReservationCannotMoveAPreparedMealsSource() {
        var lot = new ResourceLot(LOT, OWNER, "minecraft:wheat", 128, "stock", List.of());
        var cold = new FungibleResourceLedger(Map.of(LOT, lot), Map.of(),
                Map.of(ACCOUNT, new CustodyAccount(ACCOUNT, new ResourceCustody.Container(CONTAINER),
                        Map.of(LOT, 128), Map.of())), Map.of());
        var hot = cold.rebind(ACCOUNT, 16L, FungiblePhysicalObservation.bind(cold, ACCOUNT, 16L,
                List.of(stack(0, 64), stack(1, 64))));
        var meal = new ClaimAllocation(new SubjectId("claim:z-meal"), new SubjectId("resident:selection"),
                OWNER, lot.itemKind(), 1, Map.of(LOT, 1), ClaimPurpose.EXTERNAL_RESERVATION);
        var reserved = hot.reserveBound(meal, ACCOUNT, 16L);
        var prepared = MaterialSourceSelection.select(reserved, ACCOUNT, lot.itemKind(), 1, Optional.of(meal.id()));
        var shipment = new ClaimAllocation(new SubjectId("claim:a-shipment"), new SubjectId("shipment:selection"),
                OWNER, lot.itemKind(), 64, Map.of(LOT, 64), ClaimPurpose.EXTERNAL_RESERVATION);
        var extended = reserved.reserveBound(shipment, ACCOUNT, 16L);
        assertEquals(prepared, MaterialSourceSelection.select(extended, ACCOUNT, lot.itemKind(), 1, Optional.of(meal.id())));
        assertEquals(64, MaterialSourceSelection.select(extended, ACCOUNT, lot.itemKind(), 64,
                Optional.of(shipment.id())).stream().mapToInt(MaterialSourceSelection.Slice::moved).sum());
        assertEquals(reserved.accounts().get(ACCOUNT).lotQuantities(), extended.accounts().get(ACCOUNT).lotQuantities());
    }
}
