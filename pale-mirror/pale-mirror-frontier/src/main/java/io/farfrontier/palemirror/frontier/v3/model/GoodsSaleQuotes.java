package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Addressed price advice from exact immutable orders. Expiry and current stock remain caller checks. */
final class GoodsSaleQuotes {
    private static final ImmutableInputView<Map<GoodsTradeParty, List<GoodsTradeOrder>>> SALES = new ImmutableInputView<>();
    static List<GoodsTradeOrder> forBuyer(GoodsTradeState trade, GoodsTradeParty buyer) {
        return SALES.get(List.of(trade.orders()), () -> {
            var result = new HashMap<GoodsTradeParty, List<GoodsTradeOrder>>();
            trade.orders().values().stream().filter(order -> order.side() == GoodsTradeOrder.Side.SELL)
                    .sorted(Comparator.comparing(GoodsTradeOrder::unitPriceLimit).thenComparing(GoodsTradeOrder::id))
                    .forEach(order -> result.computeIfAbsent(order.counterparty(), ignored -> new ArrayList<>()).add(order));
            result.replaceAll((party, orders) -> List.copyOf(orders));
            return Map.copyOf(result);
        }).getOrDefault(buyer, List.of());
    }
    private GoodsSaleQuotes() { }
}
