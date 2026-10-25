package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.economy.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Composition boundary: canonical owners expose facts; the pure economic planner returns advice. */
final class SettlementEconomicPlanning {
    static EconomicSnapshot facts(FrontierWorldState state, SettlementSupplyFacts input) {
        var home = input.settlement();
        var trade = state.companies().goodsTrade(); var rules = state.bootstrap().ruleset().goodsTrade();
        var participant = trade.participants().participants().get(home);
        var basis = input.basis();
        var prices = new TreeMap<String, Long>();
        for (var commodity : rules.policies().get(participant.policy())) {
            var item = commodity.itemKind();
            long value = GoodsResourceValuation.replacement(state, participant, item, input.atTick()).raw();
            var stock = input.stocks().get(item);
            var quote = ConditionalSupplyPricing.best(state, participant, item,
                    Math.max(1, stock.target() - stock.covered()), input.market().availableMoney(), input.atTick());
            // A conditional price can size the cash envelope, not declare a
            // funded restart, stock or a delivery date.
            prices.put(item, quote.map(ConditionalSupplyPricing.Estimate::cashUnitExpense).orElse(value));
        }
        long receivables = trade.contracts().values().stream().filter(c -> !c.terminal() && c.seller().equals(participant.party()))
                .mapToLong(c -> c.outstandingPrice().raw()).reduce(0L, Math::addExact);
        return new EconomicSnapshot(state.humanPopulation().schedule(home).dayTicks(), input.stocks(),
                basis.needs(), basis.flows(), input.reserves(), input.capacities(), input.commitments(),
                input.market(), prices, List.of(), receivables);
    }
    record QuoteCandidate(EconomicSnapshot.ExportInterest interest, GoodsParticipantView buyer,
                          GoodsDeliveryAdvice.Advice freight) { }
    record ExportAdvice(List<QuoteCandidate> consented, List<QuoteCandidate> counters) {
        ExportAdvice { consented = List.copyOf(consented); counters = List.copyOf(counters); }
        List<QuoteCandidate> all() { var result = new ArrayList<>(consented); result.addAll(counters); return List.copyOf(result); }
    }
    static List<EconomicSnapshot.ExportInterest> exports(FrontierWorldState state, SettlementSupplyFacts input,
            boolean requireSustainedExport, Set<String> potentialItems) {
        return exportAdvice(state, input, requireSustainedExport, potentialItems, false).consented().stream()
                .map(QuoteCandidate::interest).toList();
    }
    static ExportAdvice exportAdvice(FrontierWorldState state, SettlementSupplyFacts input,
            boolean requireSustainedExport, Set<String> potentialItems, boolean includeCounters) {
        var home = input.settlement();
        var trade = state.companies().goodsTrade(); var rules = state.bootstrap().ruleset().goodsTrade();
        var participant = trade.participants().participants().get(home);
        var asks = new TreeMap<String, Long>();
        for (var commodity : rules.policies().get(participant.policy()))
            asks.put(commodity.itemKind(), GoodsParticipantPricing.ask(participant, commodity).raw());
        long now = input.atTick();
        var interests = new ArrayList<QuoteCandidate>(); var counters = new ArrayList<QuoteCandidate>();
        for (var item : new TreeSet<>(potentialItems)) {
          for (var demand : GoodsBuyerDemand.read(state, participant, item, now, includeCounters ? 0 : asks.get(item))) {
            var order = demand.order();
            long salePrice = asks.get(item);
            int quantity = demand.quantity();
            var cultivation = state.bootstrap().ruleset().fields().cultivation();
            if (requireSustainedExport && cultivation.isPresent()) {
                var observation = trade.demandHistory().observations().get(
                        new io.farfrontier.palemirror.frontier.v3.model.market.MarketDemandHistory.Key(order.party().id(), order.itemKind()));
                long duration = Math.multiplyExact(state.humanPopulation().schedule(home).dayTicks(), cultivation.orElseThrow().stableDemandDays());
                var sustained = observation == null ? Optional.<io.farfrontier.palemirror.frontier.v3.model.market.MarketDemandHistory.Quote>empty()
                        : observation.sustainedQuote(now, duration, Math.multiplyExact(2, rules.reviewInterval()));
                if (sustained.isEmpty() || sustained.orElseThrow().unitPrice() < salePrice) continue;
                quantity = (int) Math.min(quantity, sustained.orElseThrow().quantity());
            }
            if (quantity == 0) continue;
            // A counterquote can inform reallocation of current real cash, never
            // conditional revenue. Existing reservations are already subtracted.
            long budget = includeCounters ? demand.buyer().availableMoney().raw() : demand.budget().raw();
            var freight = GoodsDeliveryAdvice.read(state, participant, demand.buyer(), item, quantity,
                    salePrice, budget, GoodsDeliveryAdvice.Supply.CONDITIONAL_PRODUCTION, now);
            if (freight.isEmpty()) continue;
            quantity = freight.orElseThrow().quantity();
            long unitExpense = Math.addExact(salePrice, freight.orElseThrow().deliveryPerItem());
            long unitCash = Math.addExact(salePrice, freight.orElseThrow().cashDeliveryPerItem());
            boolean consented = order.unitPriceLimit().raw() >= unitExpense && demand.budget().raw() >= unitCash;
            if (!consented && !includeCounters) continue;
            var candidate = new QuoteCandidate(new EconomicSnapshot.ExportInterest(order.party().id().value(), item, quantity,
                    salePrice, demand.buyer().availableMoney().raw(), consented ? demand.budget().raw() : budget,
                    freight.orElseThrow().deliveryPerItem(), freight.orElseThrow().cashDeliveryPerItem()), demand.buyer(), freight.orElseThrow());
            (consented ? interests : counters).add(candidate);
          }
        }
        return new ExportAdvice(interests, counters);
    }
    private SettlementEconomicPlanning() { }
}
