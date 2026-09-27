package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorContainerItemOrderTest {
    private final SubjectId owner = new SubjectId("service:repair-1");
    private final SubjectId actor = new SubjectId("resident:1-3");
    private final InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 4);
    private final ActorContainerItemOrder.ContainerEndpoint.ExactSlot endpoint =
            new ActorContainerItemOrder.ContainerEndpoint.ExactSlot(slot);
    private final SurfaceAnchor station = SurfaceAnchor.at(11, 65, 12);

    @Test
    void exactTakeKeepsItsDeclaredSourceActorAndSemanticGoal() {
        ExactItemStack wheat = new ExactItemStack(new SubjectId("item:wheat-1"), new SubjectId("settlement:1"),
                "minecraft:wheat", 12, slot);
        ActorContainerItemOrder order = new ActorContainerItemOrder(owner, actor, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Exact(wheat), endpoint, station, ActorContainerItemOrder.Hand.OFF, 2L, 7L);
        assertEquals(actor, order.movementOrder().actorId());
        assertTrue(order.movementOrder().arrivedAt(station));
        assertEquals(12, order.portion().quantity());
        assertThrows(IllegalArgumentException.class, () -> new ActorContainerItemOrder(owner, actor,
                ActorContainerItemOrder.Direction.TAKE, new ActorContainerItemOrder.Portion.Exact(wheat),
                new ActorContainerItemOrder.ContainerEndpoint.ExactSlot(new InventoryCustody.ContainerSlot(slot.containerId(), 5)),
                station, ActorContainerItemOrder.Hand.OFF, 2L, 7L));
        assertThrows(IllegalArgumentException.class, () -> new ActorContainerItemOrder(owner, actor,
                ActorContainerItemOrder.Direction.PLACE, new ActorContainerItemOrder.Portion.Exact(wheat),
                endpoint, station, ActorContainerItemOrder.Hand.OFF, 2L, 7L));
    }

    @Test
    void outputPlaceNeedsThatSameActorsCanonicalCustody() {
        ExactItemStack bread = new ExactItemStack(new SubjectId("item:bread-1"), new SubjectId("settlement:1"),
                "minecraft:bread", 12, new InventoryCustody.Actor(actor));
        ActorContainerItemOrder order = new ActorContainerItemOrder(owner, actor, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Exact(bread), endpoint, station, ActorContainerItemOrder.Hand.OFF, 3L, 7L);
        assertEquals(ActorContainerItemOrder.Direction.PLACE, order.direction());
        assertThrows(IllegalArgumentException.class, () -> new ActorContainerItemOrder(owner, new SubjectId("resident:1-4"),
                ActorContainerItemOrder.Direction.PLACE, new ActorContainerItemOrder.Portion.Exact(bread),
                endpoint, station, ActorContainerItemOrder.Hand.OFF, 3L, 7L));
    }

    @Test
    void fungiblePortionIsOneBoundedExplicitAllocation() {
        ActorContainerItemOrder.Portion.Fungible portion = new ActorContainerItemOrder.Portion.Fungible(
                new SubjectId("custody:depot-1"), new ResourceCustody.Container(slot.containerId()),
                new SubjectId("custody:worker-1"), new ResourceCustody.Actor(actor),
                Optional.of(new SubjectId("claim:bread-1")), "minecraft:wheat",
                Map.of(new SubjectId("lot:wheat-1"), 8, new SubjectId("lot:wheat-2"), 5));
        assertEquals(13, portion.quantity());
        assertThrows(IllegalArgumentException.class, () -> new ActorContainerItemOrder.Portion.Fungible(
                portion.sourceAccountId(), portion.sourceCustody(), portion.destinationAccountId(), portion.destinationCustody(),
                portion.claimId(), portion.itemKind(), Map.of(new SubjectId("lot:wheat-1"), 65)));
        ActorContainerItemOrder take = new ActorContainerItemOrder(owner, actor, ActorContainerItemOrder.Direction.TAKE,
                portion, new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(slot.containerId()), station,
                ActorContainerItemOrder.Hand.MAIN, 0L, 1L);
        assertEquals(ActorContainerItemOrder.Direction.TAKE, take.direction());
        assertThrows(IllegalArgumentException.class, () -> new ActorContainerItemOrder(owner, actor,
                ActorContainerItemOrder.Direction.PLACE, portion, take.containerEndpoint(), station,
                ActorContainerItemOrder.Hand.MAIN, 0L, 1L));
    }
}
