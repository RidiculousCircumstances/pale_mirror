package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Local hauling owns its receiving promise; generic storage only composes this read-only demand. */
final class InternalShipmentStorageDemand {
    private InternalShipmentStorageDemand() { }
    private static final ImmutableKeyedInputView<SubjectId, List<ContainerInboundCapacity.Demand>> VIEW = new ImmutableKeyedInputView<>(512);
    static List<ContainerInboundCapacity.Demand> pending(FrontierWorldState state, SubjectId container) {
        var shipments = state.shipments().receiving(container);
        return VIEW.get(state.bootstrap(), container, List.of(shipments), () -> shipments.stream()
                .filter(shipment -> !shipment.terminal() && (shipment.authorization().kind() == ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT || shipment.authorization().kind() == ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT))
                .map(shipment -> new ContainerInboundCapacity.Demand(shipment.id(), shipment.receiver().containerId(), shipment.itemKind(), shipment.quantity(),
                        shipment.cargo() instanceof ShipmentCargo.Individual ? ContainerInboundCapacity.Representation.INDIVIDUAL
                                : ContainerInboundCapacity.Representation.FUNGIBLE, ((ResourceCustody.Container) shipment.receiver().custody()).section()))
                .toList());
    }
}
