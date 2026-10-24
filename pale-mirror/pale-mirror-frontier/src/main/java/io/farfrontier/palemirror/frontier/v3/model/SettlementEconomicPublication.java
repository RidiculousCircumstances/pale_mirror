package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Acceptance checks the economic envelope. Replay never calculates policy. */
public final class SettlementEconomicPublication {
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, SettlementEconomicPlan plan, long now) {
        var trade = state.companies().goodsTrade(); var registry = trade.participants();
        var participant = Objects.requireNonNull(registry.participants().get(subject), "economic publication owner");
        var catalog = state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy()).stream()
                .map(GoodsTradeRules.Commodity::itemKind).collect(java.util.stream.Collectors.toSet());
        if (plan.atTick() > now || !plan.complete().demands().keySet().equals(catalog))
            throw new IllegalArgumentException("economic publication has foreign catalog/time");
        for (var entry : plan.buyerVersions().entrySet()) {
            var accepted = registry.economicState(entry.getKey()).accepted();
            if (accepted.map(SettlementEconomicPlan::revision).orElse(0L).longValue() != entry.getValue())
                throw new IllegalArgumentException("economic publication uses a stale buyer envelope");
        }
        return state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(
                registry.withEconomicState(subject, registry.economicState(subject).accept(plan)))));
    }
    private SettlementEconomicPublication() { }
}
