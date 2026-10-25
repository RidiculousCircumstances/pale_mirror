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
        for (var entry : plan.peerVersions().entrySet()) {
            var accepted = registry.economicState(entry.getKey()).accepted();
            if (accepted.map(SettlementEconomicPlan::revision).orElse(0L).longValue() != entry.getValue())
                throw new IllegalArgumentException("economic publication uses a stale buyer envelope");
        }
        requireQuoteEnvelope(registry, participant, plan, state.bootstrap().ruleset().goodsTrade());
        return state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(
                registry.withEconomicState(subject, registry.economicState(subject).accept(plan)))));
    }
    /** The same nominal tuple is validated at publication and snapshot hydration.
     * Ordinary queries never scan the planning registry or recalculate policy. */
    public static void requireQuoteEnvelope(GoodsParticipantState registry, GoodsParticipant participant, SettlementEconomicPlan plan, GoodsTradeRules rules) {
        if (plan.conditionalQuotes().isEmpty()) return;
        var catalog = rules.policies().get(participant.policy()).stream().map(GoodsTradeRules.Commodity::itemKind)
                .collect(java.util.stream.Collectors.toSet());
        var known = participant.known().stream().map(GoodsParticipant.Counterparty::party)
                .collect(java.util.stream.Collectors.toSet());
        for (var quote : plan.conditionalQuotes()) {
            var recipient = registry.participants().get(quote.recipient().id());
            if (!quote.supplier().equals(participant.party()) || recipient == null
                    || !recipient.party().equals(quote.recipient()) || !known.contains(quote.recipient()))
                throw new IllegalArgumentException("conditional quote owner mismatch: publisher=" + participant.party() + " quote=" + quote.key());
            if (!catalog.contains(quote.itemKind()) || quote.quantity() > Math.multiplyExact(rules.maximumBatch(), rules.supplyPlanning().maximumTripCargoStacks())
                    || quote.expiresAtTick() > Math.addExact(plan.atTick(), Math.multiplyExact(2, rules.reviewInterval())))
                throw new IllegalArgumentException("conditional quote exceeds catalog/freshness bounds: " + quote.key());
        }
    }
    private SettlementEconomicPublication() { }
}
