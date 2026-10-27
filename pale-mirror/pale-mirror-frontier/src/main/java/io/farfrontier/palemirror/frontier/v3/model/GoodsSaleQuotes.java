package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Public asks available to a known buyer. Expiry/current stock remain caller checks. */
final class GoodsSaleQuotes {
    private static final ImmutableInputView<List<GoodsTradeOrder>> SALES = new ImmutableInputView<>();
    static List<GoodsTradeOrder> forBuyer(GoodsTradeState trade, GoodsTradeParty buyer) {
        var participant = trade.participants().participants().get(buyer.id());
        if (participant == null || !participant.party().equals(buyer)) throw new IllegalArgumentException("quote reader lacks its exact buyer");
        return SALES.get(List.of(trade.orders()), () -> trade.orders().values().stream()
                .filter(order -> order.side() == GoodsTradeOrder.Side.SELL)
                .sorted(Comparator.comparing(GoodsTradeOrder::unitPriceLimit).thenComparing(GoodsTradeOrder::id)).toList()).stream()
                .filter(order -> !order.party().id().equals(buyer.id()))
                .filter(order -> {
                    var seller = trade.participants().participants().get(order.party().id());
                    return seller != null && seller.party().equals(order.party()) && GoodsBuyerDemand.mutuallyKnown(seller, participant);
                }).toList();
    }
    private GoodsSaleQuotes() { }
}
