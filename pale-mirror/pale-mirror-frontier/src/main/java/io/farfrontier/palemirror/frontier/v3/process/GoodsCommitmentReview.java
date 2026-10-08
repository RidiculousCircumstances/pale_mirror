package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Bounded source-side withdrawal, before publishing more quotes or dispatching retained promises. */
final class GoodsCommitmentReview {
    private static final int MAX_RELEASES_PER_REVIEW = 64;
    record Result(FrontierWorldState state, List<ProposedEvent> events) { }
    static Result plan(FrontierWorldState state, GoodsParticipant participant) {
        var events = new ArrayList<ProposedEvent>();
        var contracts = state.companies().goodsTrade().contracts().values().stream()
                .filter(value -> !value.terminal() && value.seller().equals(participant.party()))
                .sorted(Comparator.comparing(GoodsTradeContract::id)).toList();
        for (var original : contracts) {
            for (var claim : original.outstandingClaims().keySet().stream().sorted().toList()) {
                if (events.size() == MAX_RELEASES_PER_REVIEW) return new Result(state, List.copyOf(events));
                var current = state.companies().goodsTrade().contracts().get(original.id());
                var reason = GoodsTradeSourceAdmission.withdrawalReason(state, current);
                if (reason.isEmpty()) break;
                if (!GoodsTradeStateSupport.canCancelBeforeLoading(state, current, claim)) continue;
                var receipt = new SubjectId("receipt:goods-withdraw/" + WorkOpportunityIdentity.digest(
                        current.id().value() + "|" + current.revision() + "|" + claim.value()));
                var disposition = new GoodsTradeDisposition(receipt, current.id(), current.revision(), claim,
                        current.outstandingClaims().get(claim), reason.orElseThrow());
                state = GoodsTradeStateSupport.cancelBeforeLoading(state, participant.party().id(), disposition);
                events.add(new ProposedEvent(participant.party().id(), new GoodsTradeCancelled(disposition)));
            }
        }
        return new Result(state, List.copyOf(events));
    }
    private GoodsCommitmentReview() { }
}
