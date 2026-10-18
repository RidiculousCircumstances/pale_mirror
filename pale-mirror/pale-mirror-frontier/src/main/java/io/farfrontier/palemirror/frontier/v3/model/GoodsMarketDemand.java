package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.*;

/** Read-only quoted demand for price discovery, not shipment feasibility or spending authority. */
final class GoodsMarketDemand {
    /** Market exposure is a standing lawful offer, not a caravan already won.
     * Losing to a cheaper supplier must still age the unsold offer. */
    static boolean offered(FrontierWorldState state, GoodsParticipant seller, String item, long now) {
        var trade = state.companies().goodsTrade();
        return trade.orders().values().stream().anyMatch(order -> {
            if (order.side() != GoodsTradeOrder.Side.SELL || !order.party().equals(seller.party())
                    || !order.itemKind().equals(item) || order.expiresAtTick() < now || order.availableQuantity() == 0
                    || !GoodsParticipantStorage.eligible(state, seller, order.containerId(), item)) return false;
            var peer = trade.participants().participants().get(order.counterparty().id());
            return peer != null && peer.party().equals(order.counterparty())
                    && seller.known().stream().anyMatch(k -> k.party().equals(peer.party()))
                    && peer.known().stream().anyMatch(k -> k.party().equals(seller.party()));
        });
    }
    static Map<String, FixedScalar> fundedBids(FrontierWorldState state, GoodsParticipant seller, long now) {
        var trade = state.companies().goodsTrade();
        var known = new HashSet<GoodsTradeParty>(); seller.known().forEach(peer -> known.add(peer.party()));
        var result = new TreeMap<String, FixedScalar>();
        for (var order : trade.orders().values()) {
            if (order.side() != GoodsTradeOrder.Side.BUY || !order.counterparty().equals(seller.party())
                    || !known.contains(order.party()) || order.expiresAtTick() <= now || order.availableQuantity() == 0) continue;
            var buyer = trade.participants().participants().get(order.party().id());
            if (buyer == null || !buyer.party().equals(order.party())
                    || buyer.known().stream().noneMatch(peer -> peer.party().equals(seller.party()))
                    || !GoodsParticipantStorage.eligible(state, buyer, order.containerId(), order.itemKind())) continue;
            long limit = Math.min(order.unitPriceLimit().raw(), state.inventory().economics().availableToReserve(order.party().id()).raw());
            if (limit > 0) result.merge(order.itemKind(), new FixedScalar(limit), (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }
        // Alternative warehouses/peers are not additive cash or demand. One highest
        // bid prevents a poorer buyer from overriding an already compatible quote.
        return Map.copyOf(result);
    }
    private GoodsMarketDemand() { }
}
