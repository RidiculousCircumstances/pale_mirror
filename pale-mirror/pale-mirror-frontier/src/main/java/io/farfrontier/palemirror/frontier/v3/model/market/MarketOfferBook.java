package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.model.GoodsTradeOrder;
import io.farfrontier.palemirror.frontier.v3.model.GoodsTradeParty;
import io.farfrontier.palemirror.frontier.v3.model.GoodsTradeState;
import java.util.*;

/** Public price/time book. It grants neither stock, money nor carriage authority. */
public final class MarketOfferBook {
    public record Pair(GoodsTradeOrder sell, GoodsTradeOrder buy) {
        public Pair {
            Objects.requireNonNull(sell); Objects.requireNonNull(buy);
            if (sell.side() != GoodsTradeOrder.Side.SELL || buy.side() != GoodsTradeOrder.Side.BUY
                    || sell.party().id().equals(buy.party().id()) || !sell.itemKind().equals(buy.itemKind()))
                throw new IllegalArgumentException("market pair needs distinct owners and opposing commodity consent");
        }
    }
    private record SaleScope(GoodsTradeParty seller, String itemKind) { }
    private final Map<String, List<GoodsTradeOrder>> sales;
    private final Map<SaleScope, List<GoodsTradeOrder>> sellerSales;
    private final long assessedAtTick, validThroughTick;
    private final Map<GoodsTradeParty, List<GoodsTradeOrder>> bids;
    private final Set<GoodsTradeParty> buyers;
    public MarketOfferBook(Collection<GoodsTradeOrder> orders, long now) {
        if (now < 0 || orders.size() > GoodsTradeState.MAX_ORDERS
                || orders.stream().map(GoodsTradeOrder::id).distinct().count() != orders.size())
            throw new IllegalArgumentException("invalid bounded market order book");
        assessedAtTick = now;
        long through = Long.MAX_VALUE;
        var asks = new HashMap<String, List<GoodsTradeOrder>>();
        var purchases = new HashMap<GoodsTradeParty, List<GoodsTradeOrder>>();
        for (var order : orders) {
            if (order.availableQuantity() == 0 || order.expiresAtTick() < now) continue;
            through = Math.min(through, order.expiresAtTick());
            if (order.side() == GoodsTradeOrder.Side.SELL)
                asks.computeIfAbsent(order.itemKind(), ignored -> new ArrayList<>()).add(order);
            else purchases.computeIfAbsent(order.party(), ignored -> new ArrayList<>()).add(order);
        }
        asks.replaceAll((item, values) -> values.stream().sorted(Comparator.comparing(GoodsTradeOrder::unitPriceLimit)
                .thenComparingLong(GoodsTradeOrder::expiresAtTick).thenComparing(GoodsTradeOrder::id)).toList());
        purchases.replaceAll((party, values) -> values.stream().sorted(Comparator.comparing(GoodsTradeOrder::itemKind)
                .thenComparing(GoodsTradeOrder::unitPriceLimit, Comparator.reverseOrder())
                .thenComparingLong(GoodsTradeOrder::expiresAtTick).thenComparing(GoodsTradeOrder::id)).toList());
        sales = Map.copyOf(asks); bids = Map.copyOf(purchases); validThroughTick = through;
        var bySeller = new HashMap<SaleScope, List<GoodsTradeOrder>>();
        asks.forEach((item, values) -> values.forEach(order -> bySeller.computeIfAbsent(
                new SaleScope(order.party(), item), ignored -> new ArrayList<>()).add(order)));
        bySeller.replaceAll((scope, values) -> List.copyOf(values)); sellerSales = Map.copyOf(bySeller);
        var purchasers = new TreeSet<GoodsTradeParty>(Comparator.comparing(GoodsTradeParty::id));
        purchasers.addAll(purchases.keySet()); buyers = Collections.unmodifiableSet(purchasers);
    }
    /** This exact immutable order image changes at expiry, never on an arbitrary cache TTL. */
    public boolean validAt(long now) { return now >= assessedAtTick && now <= validThroughTick; }
    /** Compare the purchaser's public bids to asks; consent/known-party admission stays with trade. */
    public List<Pair> offers(GoodsTradeParty buyer, Optional<GoodsTradeParty> seller, Set<String> excluded) {
        Objects.requireNonNull(buyer); Objects.requireNonNull(seller); Objects.requireNonNull(excluded);
        var result = new ArrayList<Pair>();
        for (var buy : bids.getOrDefault(buyer, List.of())) {
            if (excluded.contains(buy.itemKind())) continue;
            var alternatives = seller.map(party -> sellerSales.getOrDefault(new SaleScope(party, buy.itemKind()), List.of()))
                    .orElseGet(() -> sales.getOrDefault(buy.itemKind(), List.of()));
            for (var sell : alternatives) {
                if (sell.party().id().equals(buyer.id()) || seller.filter(value -> !value.equals(sell.party())).isPresent()) continue;
                if (result.size() == GoodsTradeState.MAX_ORDERS) return List.copyOf(result);
                result.add(new Pair(sell, buy));
            }
        }
        return List.copyOf(result);
    }
    /** Only owners with an opposing live commodity offer are procurement alternatives.
     * This derived selection does not establish mutual knowledge or trade authority. */
    public Set<GoodsTradeParty> sellers(GoodsTradeParty buyer) {
        var result = new TreeSet<GoodsTradeParty>(Comparator.comparing(GoodsTradeParty::id));
        for (var item : bids.getOrDefault(buyer, List.of()).stream().map(GoodsTradeOrder::itemKind).distinct().toList())
            for (var ask : sales.getOrDefault(item, List.of()))
                if (!ask.party().id().equals(buyer.id())) result.add(ask.party());
        return Collections.unmodifiableSet(result);
    }
    public Set<GoodsTradeParty> buyers() { return buyers; }
}
