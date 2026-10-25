package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.market.FundedCargoLot;
import java.util.*;

/** Reads peer-owned accepted estimates only. Never evaluates a peer economy,
 * creates an offer of stored goods or supplies an arrival/production authority. */
public final class ConditionalSupplyPricing {
    record Estimate(ConditionalProductionQuote quote, int quantity, long resourceUnitExpense, long cashUnitExpense) { }
    static Optional<Estimate> best(FrontierWorldState state, GoodsParticipant buyer, String item,
            long requested, long cash, long now) {
        if (requested < 1 || cash < 1) return Optional.empty();
        Estimate best = null;
        for (var quote : currentQuotes(state, buyer, now)) {
            if (!quote.itemKind().equals(item)) continue;
            int quantity = FundedCargoLot.quantity((int) Math.min(requested, quote.quantity()), quote.goodsUnitPrice(), quote.cashFreight(), cash);
            if (quantity == 0) continue;
            var estimate = new Estimate(quote, quantity, quote.resourceUnitExpense(quantity), quote.cashUnitExpense(quantity));
            if (best == null || estimate.resourceUnitExpense() < best.resourceUnitExpense()
                    || estimate.resourceUnitExpense() == best.resourceUnitExpense() && quantity > best.quantity()) best = estimate;
        }
        return Optional.ofNullable(best);
    }
    /** Read-only diagnostic/advice projection; no source assessment or valuation. */
    public static List<ConditionalProductionQuote> currentQuotes(FrontierWorldState state, GoodsParticipant buyer, long now) {
        var registry = state.companies().goodsTrade().participants(); var result = new ArrayList<ConditionalProductionQuote>();
        for (var known : buyer.known()) {
            var source = registry.participants().get(known.party().id());
            if (source == null || !source.party().equals(known.party())) continue;
            var planning = registry.economicState(source.party().id()); var accepted = planning.accepted();
            if (accepted.isEmpty()) continue;
            var plan = accepted.orElseThrow();
            if (planning.changes().contains(SettlementEconomicPlanningState.Change.CASH)) continue;
            var blocked = planning.invalidatedProduction(plan.complete().demands().keySet());
            for (var quote : plan.conditionalQuotes()) {
                if (!quote.supplier().equals(source.party()) || quote.sourcePlanRevision() != plan.revision()
                        || quote.sourceInputRevision() != plan.inputRevision())
                    throw new IllegalArgumentException("conditional quote has a foreign accepted supplier generation");
                if (!quote.recipient().equals(buyer.party()) || blocked.contains(quote.itemKind())
                        || now < quote.assessedAtTick() || now >= quote.expiresAtTick()) continue;
                result.add(quote);
            }
        }
        return List.copyOf(result);
    }
    private ConditionalSupplyPricing() { }
}
