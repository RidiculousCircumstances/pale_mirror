package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.model.GoodsTradeOrder;
import io.farfrontier.palemirror.frontier.v3.model.GoodsTradeParty;
import io.farfrontier.palemirror.frontier.v3.model.GoodsTradeState;
import java.util.*;

/** Read-only price/time order book. It grants neither stock, money nor carriage authority. */
public final class MarketOfferBook {
    public record Pair(GoodsTradeOrder sell, GoodsTradeOrder buy) {
        public Pair {
            Objects.requireNonNull(sell); Objects.requireNonNull(buy);
            if (sell.side() != GoodsTradeOrder.Side.SELL || buy.side() != GoodsTradeOrder.Side.BUY
                    || !sell.party().equals(buy.counterparty()) || !buy.party().equals(sell.counterparty())
                    || !sell.itemKind().equals(buy.itemKind()))
                throw new IllegalArgumentException("market pair must retain reciprocal commodity consent");
        }
    }
    private record Key(GoodsTradeParty seller, GoodsTradeParty buyer, String commodity) { }
    private final Map<GoodsTradeParty, List<GoodsTradeOrder>> sales;
    private final Set<GoodsTradeParty> buyers;
    private final Map<Key, List<GoodsTradeOrder>> bids;

    public MarketOfferBook(Collection<GoodsTradeOrder> orders, long now) {
        if (now < 0 || orders.size() > GoodsTradeState.MAX_ORDERS
                || orders.stream().map(GoodsTradeOrder::id).distinct().count() != orders.size())
            throw new IllegalArgumentException("invalid bounded market order book");
        var indexed = new HashMap<Key, List<GoodsTradeOrder>>();
        var current = orders.stream().filter(order -> order.availableQuantity() > 0 && order.expiresAtTick() >= now).toList();
        for (var order : current) if (order.side() == GoodsTradeOrder.Side.BUY)
            indexed.computeIfAbsent(new Key(order.counterparty(), order.party(), order.itemKind()), ignored -> new ArrayList<>()).add(order);
        indexed.replaceAll((key, values) -> values.stream().sorted(Comparator.comparing(GoodsTradeOrder::id)).toList());
        bids = Map.copyOf(indexed);
        var ordered = current.stream().filter(order -> order.side() == GoodsTradeOrder.Side.SELL)
                .sorted(Comparator.comparing(GoodsTradeOrder::itemKind).thenComparing(GoodsTradeOrder::unitPriceLimit)
                        .thenComparingLong(GoodsTradeOrder::expiresAtTick).thenComparing(GoodsTradeOrder::id)).toList();
        var byBuyer = new HashMap<GoodsTradeParty, List<GoodsTradeOrder>>();
        for (var order : ordered) byBuyer.computeIfAbsent(order.counterparty(), ignored -> new ArrayList<>()).add(order);
        byBuyer.replaceAll((buyer, quotes) -> List.copyOf(quotes));
        sales = Map.copyOf(byBuyer);
        var purchasers = new TreeSet<GoodsTradeParty>(Comparator.comparing(GoodsTradeParty::id));
        bids.keySet().forEach(key -> purchasers.add(key.buyer()));
        buyers = Collections.unmodifiableSet(purchasers);
    }

    /** One purchaser compares all its known quotes; equal prices keep stable expiry/identity order. */
    public List<Pair> offers(GoodsTradeParty buyer, Optional<GoodsTradeParty> seller, Set<String> excluded) {
        Objects.requireNonNull(buyer); Objects.requireNonNull(seller); excluded = Set.copyOf(excluded);
        var omitted = excluded;
        return sales.getOrDefault(buyer, List.of()).stream().filter(sell -> seller.filter(value -> !value.equals(sell.party())).isEmpty() && !omitted.contains(sell.itemKind()))
                .flatMap(sell -> bids.getOrDefault(new Key(sell.party(), buyer, sell.itemKind()), List.of()).stream()
                        .map(buy -> new Pair(sell, buy)))
                .limit(GoodsTradeState.MAX_ORDERS).toList();
    }

    public Set<GoodsTradeParty> buyers() {
        return buyers;
    }
}
