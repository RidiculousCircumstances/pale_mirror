package io.farfrontier.palemirror.frontier.v3.model;

/** Recognized source withdrawal releases only its internal promise, not title or treasury. */
final class InternalShipmentForfeitureOwner implements FungibleClaimForfeitureOwner {
    @Override public void validate(FrontierWorldState state, ClaimAllocation claim, FungibleResourceHandoffObserved observed) {
        if (claim.purpose() != ClaimPurpose.INTERNAL_LOGISTICS || !claim.claimantId().equals(claim.economicOwnerId()))
            throw new IllegalArgumentException("internal withdrawal has a foreign issuer");
        ShipmentStateSupport.requireSourceWithdrawal(state, claim.id());
    }
    @Override public FungibleForfeitureSettlement settle(FrontierWorldState state, java.util.List<ClaimAllocation> claims,
                                                        FungibleForfeitureSettlement transaction) {
        for (var claim : claims) {
            if (transaction.inventory().fungibleResources().claims().containsKey(claim.id()))
                throw new IllegalArgumentException("internal withdrawal precedes accounted resource loss");
            transaction = ShipmentStateSupport.withdrawSourceClaim(state, claim.id(), transaction);
            for (var shipment : transaction.shipments().shipments().values()) {
                if (shipment.authorization().kind() == ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT
                        && shipment.reception().filter(receipt -> receipt.claimId().equals(claim.id())).isPresent())
                    transaction = transaction.withShipments(transaction.shipments().replace(shipment,
                            shipment.acknowledged(shipment.reception().orElseThrow().id())), transaction.executions(), transaction.movements());
            }
        }
        return transaction;
    }
}
