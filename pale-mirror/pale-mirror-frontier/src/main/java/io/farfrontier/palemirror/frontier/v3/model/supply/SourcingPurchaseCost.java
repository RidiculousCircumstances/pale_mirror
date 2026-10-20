package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Quantity-aware cash advice. Each physical offer pays its fixed freight once.
 * A partial quote is never proof that the remaining goods can be bought. */
public final class SourcingPurchaseCost {
    public record Quote(long quantity, long cash) {
        public Quote {
            if (quantity < 0 || cash < 0) throw new IllegalArgumentException("negative purchase quote");
        }
    }
    public static Quote quote(SourcingMarket market, String item, long wanted, long deadline) {
        if (wanted < 0) throw new IllegalArgumentException("negative purchase quantity");
        long remaining = wanted, cash = 0;
        var offers = new ArrayList<>(market.offers().stream()
                .filter(o -> o.item().equals(item) && o.deliveryTicks() <= deadline).toList());
        while (remaining > 0 && !offers.isEmpty()) {
            final long needed = remaining;
            var best = offers.stream().min(Comparator.<SourcingMarket.Offer>comparingLong(o ->
                    Math.ceilDiv(cost(o, Math.min(needed, o.quantity())), Math.min(needed, o.quantity())))
                    .thenComparing(SourcingMarket.Offer::id)).orElseThrow();
            long take = Math.min(remaining, best.quantity());
            cash = Math.addExact(cash, cost(best, take));
            remaining -= take; offers.remove(best);
        }
        if (remaining == 0 && wanted > 0) {
            // Greedy unit ranking can add a cheap tiny lot without avoiding the
            // second trip's fixed expense. Compare complete single-lot options too.
            long single = market.offers().stream().filter(o -> o.item().equals(item)
                    && o.deliveryTicks() <= deadline && o.quantity() >= wanted)
                    .mapToLong(o -> cost(o, wanted)).min().orElse(cash);
            cash = Math.min(cash, single);
        }
        return new Quote(wanted - remaining, cash);
    }
    public static long forecast(SourcingMarket market, String item, long wanted, long referencePrice) {
        var quote = quote(market, item, wanted, Long.MAX_VALUE);
        return Math.addExact(quote.cash(), Math.multiplyExact(wanted - quote.quantity(), referencePrice));
    }
    private static long cost(SourcingMarket.Offer offer, long quantity) {
        return Math.addExact(Math.multiplyExact(quantity, offer.cashPerItem()), offer.tripCashCost());
    }
    private SourcingPurchaseCost() { }
}
