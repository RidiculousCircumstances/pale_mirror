package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Derived incoming same-owner inventory, not a balance or a promise to manufacture a resource. */
final class InternalShipmentIncomingStock {
    static int quantity(ShipmentState state, SubjectId owner, SubjectId container, String itemKind) {
        int quantity = 0;
        for (var shipment : state.receiving(container, itemKind)) {
            var grant = shipment.authorization();
            if ((grant.kind() != ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT
                    && grant.kind() != ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT)
                    || !grant.claimantId().equals(owner)) continue;
            if (!shipment.terminal()) quantity = Math.addExact(quantity, shipment.quantity());
            // Unloaded-but-unaccepted stock is still reserved; count it once until its exact claim is released.
            if (shipment.reception().isPresent()) quantity = Math.addExact(quantity, shipment.reception().orElseThrow().quantity());
        }
        return quantity;
    }
    private InternalShipmentIncomingStock() { }
}
