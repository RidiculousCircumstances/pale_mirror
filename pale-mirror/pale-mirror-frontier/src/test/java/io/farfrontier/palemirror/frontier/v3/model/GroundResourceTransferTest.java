package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroundResourceTransferTest {
    private final SubjectId owner = new SubjectId("settlement:1"), lot = new SubjectId("lot:ground-stone"),
            source = new SubjectId("custody:ground-stone"), actor = new SubjectId("resident:1-1"),
            job = new SubjectId("shipment:ground-stone"), destination = new SubjectId("custody:ground-hauler");
    private final UUID entity = UUID.fromString("244352e2-4b65-4213-85f2-d59f953dccd3");
    private final ResourceCustody.Ground ground = new ResourceCustody.Ground(entity, new BlockPosition(1, 65, 1));
    private FungibleResourceLedger ledger() {
        var base = FungibleResourceLedger.empty().issue(new ResourceLot(lot, owner, "minecraft:cobblestone", 10, "fixture:observed-drop", List.of()),
                new CustodyAccount(source, ground, Map.of(lot, 10), Map.of()));
        return base.rebind(source, 3, FungiblePhysicalObservation.bind(base, source, 3,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(entity), "minecraft:cobblestone", 10))));
    }
    private ActorGroundItemOrder order(int quantity, long epoch) {
        return new ActorGroundItemOrder(job, actor, new ActorContainerItemOrder.Portion.Fungible(source, ground,
                destination, new ResourceCustody.Actor(actor), Optional.empty(), "minecraft:cobblestone", Map.of(lot, quantity)),
                entity, epoch, SurfaceAnchor.at(1, 64, 1), new ActorItemSlot.Pocket(0), 0, 1);
    }
    @Test void partialGroundPickupUsesSameLotLedgerAndPreservesTitleAndRemainingEntity() {
        var before = ledger(); var order = order(4, 3);
        assertEquals(new MaterialSourceSelection.Slice(new PhysicalStackAddress.WorldEntity(entity), 10, 4, 3),
                MaterialSourceSelection.select(before, order).getFirst());
        var after = before.transferActorOrderObservedStacks(order, 3, 7,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(entity), "minecraft:cobblestone", 6)),
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(actor, UUID.randomUUID(), 0), "minecraft:cobblestone", 4)));
        assertEquals(Map.of(lot, 6), after.accounts().get(source).lotQuantities());
        assertEquals(Map.of(lot, 4), after.accounts().get(destination).lotQuantities());
        assertEquals(10, after.lots().get(lot).quantity()); assertEquals(owner, after.lots().get(lot).economicOwnerId());
        assertEquals(Optional.of(new ActorItemSlot.Pocket(0)), after.accounts().get(destination).actorPresentation());
    }
    @Test void staleEpochForeignEntityOrReservedPortionCannotFundPickup() {
        var ledger = ledger(); var sourceAfter = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(entity), "minecraft:cobblestone", 6));
        var destinationAfter = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(actor, UUID.randomUUID(), 0), "minecraft:cobblestone", 4));
        assertThrows(IllegalArgumentException.class, () -> ledger.transferActorOrderObservedStacks(order(4, 2), 3, 7, sourceAfter, destinationAfter));
        assertThrows(IllegalArgumentException.class, () -> ledger.transferActorOrderObservedStacks(order(4, 3), 3, 7,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(UUID.randomUUID()), "minecraft:cobblestone", 6)), destinationAfter));
        var reserved = ledger.reserveBound(new ClaimAllocation(new SubjectId("claim:other"), owner, owner, "minecraft:cobblestone", 7,
                Map.of(lot, 7), ClaimPurpose.INTERNAL_LOGISTICS), source, 3);
        assertThrows(IllegalArgumentException.class, () -> MaterialSourceSelection.select(reserved, order(4, 3)));
        assertThrows(IllegalStateException.class, () -> ledger.transferActorOrderCold(order(4, 3)), "native custody cannot be spent in COLD");
        assertThrows(IllegalArgumentException.class, () -> new ActorGroundItemOrder(job, actor, order(4, 3).portion(), UUID.randomUUID(), 3,
                SurfaceAnchor.at(1, 64, 1), new ActorItemSlot.Pocket(0), 0, 1));
    }
    @Test void groundCustodyIsAnExplicitCurrentFormatValueAndSurvivesWorldSnapshot() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ground-custody"), 20260918065L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
        var resources = state.inventory().fungibleResources().issue(ledger().lots().get(lot), ledger().accounts().get(source));
        resources = resources.rebind(source, 3, FungiblePhysicalObservation.bind(resources, source, 3,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(entity), "minecraft:cobblestone", 10))));
        var updated = state.withInventory(state.inventory().withFungibleResources(resources)); var codec = new FrontierWorldStateCodec();
        assertEquals(updated, codec.decode(codec.encode(updated)));
        assertNotEquals(ground, new ResourceCustody.WorldCarrier(entity));
    }
}
