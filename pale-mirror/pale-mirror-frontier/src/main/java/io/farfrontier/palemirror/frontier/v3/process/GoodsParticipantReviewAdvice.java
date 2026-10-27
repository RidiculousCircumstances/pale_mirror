package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketPricePolicy;
import java.util.*;

/** Economic owner of disposable forecasts. Advice contains neither reservations,
 * actor assignments nor transactions. Exact trade execution starts from current state. */
final class GoodsParticipantReviewAdvice {
    private record Peer(GoodsTradeParty party, GoodsPolicyKind policy, SubjectId settlement,
            List<GoodsParticipant.Counterparty> known, Map<String, MarketPricePolicy.Quote> prices,
            long inputRevision, long planRevision) { }
    static ScheduledAdvisory<FrontierWorldState> capture(FrontierWorldState state, ScheduledAction action, SimInstant at) {
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(action.subject()), "economic advice owner");
        var basis = peers(state, participant); var bootstrap = state.bootstrap();
        var navigation = state.navigationGeometry(); var physical = state.physicalDeltas();
        return () -> {
            var prepared = SettlementEconomicPlanPreparation.review(state, action, at);
            return (current, executionAt) -> {
                if (current.bootstrap() != bootstrap
                        || !participant.equals(current.companies().goodsTrade().participants().participants().get(action.subject()))
                        || current.navigationGeometry() != navigation || current.physicalDeltas() != physical
                        || !basis.equals(peers(current, participant)) || executionAt.compareTo(at) < 0
                        || executionAt.ticks() >= prepared.publication().map(SettlementEconomicPlan::reviewAtTick)
                                .orElse(Math.addExact(at.ticks(), bootstrap.ruleset().goodsTrade().reviewInterval())))
                    return Optional.empty();
                return Optional.of(() -> GoodsParticipantProcess.prepare(current, action, executionAt, prepared));
            };
        };
    }
    private static Map<SubjectId, Peer> peers(FrontierWorldState state, GoodsParticipant owner) {
        var registry = state.companies().goodsTrade().participants();
        var result = new TreeMap<SubjectId, Peer>();
        var ids = new TreeSet<SubjectId>(); ids.add(owner.party().id()); owner.known().forEach(peer -> ids.add(peer.party().id()));
        for (var id : ids) {
            var participant = registry.participants().get(id);
            if (participant == null) continue; // Presence/absence is part of this exact declared-peer map.
            var input = registry.economicState(id);
            result.put(id, new Peer(participant.party(), participant.policy(), participant.settlementId(), participant.known(), participant.prices(),
                    input.inputRevision(), input.accepted().map(SettlementEconomicPlan::revision).orElse(0L)));
        }
        return Map.copyOf(result);
    }
    private GoodsParticipantReviewAdvice() { }
}
