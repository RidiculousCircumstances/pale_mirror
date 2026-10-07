package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Physical resource changes release the affected promise and money, never award buyer title or delivery. */
final class GoodsTradeForfeitureOwner implements FungibleClaimForfeitureOwner {
    @Override public void validate(FrontierWorldState before, ClaimAllocation claim, FungibleResourceHandoffObserved observation) {
        GoodsTradeContract contract = require(before, claim);
        ShipmentStateSupport.requireSourceWithdrawal(before, claim.id());
        if (before.physicalIntents().values().stream().anyMatch(i -> i.causeSubjectId().equals(contract.id()))) {
            throw new IllegalArgumentException("goods allocation change cannot bypass an unresolved physical effect");
        }
        // The resource handoff independently checks the exact old/new physical layout and holder.
        // Possibly applied shipment effects retain their own obligation independently.
    }
    @Override public FungibleForfeitureSettlement settle(FrontierWorldState before, java.util.List<ClaimAllocation> claims,
                                                        FungibleForfeitureSettlement transaction) {
        for (var claim : claims) {
        var original = require(before, claim);
        GoodsTradeContract contract = transaction.companies().goodsTrade().contracts().get(original.id());
        if (transaction.inventory().fungibleResources().claims().containsKey(claim.id())) {
            throw new IllegalArgumentException("commercial forfeiture requires the accounted physical release first");
        }
        String identity = contract.id().value() + "\n" + contract.revision() + "\n" + claim.id().value();
        SubjectId receipt = new SubjectId("receipt:goods-change-" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)));
        var disposition = new GoodsTradeDisposition(receipt, contract.id(), contract.revision(), claim.id(), claim.quantity(),
                GoodsTradeDisposition.Reason.OBSERVED_ALLOCATION_CHANGED);
        var trade = transaction.companies().goodsTrade().dispose(disposition);
        var economics = transaction.inventory().economics().releasePortion(contract.financialReservationId(),
                contract.deliveredUnitPrice().multiply(claim.quantity()));
        transaction = transaction.withTrade(transaction.inventory().withEconomics(economics), transaction.companies().withGoodsTrade(trade));
        transaction = ShipmentStateSupport.withdrawSourceClaim(before, claim.id(), transaction);
        }
        return transaction;
    }
    private static GoodsTradeContract require(FrontierWorldState state, ClaimAllocation claim) {
        GoodsTradeContract contract = state.companies().goodsTrade().contracts().get(claim.claimantId());
        if (claim.purpose() != ClaimPurpose.GOODS_TRADE || contract == null
                || !claim.economicOwnerId().equals(contract.seller().id()) || !claim.itemKind().equals(contract.itemKind())
                || contract.outstandingClaims().getOrDefault(claim.id(), 0) != claim.quantity()) {
            throw new IllegalArgumentException("physical goods change lost its exact commercial allocation");
        }
        return contract;
    }
}
