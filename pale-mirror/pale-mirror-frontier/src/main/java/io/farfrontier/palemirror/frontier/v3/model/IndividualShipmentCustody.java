package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Individual-stock adapter for the same transport workflow and actor item protocol. */
final class IndividualShipmentCustody {
    private IndividualShipmentCustody() { }
    static ActorContainerItemOrder order(FrontierWorldState state, Shipment shipment, ShipmentCargo.Individual cargo,
                                         Map<SubjectId, Integer> portions) {
        if (shipment.terminal() || shipment.reception().isPresent() || !cargo.portions().equals(portions))
            throw new IllegalArgumentException("individual transport order has no entire current cargo");
        var item = state.inventory().items().get(cargo.itemId());
        if (!cargo.matches(item)) throw new IllegalArgumentException("transported individual changed outside its custody protocol");
        boolean take = shipment.status() == Shipment.Status.AWAITING_LOAD;
        var retained = shipment.pendingPhysicalStep().flatMap(ShipmentPhysicalStep::individual);
        if (retained.filter(witness -> !witness.item().equals(item)).isPresent())
            throw new IllegalArgumentException("prepared individual transfer lost its preimage");
        InventoryCustody.ContainerSlot endpoint;
        if (retained.isPresent()) endpoint = retained.orElseThrow().endpoint();
        else if (take) {
            if (!(item.custody() instanceof InventoryCustody.ContainerSlot source)
                    || !shipment.sender().container().equals(Optional.of(source.containerId())))
                throw new IllegalArgumentException("individual shipment source is no longer at its declared warehouse");
            endpoint = source;
        } else {
            int slot = ContainerStorageAdmission.first(state, shipment.unloadingEndpoint().containerId(),
                    ShipmentAuthorizationComposition.capacityCompletionOwner(shipment), ignored -> false).orElseThrow(
                        () -> new IllegalArgumentException("individual shipment receiver has no unreserved slot"));
            endpoint = new InventoryCustody.ContainerSlot(shipment.unloadingEndpoint().containerId(), slot);
        }
        Optional<InventoryCustody.ContainerSlot> attached = retained.isPresent() ? retained.orElseThrow().attached() : shipment.mobileContainerId().map(container -> {
            if (!take && item.custody() instanceof InventoryCustody.ContainerSlot current && current.containerId().equals(container)) return current;
            return new InventoryCustody.ContainerSlot(container, ContainerStorageAdmission.first(state, container,
                    Optional.empty(), ignored -> false).orElseThrow(() -> new IllegalArgumentException("individual cargo has no attached slot")));
        });
        var actorSlot = shipment.mobileContainerId().<ActorItemSlot>map(ActorItemSlot.AttachedStorage::new)
                .orElseGet(() -> new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN));
        return new ActorContainerItemOrder(shipment.id(), shipment.execution().actorId(),
                take ? ActorContainerItemOrder.Direction.TAKE : ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Exact(item, attached, Optional.of(shipment.authorization())),
                new ActorContainerItemOrder.ContainerEndpoint.ExactSlot(endpoint),
                take ? shipment.sender().station() : shipment.unloadingEndpoint().station(), actorSlot, take ? 0 : 1, shipment.revision());
    }
    static boolean fits(FrontierWorldState state, Shipment shipment) {
        boolean take = shipment.status() == Shipment.Status.AWAITING_LOAD;
        if (take && shipment.mobileContainerId().isEmpty()) return state.inventory().actorItems(shipment.execution().actorId()).isEmpty();
        var container = take ? shipment.mobileContainerId().orElseThrow() : shipment.unloadingEndpoint().containerId();
        return ContainerStorageAdmission.receiveIndividuals(state, container, shipment.itemKind(), 1,
                take ? Optional.empty() : ShipmentAuthorizationComposition.capacityCompletionOwner(shipment));
    }
    static void validateCustody(FrontierWorldState state, Shipment shipment, ShipmentCargo.Individual cargo) {
        var item = state.inventory().items().get(cargo.itemId());
        var expected = shipment.status() == Shipment.Status.AWAITING_LOAD ? shipment.sender().custody() : shipment.carriedCustody();
        boolean matches = item != null && switch (expected) {
            case ResourceCustody.Container container -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(container.containerId());
            case ResourceCustody.Actor actor -> item.custody().equals(new InventoryCustody.Actor(actor.actorId()));
            default -> false;
        };
        if (!cargo.matches(item) || !matches) throw new IllegalArgumentException("live individual shipment lost its exact title, condition or custody");
    }
}
