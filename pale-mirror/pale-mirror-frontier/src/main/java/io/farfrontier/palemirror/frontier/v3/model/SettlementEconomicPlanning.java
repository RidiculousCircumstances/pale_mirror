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
        for (var commodity : rules.policies().get(participant.policy())) prices.put(commodity.itemKind(),
                trade.priceHistory().reference(commodity.itemKind(), rules.pricing().recencyTicks()).orElse(commodity.minimumSellPrice()).raw());
        long receivables = trade.contracts().values().stream().filter(c -> !c.terminal() && c.seller().equals(participant.party()))
                .mapToLong(c -> c.outstandingPrice().raw()).reduce(0L, Math::addExact);
        return new EconomicSnapshot(state.humanPopulation().schedule(home).dayTicks(), input.stocks(),
                basis.needs(), basis.flows(), input.reserves(), input.capacities(), input.commitments(),
                input.market(), prices, List.of(), receivables);
    }
    static List<EconomicSnapshot.ExportInterest> exports(FrontierWorldState state, SettlementSupplyFacts input,
            boolean requireSustainedExport) {
        var home = input.settlement();
        var trade = state.companies().goodsTrade(); var rules = state.bootstrap().ruleset().goodsTrade();
        var participant = trade.participants().participants().get(home);
        var asks = new TreeMap<String, Long>();
        for (var commodity : rules.policies().get(participant.policy()))
            asks.put(commodity.itemKind(), GoodsParticipantPricing.ask(participant, commodity).raw());
        long now = input.atTick();
        var interests = new ArrayList<EconomicSnapshot.ExportInterest>();
        for (var item : new TreeSet<>(input.stocks().keySet())) {
          for (var demand : GoodsBuyerDemand.read(state, participant, item, now)) {
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
            var freight = GoodsDeliveryAdvice.read(state, participant, demand.buyer(), item, quantity,
                    salePrice, demand.budget().raw(), GoodsDeliveryAdvice.Supply.CONDITIONAL_PRODUCTION, now);
            if (freight.isEmpty()) continue;
            quantity = freight.orElseThrow().quantity();
            long unitExpense = Math.addExact(salePrice, freight.orElseThrow().deliveryPerItem());
            if (order.unitPriceLimit().raw() < unitExpense || demand.budget().raw() < unitExpense) continue;
            interests.add(new EconomicSnapshot.ExportInterest(order.party().id().value(), item, quantity,
                    salePrice, demand.buyer().availableMoney().raw(), demand.budget().raw(), freight.orElseThrow().deliveryPerItem()));
          }
        }
        return List.copyOf(interests);
    }
    private SettlementEconomicPlanning() { }
}
