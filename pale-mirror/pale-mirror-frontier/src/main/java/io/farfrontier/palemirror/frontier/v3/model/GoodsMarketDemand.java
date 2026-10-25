package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.market.SellerPriceDiscovery;
import java.util.*;

/** Canonical open quotes -> bounded price advice. No route requests, recursive plans or reservations. */
final class GoodsMarketDemand {
    static SellerPriceDiscovery.Market read(FrontierWorldState state, GoodsParticipant seller, String item, long surplus, long now) {
        if (surplus <= 0) return SellerPriceDiscovery.Market.empty();
        var trade = state.companies().goodsTrade();
        var result = new ArrayList<SellerPriceDiscovery.Demand>();
        for (var demand : GoodsBuyerDemand.read(state, seller, item, now)) {
            var order = demand.order(); var view = demand.buyer(); var buyer = view.participant();
            int quantity = demand.quantity();
            var delivery = GoodsDeliveryAdvice.read(state, seller, view, item, (int) Math.min(surplus, quantity),
                    1, demand.budget().raw(), GoodsDeliveryAdvice.Supply.STORED, now);
            if (delivery.isEmpty()) continue;
            quantity = Math.min(quantity, delivery.orElseThrow().quantity());
            var competitors = new TreeMap<SubjectId, SellerPriceDiscovery.Offer>();
            for (var other : trade.orders().values()) {
                if (other.side() != GoodsTradeOrder.Side.SELL || !other.counterparty().equals(buyer.party())
                        || other.party().equals(seller.party()) || !other.itemKind().equals(item)
                        || other.expiresAtTick() <= now || other.availableQuantity() == 0) continue;
                var provider = trade.participants().participants().get(other.party().id());
                if (provider == null || !provider.party().equals(other.party()) || !GoodsBuyerDemand.mutuallyKnown(provider, buyer)
                        || !GoodsParticipantStorage.eligible(state, provider, other.containerId(), item)) continue;
                int stock = Math.min(other.availableQuantity(), GoodsParticipantStorage.unclaimed(state, provider, other.containerId(), item));
                if (stock == 0) continue;
                var freight = GoodsDeliveryAdvice.from(state, provider, other.containerId(), view, item, Math.min(quantity, stock),
                        other.unitPriceLimit().raw(), demand.budget().raw(), now);
                if (freight.isEmpty()) continue;
                var priced = freight.orElseThrow();
                var offer = new SellerPriceDiscovery.Offer(provider.party().id(), priced.quantity(), other.unitPriceLimit(),
                        new FixedScalar(priced.tripExpense()), new FixedScalar(priced.cashDelivery()), priced.deliveryTicks(),
                        priced.workerTicks(), priced.minimumDispatchQuantity(), priced.departure());
                competitors.merge(provider.party().id(), offer, (a, b) -> {
                    var need = new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Requirement(item,
                            demand.quantity(), view.supplyPlan().map(p -> p.require(item).deadlineTicks()).orElse(Long.MAX_VALUE));
                    var alternatives = List.of(asProcurement(a, item, other.id()), asProcurement(b, item, order.id()));
                    var preferred = new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection().preferred(need, alternatives,
                            state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().orElseThrow().labourValuePerTick()).orElseThrow();
                    return preferred.id().equals(other.id()) ? a : b;
                });
            }
            result.add(new SellerPriceDiscovery.Demand(buyer.party().id(), quantity, demand.budget(), order.unitPriceLimit(),
                    FixedScalar.ZERO, List.copyOf(competitors.values()), new FixedScalar(delivery.orElseThrow().tripExpense()),
                    delivery.orElseThrow().minimumDispatchQuantity(), new FixedScalar(delivery.orElseThrow().cashDelivery()),
                    view.supplyPlan().map(p -> p.require(item).deadlineTicks()).orElse(Long.MAX_VALUE), delivery.orElseThrow().deliveryTicks(),
                    delivery.orElseThrow().workerTicks(), state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().orElseThrow().labourValuePerTick(),
                    delivery.orElseThrow().departure()));
        }
        return new SellerPriceDiscovery.Market(result);
    }
    private static io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Offer asProcurement(
            SellerPriceDiscovery.Offer offer, String item, SubjectId id) {
        return new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Offer(id,
                Map.of(item, new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Line(offer.quantity(), offer.goodsPrice())),
                offer.resourceFreight(), offer.arrivalDelayTicks(), offer.workerTicks(), offer.departure());
    }
    private GoodsMarketDemand() { }
}
