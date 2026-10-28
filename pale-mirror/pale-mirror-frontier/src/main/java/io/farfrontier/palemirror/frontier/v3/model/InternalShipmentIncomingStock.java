package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Derived incoming same-owner inventory, not a balance or a promise to manufacture a resource. */
final class InternalShipmentIncomingStock {
    private record Scope(SubjectId owner, SubjectId container, String itemKind) { }
    private static final ImmutableGroupView<SubjectId, Shipment, Scope> INCOMING = new ImmutableGroupView<>();
    private static java.util.Set<Scope> membership(Shipment shipment) {
        var grant = shipment.authorization();
        return grant.kind() == ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT
                || grant.kind() == ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT
                ? java.util.Set.of(new Scope(grant.claimantId(), shipment.receiver().containerId(), shipment.itemKind())) : java.util.Set.of();
    }
    static int quantity(ShipmentState state, SubjectId owner, SubjectId container, String itemKind) {
        int quantity = 0;
        for (var shipment : INCOMING.rows(InternalShipmentIncomingStock.class, state.shipments(),
                InternalShipmentIncomingStock::membership, new Scope(owner, container, itemKind))) {
            if (!shipment.terminal()) quantity = Math.addExact(quantity, shipment.quantity());
            // Unloaded-but-unaccepted stock is still reserved; count it once until its exact claim is released.
            if (shipment.reception().isPresent()) quantity = Math.addExact(quantity, shipment.reception().orElseThrow().quantity());
        }
        return quantity;
    }
    private InternalShipmentIncomingStock() { }
}
