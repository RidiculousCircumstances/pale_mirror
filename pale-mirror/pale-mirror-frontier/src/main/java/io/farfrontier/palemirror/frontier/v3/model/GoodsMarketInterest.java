package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Rebuildable commodity/side interest, never an authorization or another order owner. */
public final class GoodsMarketInterest {
    private record Key(String item, GoodsTradeOrder.Side side) { }
    private static final ImmutableGroupView<io.farfrontier.palemirror.frontier.v3.api.SubjectId, GoodsTradeOrder, Key> ORDERS = new ImmutableGroupView<>();
    private GoodsMarketInterest() { }
    public static List<GoodsTradeParty> recipients(GoodsTradeState trade, GoodsTradeOrder quote, OptionalLong atTick) {
        return recipients(trade, quote, atTick, ignored -> true);
    }
    /** Immediate matching interest is narrower than economic observation. An
     * above-bid ask still informs the next price/production review, but cannot
     * be accepted under the current opposing consent. This grants no admission. */
    public static List<GoodsTradeParty> matchingRecipients(GoodsTradeState trade, GoodsTradeOrder quote, long now) {
        if (now < 0) throw new IllegalArgumentException("negative matching instant");
        return recipients(trade, quote, OptionalLong.of(now), order -> quote.availableQuantity() > 0
                && quote.expiresAtTick() >= now && (quote.side() == GoodsTradeOrder.Side.SELL
                    ? quote.unitPriceLimit().compareTo(order.unitPriceLimit()) <= 0
                    : order.unitPriceLimit().compareTo(quote.unitPriceLimit()) <= 0));
    }
    private static List<GoodsTradeParty> recipients(GoodsTradeState trade, GoodsTradeOrder quote, OptionalLong atTick,
            java.util.function.Predicate<GoodsTradeOrder> accepts) {
        var owner = trade.participants().participants().get(quote.party().id());
        if (owner == null || !owner.party().equals(quote.party())) throw new IllegalArgumentException("quote lost its declared participant");
        var opposite = quote.side() == GoodsTradeOrder.Side.BUY ? GoodsTradeOrder.Side.SELL : GoodsTradeOrder.Side.BUY;
        var grouped = new HashMap<GoodsTradeParty, Long>();
        for (var order : ORDERS.unorderedRows(GoodsMarketInterest.class, trade.orders(),
                order -> Set.of(new Key(order.itemKind(), order.side())), new Key(quote.itemKind(), opposite)))
            if (order.availableQuantity() > 0 && accepts.test(order)) grouped.merge(order.party(), order.expiresAtTick(), Math::max);
        return grouped.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().id()))
                .filter(entry -> !entry.getKey().id().equals(quote.party().id())
                        && (atTick.isEmpty() || entry.getValue() >= atTick.getAsLong()))
                .map(Map.Entry::getKey).filter(party -> {
                    var other = trade.participants().participants().get(party.id());
                    return other != null && other.party().equals(party) && GoodsBuyerDemand.mutuallyKnown(owner, other);
                }).toList();
    }
}
