package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Source commercial policy; stock/title, transport and physical effects keep their own owners. */
public final class GoodsTradeSourceAdmission {
    private GoodsTradeSourceAdmission() { }

    public static int protectedMinimum(FrontierWorldState state, GoodsTradeParty party, String itemKind, long atTick) {
        GoodsParticipant participant = state.companies().goodsTrade().participants().participants().get(party.id());
        if (participant == null || !participant.party().equals(party))
            throw new IllegalArgumentException("source policy requires its declared economic participant");
        var stock = GoodsParticipantAssessment.domestic(state, participant, atTick).view().stocks().get(itemKind);
        return stock == null ? 0 : stock.protectedMinimum();
    }

    /** Reassess only the promises still at source, not cargo already delegated to transport. */
    public static Optional<GoodsTradeDisposition.Reason> withdrawalReason(FrontierWorldState state,
                                                                         GoodsTradeContract contract, long atTick) {
        int minimum = protectedMinimum(state, contract.seller(), contract.itemKind(), atTick);
        var participant = state.companies().goodsTrade().participants().participants().get(contract.seller().id());
        int free = GoodsParticipantStorage.unclaimed(state, participant, contract.itemKind());
        if (free < minimum) return Optional.of(GoodsTradeDisposition.Reason.LOCAL_RESERVE_REQUIRED);
        return Optional.empty();
    }
}
