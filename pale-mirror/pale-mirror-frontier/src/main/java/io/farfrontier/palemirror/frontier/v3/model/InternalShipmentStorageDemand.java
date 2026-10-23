package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Local hauling owns its receiving promise; generic storage only composes this read-only demand. */
final class InternalShipmentStorageDemand {
    private InternalShipmentStorageDemand() { }
    static List<ContainerInboundCapacity.Demand> pending(FrontierWorldState state) {
        return state.shipments().shipments().values().stream()
                .filter(shipment -> !shipment.terminal() && (shipment.authorization().kind() == ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT || shipment.authorization().kind() == ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT))
                .map(shipment -> new ContainerInboundCapacity.Demand(shipment.id(), shipment.receiver().containerId(), shipment.itemKind(), shipment.quantity(),
                        shipment.cargo() instanceof ShipmentCargo.Individual ? ContainerInboundCapacity.Representation.INDIVIDUAL
                                : ContainerInboundCapacity.Representation.FUNGIBLE))
                .toList();
    }
}
