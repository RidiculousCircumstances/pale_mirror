package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorCarriedResourcesTest {
    @Test void consumingFoodDoesNotConsumeOrRebindCarriedOre() {
        SubjectId actor = new SubjectId("resident:carrier");
        SubjectId economy = new SubjectId("settlement:owner");
        SubjectId cargo = new SubjectId("custody:ore"), ore = new SubjectId("lot:ore");
        SubjectId depot = new SubjectId("container:depot"), foodSource = new SubjectId("custody:food-source");
        SubjectId food = new SubjectId("custody:food"), bread = new SubjectId("lot:bread");
        UUID body = UUID.fromString("00000000-0000-0000-0000-000000000154");
        FungibleResourceLedger ledger = FungibleResourceLedger.empty()
                .issue(new ResourceLot(ore, economy, "minecraft:iron_ingot", 37, "test:ore", List.of()),
                        new CustodyAccount(cargo, new ResourceCustody.Actor(actor), Map.of(ore, 37), Map.of()))
                .issue(new ResourceLot(bread, economy, "minecraft:bread", 8, "test:bread", List.of()),
                        new CustodyAccount(foodSource, new ResourceCustody.Container(depot), Map.of(bread, 8), Map.of()));
        ledger = ledger.rebind(cargo, 4, FungiblePhysicalObservation.bind(ledger, cargo, 4,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(actor, body), "minecraft:iron_ingot", 37))));
        PhysicalStackAddress sourceSlot = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 5));
        ledger = ledger.rebind(foodSource, 7, FungiblePhysicalObservation.bind(ledger, foodSource, 7,
                List.of(new FungiblePhysicalObservation.Stack(sourceSlot, "minecraft:bread", 8))));
        var originalCargo = ledger.accounts().get(cargo);
        var originalBinding = ledger.bindings().values().stream().filter(value -> value.accountId().equals(cargo)).findFirst().orElseThrow();
        ActorContainerItemOrder take = new ActorContainerItemOrder(actor, actor, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(foodSource, new ResourceCustody.Container(depot),
                        food, new ResourceCustody.Actor(actor), Optional.empty(), "minecraft:bread", Map.of(bread, 2)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(depot), SurfaceAnchor.at(0, 64, 0),
                new ActorItemSlot.Pocket(0), 1, 1);
        var destination = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(actor, body, 0), "minecraft:bread", 2);
        var before = ledger;
        assertThrows(IllegalArgumentException.class, () -> before.transferActorOrderObservedStacks(take, 7, 9,
                List.of(new FungiblePhysicalObservation.Stack(sourceSlot, "minecraft:bread", 6)),
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(actor, body, 1), "minecraft:bread", 2))));
        ledger = ledger.transferActorOrderObservedStacks(take, 7, 9,
                List.of(new FungiblePhysicalObservation.Stack(sourceSlot, "minecraft:bread", 6)), List.of(destination));
        assertEquals(2, ActorCarriedResources.accounts(ledger, actor).size());
        SubjectId claim = new SubjectId("claim:food");
        ledger = ledger.reserveBound(new ClaimAllocation(claim, actor, economy, "minecraft:bread", 2,
                Map.of(bread, 2), ClaimPurpose.RESIDENT_MEAL), food, 9);
        ledger = ledger.destroyObserved(food, 9, Map.of(bread, 2), Map.of(claim, 2), List.of());
        assertEquals(originalCargo, ledger.accounts().get(cargo));
        assertEquals(originalBinding, ledger.bindings().get(originalBinding.id()));
        assertEquals(37, ledger.totalQuantity(economy, "minecraft:iron_ingot"));
        assertEquals(6, ledger.totalQuantity(economy, "minecraft:bread"));
        assertFalse(ledger.accounts().containsKey(food));
    }
}
