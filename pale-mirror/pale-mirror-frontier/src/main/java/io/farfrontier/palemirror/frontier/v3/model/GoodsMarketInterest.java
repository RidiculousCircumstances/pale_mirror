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
    /** Current public consent only; retired contract bookkeeping is not a new offer. */
    public record PurchaseOffer(io.farfrontier.palemirror.frontier.v3.api.SubjectId id, GoodsTradeParty party,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId containerId, String itemKind, int availableQuantity,
            io.farfrontier.palemirror.frontier.v3.api.FixedScalar unitPriceLimit, long expiresAtTick) {
        private static PurchaseOffer of(GoodsTradeOrder order) {
            return new PurchaseOffer(order.id(), order.party(), order.containerId(), order.itemKind(), order.availableQuantity(),
                    order.unitPriceLimit(), order.expiresAtTick());
        }
    }
    /** Addressed offer image, including absence for each requested commodity.
     * No full order-book scan per buyer and no admission authority. */
    public static Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, PurchaseOffer> purchaseOffers(
            GoodsTradeState trade, GoodsParticipant buyer, Map<String, io.farfrontier.palemirror.frontier.v3.api.FixedScalar> limits, long now) {
        var result = new HashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, PurchaseOffer>();
        for (var item : limits.keySet()) for (var order : ORDERS.unorderedRows(GoodsMarketInterest.class, trade.orders(),
                order -> Set.of(new Key(order.itemKind(), order.side())), new Key(item, GoodsTradeOrder.Side.SELL))) {
            if (order.availableQuantity() <= 0 || order.expiresAtTick() < now || order.party().equals(buyer.party())
                    || order.unitPriceLimit().compareTo(limits.get(item)) > 0) continue;
            var seller = trade.participants().participants().get(order.party().id());
            if (seller != null && seller.party().equals(order.party()) && GoodsBuyerDemand.mutuallyKnown(buyer, seller))
                result.put(order.id(), PurchaseOffer.of(order));
        }
        return Map.copyOf(result);
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
