package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Rebuildable commodity/side interest, never an authorization or another order owner. */
public final class GoodsMarketInterest {
    private record Key(String item, GoodsTradeOrder.Side side) { }
    private record Interest(GoodsTradeParty party, long expiresAt) { }
    private static final ImmutableResultView<String, Map<Key, List<Interest>>> INDEX = new ImmutableResultView<>(4);
    private GoodsMarketInterest() { }
    public static List<GoodsTradeParty> recipients(GoodsTradeState trade, GoodsTradeOrder quote, OptionalLong atTick) {
        var index = INDEX.get(List.of(trade.orders()), "market-interest", () -> {
            var grouped = new HashMap<Key, Map<GoodsTradeParty, Long>>();
            for (var order : trade.orders().values()) if (order.availableQuantity() > 0)
                grouped.computeIfAbsent(new Key(order.itemKind(), order.side()), ignored -> new HashMap<>())
                        .merge(order.party(), order.expiresAtTick(), Math::max);
            var result = new HashMap<Key, List<Interest>>();
            grouped.forEach((key, parties) -> result.put(key, parties.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().id()))
                    .map(entry -> new Interest(entry.getKey(), entry.getValue())).toList()));
            return Map.copyOf(result);
        });
        var owner = trade.participants().participants().get(quote.party().id());
        if (owner == null || !owner.party().equals(quote.party())) throw new IllegalArgumentException("quote lost its declared participant");
        var opposite = quote.side() == GoodsTradeOrder.Side.BUY ? GoodsTradeOrder.Side.SELL : GoodsTradeOrder.Side.BUY;
        return index.getOrDefault(new Key(quote.itemKind(), opposite), List.of()).stream()
                .filter(interest -> !interest.party().id().equals(quote.party().id())
                        && (atTick.isEmpty() || interest.expiresAt() >= atTick.getAsLong()))
                .map(Interest::party).filter(party -> {
                    var other = trade.participants().participants().get(party.id());
                    return other != null && other.party().equals(party) && GoodsBuyerDemand.mutuallyKnown(owner, other);
                }).toList();
    }
}
