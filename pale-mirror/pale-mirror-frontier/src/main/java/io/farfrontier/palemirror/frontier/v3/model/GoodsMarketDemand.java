package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.market.SellerPriceDiscovery;
import java.util.*;

/** Canonical open quotes -> bounded price advice. No route requests, recursive plans or reservations. */
final class GoodsMarketDemand {
    static SellerPriceDiscovery.Market read(FrontierWorldState state, GoodsParticipant seller, String item, long surplus, long now) {
        if (surplus <= 0) return SellerPriceDiscovery.Market.empty();
        var trade = state.companies().goodsTrade();
        var rules = state.bootstrap().ruleset().goodsTrade();
        var buyers = new TreeMap<SubjectId, GoodsTradeOrder>();
        for (var order : trade.orders().values()) {
            if (order.side() != GoodsTradeOrder.Side.BUY || !order.counterparty().equals(seller.party())
                    || !order.itemKind().equals(item) || order.expiresAtTick() <= now || order.availableQuantity() == 0) continue;
            var buyer = trade.participants().participants().get(order.party().id());
            if (buyer == null || !buyer.party().equals(order.party()) || !mutuallyKnown(seller, buyer)
                    || !GoodsParticipantStorage.eligible(state, buyer, order.containerId(), item)
                    || !GoodsTradeStateSupport.receivingCapacity(state, order.containerId(), item, 1)) continue;
            // Alternative warehouse quotes reuse one quantity and one purse.
            buyers.merge(buyer.party().id(), order, (a, b) -> compare(a, b) <= 0 ? a : b);
        }
        var result = new ArrayList<SellerPriceDiscovery.Demand>();
        for (var entry : buyers.entrySet()) {
            var order = entry.getValue(); var buyer = trade.participants().participants().get(entry.getKey());
            var view = GoodsParticipantView.read(state, buyer);
            var budget = GoodsParticipantFunding.purchase(view, rules, item);
            if (budget.raw() <= 0) continue;
            int quantity = Math.min(order.availableQuantity(), rules.maximumBatch());
            if (view.supplyPlan().isPresent()) quantity = (int) Math.min(quantity, view.supplyPlan().orElseThrow().require(item).purchase());
            if (quantity == 0) continue;
            var delivery = delivery(state, seller, buyer, item, (int) Math.min(surplus, quantity));
            if (delivery.isEmpty()) continue;
            var competitors = new TreeMap<SubjectId, SellerPriceDiscovery.Offer>();
            for (var other : trade.orders().values()) {
                if (other.side() != GoodsTradeOrder.Side.SELL || !other.counterparty().equals(buyer.party())
                        || other.party().equals(seller.party()) || !other.itemKind().equals(item)
                        || other.expiresAtTick() <= now || other.availableQuantity() == 0) continue;
                var provider = trade.participants().participants().get(other.party().id());
                if (provider == null || !provider.party().equals(other.party()) || !mutuallyKnown(provider, buyer)
                        || !GoodsParticipantStorage.eligible(state, provider, other.containerId(), item)) continue;
                int stock = Math.min(other.availableQuantity(), GoodsParticipantStorage.unclaimed(state, provider, other.containerId(), item));
                if (stock == 0) continue;
                var freight = deliveryFrom(state, other.containerId(), buyer, item, Math.min(quantity, stock));
                if (freight.isEmpty()) continue;
                var offer = new SellerPriceDiscovery.Offer(provider.party().id(), stock, other.unitPriceLimit().plus(freight.orElseThrow()));
                competitors.merge(provider.party().id(), offer, (a, b) -> {
                    int price = a.landedPrice().compareTo(b.landedPrice());
                    return price < 0 || price == 0 && a.quantity() >= b.quantity() ? a : b;
                });
            }
            result.add(new SellerPriceDiscovery.Demand(buyer.party().id(), quantity, budget, order.unitPriceLimit(),
                    delivery.orElseThrow(), List.copyOf(competitors.values())));
        }
        return new SellerPriceDiscovery.Market(result);
    }
    private static int compare(GoodsTradeOrder a, GoodsTradeOrder b) {
        int price = b.unitPriceLimit().compareTo(a.unitPriceLimit());
        if (price != 0) return price;
        int quantity = Integer.compare(b.availableQuantity(), a.availableQuantity());
        return quantity != 0 ? quantity : a.id().compareTo(b.id());
    }
    private static boolean mutuallyKnown(GoodsParticipant a, GoodsParticipant b) {
        return a.known().stream().anyMatch(k -> k.party().equals(b.party()))
                && b.known().stream().anyMatch(k -> k.party().equals(a.party()));
    }
    private static Optional<FixedScalar> delivery(FrontierWorldState state, GoodsParticipant seller,
                                                  GoodsParticipant buyer, String item, int quantity) {
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isEmpty()) return Optional.of(FixedScalar.ZERO);
        FixedScalar best = null;
        for (var container : GoodsParticipantStorage.containers(state, seller, item)) {
            int stock = GoodsParticipantStorage.unclaimed(state, seller, container, item);
            if (stock == 0) continue;
            int carried = Math.min(quantity, stock);
            var freight = deliveryFrom(state, container, buyer, item, carried);
            if (freight.isEmpty()) continue;
            var unit = freight.orElseThrow();
            if (best == null || unit.compareTo(best) < 0) best = unit;
        }
        return Optional.ofNullable(best);
    }
    private static Optional<FixedScalar> deliveryFrom(FrontierWorldState state, SubjectId container,
                                                      GoodsParticipant buyer, String item, int quantity) {
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isEmpty()) return Optional.of(FixedScalar.ZERO);
        return ProcurementTravelForecast.read(state, container, item, buyer)
                .filter(timing -> GoodsDispatchAdvice.assess(state, GoodsParticipantView.read(state, buyer), Map.of(item, quantity), timing.deliveryTicks())
                        != io.farfrontier.palemirror.frontier.v3.model.supply.TripDispatchPolicy.Cause.ACCUMULATING).map(timing ->
                new FixedScalar(Math.ceilDiv(GoodsTransportValuation.estimate(state, buyer, timing.journeyTicks(), 1)
                        .resourceExpense().raw(), quantity)));
    }
    private GoodsMarketDemand() { }
}
