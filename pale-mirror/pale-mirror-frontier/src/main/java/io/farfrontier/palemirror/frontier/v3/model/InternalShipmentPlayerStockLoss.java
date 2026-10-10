package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Internal logistics withdraws an unpicked promise, never an in-flight physical effect. */
final class InternalShipmentPlayerStockLoss implements PlayerStockClaimLossOwner {
    @Override public ClaimPurpose purpose() { return ClaimPurpose.INTERNAL_LOGISTICS; }
    @Override public void validate(FrontierWorldState state, SubjectId source, ClaimAllocation claim) {
        var account = state.inventory().fungibleResources().accounts().get(source);
        if (claim.purpose() != purpose() || !claim.claimantId().equals(claim.economicOwnerId())
                || account == null || !account.claimQuantities().containsKey(claim.id()))
            throw new IllegalArgumentException("local haul source loss has no exact issuing account");
        ShipmentStateSupport.requireSourceWithdrawal(state, claim.id());
    }
    @Override public Settlement settle(FrontierWorldState state, ClaimAllocation claim, Settlement transaction) {
        return new Settlement(new InternalShipmentForfeitureOwner().settle(state, List.of(claim), transaction.resources()),
                transaction.population());
    }
    @Override public List<ReleasedActivity> released(FrontierWorldState before, ClaimAllocation claim) {
        return before.shipments().shipments().values().stream().filter(shipment -> !shipment.terminal()
                && shipment.authorization().claimId().equals(claim.id()))
                .map(shipment -> new ReleasedActivity(shipment.execution().actorId(), Optional.empty(), OptionalLong.empty())).toList();
    }
}
