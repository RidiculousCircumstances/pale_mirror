package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Current independent buyer envelopes shared by price and conditional production advice.
 * Alternative warehouses do not multiply a requirement or its funding. */
final class GoodsBuyerDemand {
    record Demand(GoodsTradeOrder order, GoodsParticipantView buyer, int quantity, FixedScalar budget) { }

    static List<Demand> read(FrontierWorldState state, GoodsParticipant seller, String item, long atTick) {
        return read(state, seller, item, atTick, 0);
    }

    /** A buyer below the seller's price cannot become executable by adding freight.
     * Filter that envelope before evaluating the buyer's independent economy. */
    static List<Demand> read(FrontierWorldState state, GoodsParticipant seller, String item, long atTick, long minimumUnitPrice) {
        if (minimumUnitPrice < 0) throw new IllegalArgumentException("negative buyer price bound");
        var trade = state.companies().goodsTrade();
        var rules = state.bootstrap().ruleset().goodsTrade();
        var buyers = new TreeMap<SubjectId, GoodsTradeOrder>();
        for (var order : trade.orders().values()) {
            if (order.side() != GoodsTradeOrder.Side.BUY || !order.counterparty().equals(seller.party())
                    || !order.itemKind().equals(item) || order.unitPriceLimit().raw() < minimumUnitPrice || order.expiresAtTick() <= atTick || order.availableQuantity() == 0) continue;
            var buyer = trade.participants().participants().get(order.party().id());
            if (buyer == null || !buyer.party().equals(order.party()) || !mutuallyKnown(seller, buyer)
                    || !GoodsParticipantStorage.eligible(state, buyer, order.containerId(), item)
                    || !GoodsTradeStateSupport.receivingCapacity(state, order.containerId(), item, 1)) continue;
            buyers.merge(buyer.party().id(), order, (a, b) -> compare(a, b) <= 0 ? a : b);
        }
        var result = new ArrayList<Demand>();
        for (var order : buyers.values()) {
            var buyer = trade.participants().participants().get(order.party().id());
            var view = GoodsParticipantAssessment.domestic(state, buyer, atTick).view();
            var budget = GoodsParticipantFunding.purchase(view, rules, item);
            if (budget.raw() <= 0) continue;
            int quantity = Math.min(order.availableQuantity(), GoodsTransportOptions.maximumQuantity(state, item));
            if (view.supplyPlan().isPresent()) quantity = (int) Math.min(quantity, view.supplyPlan().orElseThrow().require(item).purchase());
            while (quantity > 0 && !GoodsTradeStateSupport.receivingCapacity(state, order.containerId(), item, quantity)) quantity--;
            if (quantity > 0) result.add(new Demand(order, view, quantity, budget));
        }
        return List.copyOf(result);
    }

    static boolean mutuallyKnown(GoodsParticipant a, GoodsParticipant b) {
        return a.known().stream().anyMatch(k -> k.party().equals(b.party()) && k.settlementId().equals(b.settlementId()))
                && b.known().stream().anyMatch(k -> k.party().equals(a.party()) && k.settlementId().equals(a.settlementId()));
    }
    private static int compare(GoodsTradeOrder a, GoodsTradeOrder b) {
        int price = b.unitPriceLimit().compareTo(a.unitPriceLimit());
        if (price != 0) return price;
        int quantity = Integer.compare(b.availableQuantity(), a.availableQuantity());
        return quantity != 0 ? quantity : a.id().compareTo(b.id());
    }
    private GoodsBuyerDemand() { }
}
