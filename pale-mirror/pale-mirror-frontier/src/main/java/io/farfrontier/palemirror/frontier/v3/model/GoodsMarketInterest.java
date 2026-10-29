package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Rebuildable commodity/side interest, never an authorization or another order owner. */
public final class GoodsMarketInterest {
    private record Key(String item, GoodsTradeOrder.Side side) { }
    private static final ImmutableGroupView<io.farfrontier.palemirror.frontier.v3.api.SubjectId, GoodsTradeOrder, Key> ORDERS = new ImmutableGroupView<>();
    private GoodsMarketInterest() { }
    public static List<GoodsTradeParty> recipients(GoodsTradeState trade, GoodsTradeOrder quote, OptionalLong atTick) {
        var owner = trade.participants().participants().get(quote.party().id());
        if (owner == null || !owner.party().equals(quote.party())) throw new IllegalArgumentException("quote lost its declared participant");
        var opposite = quote.side() == GoodsTradeOrder.Side.BUY ? GoodsTradeOrder.Side.SELL : GoodsTradeOrder.Side.BUY;
        var grouped = new HashMap<GoodsTradeParty, Long>();
        for (var order : ORDERS.unorderedRows(GoodsMarketInterest.class, trade.orders(),
                order -> Set.of(new Key(order.itemKind(), order.side())), new Key(quote.itemKind(), opposite)))
            if (order.availableQuantity() > 0) grouped.merge(order.party(), order.expiresAtTick(), Math::max);
        return grouped.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().id()))
                .filter(entry -> !entry.getKey().id().equals(quote.party().id())
                        && (atTick.isEmpty() || entry.getValue() >= atTick.getAsLong()))
                .map(Map.Entry::getKey).filter(party -> {
                    var other = trade.participants().participants().get(party.id());
                    return other != null && other.party().equals(party) && GoodsBuyerDemand.mutuallyKnown(owner, other);
                }).toList();
    }
}
