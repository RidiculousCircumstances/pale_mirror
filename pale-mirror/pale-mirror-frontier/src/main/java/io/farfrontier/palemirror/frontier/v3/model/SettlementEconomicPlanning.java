package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.economy.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Composition boundary: canonical owners expose facts; the pure economic planner returns advice. */
final class SettlementEconomicPlanning {
    static SupplyPlanner.Plan read(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks, Map<String, SupplyReservePlanner.Target> reserves,
            List<SupplyPlanner.Process> capacities, List<SupplyPlanner.Dependency> commitments, SourcingMarket market) {
        var trade = state.companies().goodsTrade(); var rules = state.bootstrap().ruleset().goodsTrade();
        var participant = trade.participants().participants().get(home);
        var basis = SettlementReservePlanning.basis(state, home);
        var prices = new TreeMap<String, Long>();
        var asks = new TreeMap<String, Long>();
        for (var commodity : rules.policies().get(participant.policy())) prices.put(commodity.itemKind(),
                trade.priceHistory().reference(commodity.itemKind(), rules.pricing().recencyTicks()).orElse(commodity.minimumSellPrice()).raw());
        for (var commodity : rules.policies().get(participant.policy()))
            asks.put(commodity.itemKind(), GoodsParticipantPricing.ask(participant, commodity).raw());
        long now = trade.participants().participants().values().stream().mapToLong(GoodsParticipant::reviewedAtTick).max().orElse(0);
        var interests = new TreeMap<String, EconomicSnapshot.ExportInterest>();
        for (var order : trade.orders().values().stream().sorted(Comparator.comparing(GoodsTradeOrder::id)).toList()) {
            if (order.side() != GoodsTradeOrder.Side.BUY || !order.counterparty().equals(participant.party())
                    || order.expiresAtTick() <= now || order.availableQuantity() == 0 || !stocks.containsKey(order.itemKind())
                    || participant.known().stream().noneMatch(peer -> peer.party().equals(order.party()))) continue;
            long salePrice = asks.get(order.itemKind());
            if (order.unitPriceLimit().raw() < salePrice) continue;
            long quantity = order.availableQuantity();
            var cultivation = state.bootstrap().ruleset().fields().cultivation();
            if (cultivation.isPresent()) {
                var observation = trade.demandHistory().observations().get(
                        new io.farfrontier.palemirror.frontier.v3.model.market.MarketDemandHistory.Key(order.party().id(), order.itemKind()));
                long duration = Math.multiplyExact(state.humanPopulation().schedule(home).dayTicks(), cultivation.orElseThrow().stableDemandDays());
                if (observation == null || !observation.sustained(now, duration, Math.multiplyExact(2, rules.reviewInterval()))
                        || observation.minimumPrice() < salePrice) continue;
                quantity = Math.min(quantity, observation.minimumQuantity());
            }
            var buyer = trade.participants().participants().get(order.party().id());
            if (buyer == null || !GoodsParticipantStorage.eligible(state, buyer, order.containerId(), order.itemKind())) continue;
            String pool = order.party().id().value() + "|" + order.itemKind();
            var interest = new EconomicSnapshot.ExportInterest(order.party().id().value(), order.itemKind(), quantity,
                    salePrice, state.inventory().economics().availableToReserve(order.party().id()).raw());
            // Alternative warehouses publish the same buyer requirement, not new demand.
            interests.merge(pool, interest, (a, b) -> a.quantity() >= b.quantity() ? a : b);
        }
        long receivables = trade.contracts().values().stream().filter(c -> !c.terminal() && c.seller().equals(participant.party()))
                .mapToLong(c -> c.outstandingPrice().raw()).reduce(0L, Math::addExact);
        var facts = new EconomicSnapshot(state.humanPopulation().schedule(home).dayTicks(), stocks,
                basis.needs(), basis.flows(), reserves, capacities, commitments, market, prices, List.copyOf(interests.values()), receivables);
        return new EconomicPlanner().plan(facts, rules.supplyPlanning().economics().orElseThrow());
    }
    private SettlementEconomicPlanning() { }
}
