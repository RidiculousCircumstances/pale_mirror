package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Source commercial policy; stock/title, transport and physical effects keep their own owners. */
public final class GoodsTradeSourceAdmission {
    private GoodsTradeSourceAdmission() { }

    public static int protectedMinimum(FrontierWorldState state, GoodsTradeParty party,
                                       SubjectId container, String itemKind) {
        GoodsParticipant participant = state.companies().goodsTrade().participants().participants().get(party.id());
        if (participant == null || !participant.party().equals(party)
                || !participant.endpoint().containerId().equals(container))
            throw new IllegalArgumentException("source policy requires its declared participant and endpoint");
        var stock = GoodsParticipantView.read(state, participant).stocks().get(itemKind);
        return stock == null ? 0 : stock.protectedMinimum();
    }

    public static boolean dispatchFunded(FrontierWorldState state, GoodsParticipant seller, SubjectId receiver) {
        return seller.endpoint().containerId().equals(receiver)
                || state.inventory().economics().availableToReserve(seller.endpoint().settlementId())
                    .compareTo(state.bootstrap().ruleset().expedition().replenishmentBudget()) >= 0;
    }

    /** Reassess only the promises still at source, not cargo already delegated to transport. */
    public static Optional<GoodsTradeDisposition.Reason> withdrawalReason(FrontierWorldState state,
                                                                         GoodsTradeContract contract) {
        var seller = state.companies().goodsTrade().participants().participants().get(contract.seller().id());
        int minimum = protectedMinimum(state, contract.seller(), contract.sourceContainerId(), contract.itemKind());
        var account = FungibleResourceCustodySupport.accountAtContainer(state, contract.sourceContainerId());
        int free = account.map(value -> state.inventory().fungibleResources().unclaimedQuantity(
                value.id(), contract.seller().id(), contract.itemKind())).orElse(0);
        if (free < minimum) return Optional.of(GoodsTradeDisposition.Reason.LOCAL_RESERVE_REQUIRED);
        if (!dispatchFunded(state, seller, contract.receiverContainerId()))
            return Optional.of(GoodsTradeDisposition.Reason.DISPATCH_UNFUNDED);
        return Optional.empty();
    }
}
