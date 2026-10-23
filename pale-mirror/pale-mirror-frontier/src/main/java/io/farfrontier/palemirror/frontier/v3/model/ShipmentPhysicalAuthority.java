package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Prepared transport effects retain endpoint authority independently of a later body handoff. */
public final class ShipmentPhysicalAuthority {
    public static boolean pendingForContainer(FrontierWorldState state, SubjectId container) {
        return state.shipments().shipments().values().stream().anyMatch(shipment -> shipment.pendingPhysicalStep().isPresent()
                && (ShipmentServiceAccess.point(shipment).filter(container::equals).isPresent()
                    || shipment.mobileContainerId().equals(Optional.of(container))));
    }
    public static List<ContainerSlotClaim> slotClaims(FrontierWorldState state) {
        return slotClaims(state.shipments());
    }
    public static List<ContainerSlotClaim> slotClaims(ShipmentState shipments) {
        var result = new ArrayList<ContainerSlotClaim>();
        for (Shipment shipment : shipments.shipments().values()) {
            var step = shipment.pendingPhysicalStep().orElse(null);
            if (step == null) continue;
            if (shipment.carrying())
                result.add(new ContainerSlotClaim(new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.SHIPMENT, shipment.id()), new InventoryCustody.ContainerSlot(
                        shipment.unloadingEndpoint().containerId(), step.destinationSlot()),
                        ShipmentAuthorizationComposition.capacityCompletionOwner(shipment).map(owner -> new ContainerInboundCapacity.Demand(
                                owner, shipment.unloadingEndpoint().containerId(), shipment.itemKind(), step.lotQuantities().values().stream().mapToInt(Integer::intValue).sum(),
                                shipment.cargo() instanceof ShipmentCargo.Individual ? ContainerInboundCapacity.Representation.INDIVIDUAL
                                        : ContainerInboundCapacity.Representation.FUNGIBLE))));
            else if (shipment.status() == Shipment.Status.AWAITING_LOAD && shipment.mobileContainerId().isPresent())
                result.add(new ContainerSlotClaim(new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.SHIPMENT, shipment.id()), new InventoryCustody.ContainerSlot(
                        shipment.mobileContainerId().orElseThrow(), step.destinationSlot())));
        }
        return List.copyOf(result);
    }
    private ShipmentPhysicalAuthority() { }
}
