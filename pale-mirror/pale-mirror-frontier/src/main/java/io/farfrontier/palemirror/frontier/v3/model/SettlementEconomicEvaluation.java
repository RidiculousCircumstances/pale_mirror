package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlanner;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Explicit owner-invoked calculation. Never called by stock/eligibility/diagnostic reads. */
public final class SettlementEconomicEvaluation {
    private static final EconomicPlanner ECONOMY = new EconomicPlanner();
    private SettlementEconomicEvaluation() { }
    public static GoodsParticipantAssessment.Assessment domestic(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        var observed = GoodsParticipantView.observe(state, participant);
        if (participant.policy() != GoodsPolicyKind.PUBLIC_SETTLEMENT
                || !state.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled())
            return new GoodsParticipantAssessment.Assessment(atTick, GoodsParticipantView.planned(state, observed, Optional.empty()), Optional.empty(), Optional.empty());
        var facts = SettlementSupplyPlanning.collect(state, observed, atTick);
        var economicRules = state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics();
        Optional<EconomicPlanner.Domestic> domestic = economicRules.map(rules ->
                ECONOMY.domestic(SettlementEconomicPlanning.facts(state, facts), rules));
        var plan = domestic.map(EconomicPlanner.Domestic::plan).orElseGet(() -> {
            var supply = new SupplyPlanner().plan(facts.stocks(), facts.capacities(), facts.commitments(), facts.market());
            return new SupplyPlanner.Plan(supply.demands(), facts.reserves(), supply.preferredPurchaseSpending(),
                    supply.preferredPurchaseQuantities(), supply.inputRequirements(), supply.economy(), supply.backedRecipes());
        });
        return new GoodsParticipantAssessment.Assessment(atTick, GoodsParticipantView.planned(state, observed, Optional.of(plan)), Optional.of(facts), domestic);
    }
    public record Completed(GoodsParticipantAssessment.Assessment assessment, List<ConditionalProductionQuote> quotes) {
        public Completed { quotes = List.copyOf(quotes); }
    }
    public static Completed complete(FrontierWorldState state, GoodsParticipantAssessment.Assessment domestic,
            long inputRevision, long planRevision, long expiresAt) {
        if (domestic.domestic().isEmpty()) return new Completed(domestic, List.of());
        var advice = SettlementEconomicPlanning.exportAdvice(state, domestic.facts().orElseThrow(), false,
                ECONOMY.potentialExports(domestic.domestic().orElseThrow()), true);
        var reviewed = ECONOMY.withConditionalQuotes(domestic.domestic().orElseThrow(),
                advice.consented().stream().map(SettlementEconomicPlanning.QuoteCandidate::interest).toList(),
                advice.counters().stream().map(SettlementEconomicPlanning.QuoteCandidate::interest).toList());
        var supplier = domestic.view().participant(); var quotes = new ArrayList<ConditionalProductionQuote>();
        long horizon = reviewed.plan().economy().orElseThrow().horizonTicks();
        for (var candidate : advice.all()) {
            int quantity = Math.toIntExact(reviewed.priceableQuantities().getOrDefault(candidate.interest(), 0L));
            if (quantity == 0) continue;
            var freight = quantity == candidate.freight().quantity() ? Optional.of(candidate.freight())
                    : GoodsDeliveryAdvice.read(state, supplier, candidate.buyer(), candidate.interest().item(), quantity,
                            candidate.interest().unitPrice(), candidate.buyer().availableMoney().raw(),
                            GoodsDeliveryAdvice.Supply.CONDITIONAL_PRODUCTION, domestic.atTick());
            if (freight.isEmpty()) continue;
            var value = freight.orElseThrow();
            quotes.add(new ConditionalProductionQuote(supplier.party(), candidate.buyer().participant().party(),
                    candidate.interest().item(), value.quantity(), candidate.interest().unitPrice(), value.tripExpense(),
                    value.cashDelivery(), value.fulfillment(), horizon, value.deliveryTicks(), inputRevision,
                    planRevision, domestic.atTick(), expiresAt));
        }
        var assessment = new GoodsParticipantAssessment.Assessment(domestic.atTick(), GoodsParticipantView.planned(state,
                GoodsParticipantView.observe(state, supplier), Optional.of(reviewed.plan())), domestic.facts(), domestic.domestic());
        return new Completed(assessment, quotes);
    }
}
