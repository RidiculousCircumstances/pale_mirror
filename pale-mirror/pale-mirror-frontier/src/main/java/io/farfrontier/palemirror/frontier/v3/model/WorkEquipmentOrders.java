package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Exact equipment handoffs reuse ordinary actor item orders, storage admission and navigation. */
public final class WorkEquipmentOrders {
    public static ActorContainerItemOrder create(FrontierWorldState state, SubjectId workOwner, SubjectId actor,
            ExactItemStack item, ShipmentEndpoint endpoint, ActorContainerItemOrder.Direction direction,
            Optional<InventoryCustody.ContainerSlot> retainedSlot, long ordinal, long revision) {
        if (!item.equals(state.inventory().items().get(item.id())) || !item.economicOwnerId().equals(endpoint.settlementId())
                || item.count() != 1 || !item.condition().durable())
            throw new IllegalArgumentException("equipment transfer lost its exact owned individual item");
        InventoryCustody.ContainerSlot slot;
        if (direction == ActorContainerItemOrder.Direction.TAKE) {
            if (!(item.custody() instanceof InventoryCustody.ContainerSlot stored)
                    || !stored.containerId().equals(endpoint.containerId()))
                throw new IllegalArgumentException("equipment left its admitted source");
            slot = stored;
        } else {
            if (!item.custody().equals(new InventoryCustody.Actor(actor)))
                throw new IllegalArgumentException("equipment return lacks the declared holder");
            slot = retainedSlot.orElseGet(() -> new InventoryCustody.ContainerSlot(endpoint.containerId(),
                    ContainerStorageAdmission.first(state, endpoint.containerId(), Optional.empty(), held -> false)
                        .orElseThrow(() -> new IllegalArgumentException("equipment return storage is full or reserved"))));
        }
        if (!slot.containerId().equals(endpoint.containerId()) || retainedSlot.filter(value -> !value.equals(slot)).isPresent())
            throw new IllegalArgumentException("equipment handoff has a changed source/destination declaration");
        var order = new ActorContainerItemOrder(workOwner, actor, direction, new ActorContainerItemOrder.Portion.Exact(item),
                new ActorContainerItemOrder.ContainerEndpoint.ExactSlot(slot), endpoint.station(), ActorContainerItemOrder.Hand.MAIN,
                ordinal, revision);
        ActorItemCustody.requireEndpointPolicy(state, order);
        return order;
    }
    private WorkEquipmentOrders() { }
}
