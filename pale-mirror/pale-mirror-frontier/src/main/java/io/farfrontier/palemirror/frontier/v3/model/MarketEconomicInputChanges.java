package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.model.SettlementEconomicPlanningState.Change.*;

/** Only the parties named by a quote/contract are affected. A technical review is not a market change. */
final class MarketEconomicInputChanges implements EconomicInputChangePort {
    @Override public void collect(FrontierWorldState before, FrontierWorldState after, Sink sink) {
        var a = before.companies().goodsTrade(); var b = after.companies().goodsTrade(); if (a == b) return;
        EconomicInputChangePort.difference(a.orders(), b.orders(), (old, next) -> {
            if (old != null) quote(old, sink); if (next != null) quote(next, sink);
        });
        EconomicInputChangePort.difference(a.contracts(), b.contracts(), (old, next) -> {
            if (old != null) contract(old, sink); if (next != null) contract(next, sink);
        });
        if (a.participants().planning() != b.participants().planning()) for (var entry : b.participants().planning().entrySet()) {
            var current = entry.getValue().accepted();
            var prior = a.participants().planning().getOrDefault(entry.getKey(), SettlementEconomicPlanningState.empty()).accepted();
            if (current.orElse(null) == prior.orElse(null)) continue;
            if (current.isPresent()) changedCounterquotes(prior.orElse(null), current.orElseThrow(), sink);
            if (current.isEmpty() || sameBuyerDemand(prior.orElse(null), current.orElseThrow())) continue;
            for (var order : b.orders().values()) if (order.side() == GoodsTradeOrder.Side.BUY
                    && order.party().id().equals(entry.getKey()) && order.availableQuantity() > 0)
                sink.resource(order.counterparty().id(), MARKET, order.itemKind());
        }
        if (a.priceHistory() != b.priceHistory()) {
            var changed = new HashSet<String>();
            b.priceHistory().commodities().forEach((item, history) -> {
                if (!history.equals(a.priceHistory().commodities().get(item))) changed.add(item);
            });
            if (!changed.isEmpty()) for (var party : b.participants().participants().values())
                sink.changed(party.party().id(), MARKET, changed);
        }
        EconomicInputChangePort.difference(a.participants().participants(), b.participants().participants(), (old, next) -> {
            if (old != null && next != null && old.prices().equals(next.prices()) && old.known().equals(next.known())) return;
            if (next != null) sink.changed(next.party().id(), MARKET, Set.of());
        });
    }
    private record QuoteTerms(int quantity, long goods, long resources, long cash,
                              GoodsTradeFulfillment fulfillment) { }
    private static void changedCounterquotes(SettlementEconomicPlan prior, SettlementEconomicPlan current, Sink sink) {
        var before = quoteTerms(prior); var after = quoteTerms(current);
        var keys = new HashSet<>(before.keySet()); keys.addAll(after.keySet());
        for (var key : keys) if (!Objects.equals(before.get(key), after.get(key)))
            sink.resource(key.recipient().id(), MARKET, key.itemKind());
    }
    private static Map<ConditionalProductionQuote.Key, QuoteTerms> quoteTerms(SettlementEconomicPlan plan) {
        if (plan == null) return Map.of();
        var result = new HashMap<ConditionalProductionQuote.Key, QuoteTerms>();
        for (var q : plan.conditionalQuotes()) result.put(q.key(), new QuoteTerms(q.quantity(), q.goodsUnitPrice(),
                q.resourceFreight(), q.cashFreight(), q.fulfillment()));
        return result;
    }
    private static boolean sameBuyerDemand(SettlementEconomicPlan prior, SettlementEconomicPlan current) {
        if (prior == null) return false;
        var a = prior.domestic(); var b = current.domestic();
        if (a == b) return true;
        if (!a.preferredPurchaseSpending().equals(b.preferredPurchaseSpending())
                || !a.preferredPurchaseQuantities().equals(b.preferredPurchaseQuantities())
                || !a.economy().map(value -> value.purchaseBudgets()).equals(b.economy().map(value -> value.purchaseBudgets()))
                || !a.economy().map(value -> value.operatingBudgets()).equals(b.economy().map(value -> value.operatingBudgets()))
                || !a.economy().map(value -> value.purchasePrices()).equals(b.economy().map(value -> value.purchasePrices()))) return false;
        return a.demands().keySet().equals(b.demands().keySet()) && a.demands().entrySet().stream().allMatch(entry ->
                entry.getValue().purchase() == b.require(entry.getKey()).purchase()
                && entry.getValue().urgentPurchase() == b.require(entry.getKey()).urgentPurchase());
    }
    private static void quote(GoodsTradeOrder order, Sink sink) {
        // A producer's own published output is not a new input to that producer.
        sink.resource(order.counterparty().id(), MARKET, order.itemKind());
    }
    private static void contract(GoodsTradeContract contract, Sink sink) {
        sink.resource(contract.buyer().id(), COMMITMENT, contract.itemKind());
        sink.resource(contract.seller().id(), COMMITMENT, contract.itemKind());
    }
}
