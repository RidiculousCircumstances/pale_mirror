package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.math.BigInteger;
import java.util.*;

/** Static response to visible orders, not a sale or a guarantee of future revenue. */
public final class SellerPriceDiscovery {
    public record Offer(SubjectId seller, int quantity, FixedScalar landedPrice) {
        public Offer {
            Objects.requireNonNull(seller); Objects.requireNonNull(landedPrice);
            if (quantity < 1 || landedPrice.raw() <= 0) throw new IllegalArgumentException("invalid competing offer");
        }
    }
    public record Demand(SubjectId buyer, int quantity, FixedScalar budget, FixedScalar landedLimit,
                         FixedScalar deliveryPerItem, List<Offer> alternatives, FixedScalar fixedDelivery, int minimumQuantity) {
        public Demand(SubjectId buyer, int quantity, FixedScalar budget, FixedScalar landedLimit,
                      FixedScalar deliveryPerItem, List<Offer> alternatives, FixedScalar fixedDelivery) {
            this(buyer, quantity, budget, landedLimit, deliveryPerItem, alternatives, fixedDelivery, 1);
        }
        public Demand(SubjectId buyer, int quantity, FixedScalar budget, FixedScalar landedLimit,
                      FixedScalar deliveryPerItem, List<Offer> alternatives) {
            this(buyer, quantity, budget, landedLimit, deliveryPerItem, alternatives, FixedScalar.ZERO);
        }
        public Demand {
            Objects.requireNonNull(buyer); Objects.requireNonNull(budget); Objects.requireNonNull(landedLimit);
            Objects.requireNonNull(deliveryPerItem); alternatives = List.copyOf(alternatives);
            Objects.requireNonNull(fixedDelivery);
            if (quantity < 1 || budget.raw() <= 0 || landedLimit.raw() <= 0 || deliveryPerItem.raw() < 0
                    || fixedDelivery.raw() < 0 || minimumQuantity < 1
                    || alternatives.stream().map(Offer::seller).distinct().count() != alternatives.size())
                throw new IllegalArgumentException("invalid funded demand");
        }
    }
    public record Market(List<Demand> buyers) {
        public Market {
            buyers = buyers.stream().sorted(Comparator.comparing(Demand::landedLimit).reversed()
                    .thenComparing(Demand::buyer)).toList();
            if (buyers.stream().map(Demand::buyer).distinct().count() != buyers.size())
                throw new IllegalArgumentException("duplicate buyer budget");
        }
        public static Market empty() { return new Market(List.of()); }
    }
    /** Compare price breakpoints; never repeatedly discount for elapsed time. */
    public static FixedScalar ask(FixedScalar previous, long surplus, Market market) {
        return ask(previous, surplus, market, FixedScalar.ZERO);
    }
    /** Retaining stock is worth its owner-declared opportunity value. This is
     * not a seed-price floor, a wage payment or authority to produce more. */
    public static FixedScalar ask(FixedScalar previous, long surplus, Market market, FixedScalar retainedValue) {
        if (retainedValue.raw() < 0 || surplus < 0) throw new IllegalArgumentException("negative sale valuation");
        long minimum = Math.max(1, retainedValue.raw());
        long anchor = Math.max(previous.raw(), minimum);
        if (surplus == 0 || market.buyers().isEmpty()) return new FixedScalar(anchor);
        var candidates = new TreeSet<Long>(); candidates.add(anchor); candidates.add(minimum);
        for (var buyer : market.buyers()) {
            long cash = Math.max(0, buyer.budget().raw() - buyer.fixedDelivery().raw());
            long ceiling = Math.min(buyer.landedLimit().raw() - Math.ceilDiv(buyer.fixedDelivery().raw(),
                    Math.min(surplus, buyer.quantity())), cash) - buyer.deliveryPerItem().raw();
            if (ceiling > 0) candidates.add(ceiling);
            long quantity = Math.min(surplus, buyer.quantity());
            long fullLot = Math.min(buyer.landedLimit().raw() - Math.ceilDiv(buyer.fixedDelivery().raw(), quantity),
                    cash / quantity) - buyer.deliveryPerItem().raw();
            if (fullLot > 0) candidates.add(fullLot);
            for (var other : buyer.alternatives()) {
                long competitive = other.landedPrice().raw() - buyer.deliveryPerItem().raw() - 1;
                if (competitive > 0 && competitive <= ceiling) candidates.add(competitive);
            }
            if (buyer.fixedDelivery().raw() > 0) {
                // A smaller affordable cargo changes freight per item. Include
                // those bounded lot breakpoints instead of jumping from an
                // unfillable large-lot ask straight to the retention floor.
                for (long count = buyer.minimumQuantity(); count <= Math.min(64, quantity); count++) {
                    long freight = Math.addExact(buyer.deliveryPerItem().raw(), Math.ceilDiv(buyer.fixedDelivery().raw(), count));
                    long price = Math.min(buyer.landedLimit().raw() - freight,
                            cash / count - buyer.deliveryPerItem().raw());
                    if (price > 0) candidates.add(price);
                    for (var other : buyer.alternatives()) {
                        long competitive = other.landedPrice().raw() - freight - 1;
                        if (competitive > 0 && competitive <= price) candidates.add(competitive);
                    }
                }
            }
        }
        long best = anchor; var revenue = gain(best, surplus, market, retainedValue.raw());
        for (long candidate : candidates) {
            if (candidate < minimum) continue;
            var value = gain(candidate, surplus, market, retainedValue.raw());
            // Keep the existing quote on equal proceeds: no churn without a benefit.
            if (value.compareTo(revenue) > 0 || value.equals(revenue) && retainedValue.raw() > 0
                    && sold(candidate, surplus, market) > sold(best, surplus, market)) { best = candidate; revenue = value; }
        }
        return new FixedScalar(best);
    }
    private static BigInteger gain(long ask, long surplus, Market market, long retained) {
        return BigInteger.valueOf(ask - retained).multiply(BigInteger.valueOf(sold(ask, surplus, market)));
    }
    private static long sold(long ask, long surplus, Market market) {
        long sold = 0;
        var competingUsed = new HashMap<SubjectId, Long>();
        for (var buyer : market.buyers()) {
            if (sold == surplus) break;
            long unit = Math.addExact(ask, buyer.deliveryPerItem().raw());
            long ownLot = Math.min(surplus - sold, Math.min(buyer.quantity(),
                    Math.max(0, buyer.budget().raw() - buyer.fixedDelivery().raw()) / unit));
            long landed = ownLot == 0 ? Long.MAX_VALUE : Math.addExact(unit, Math.ceilDiv(buyer.fixedDelivery().raw(), ownLot));
            boolean affordableLimit = landed <= buyer.landedLimit().raw();
            long remaining = buyer.quantity(), budget = buyer.budget().raw();
            for (var other : buyer.alternatives().stream().sorted(Comparator.comparing(Offer::landedPrice)
                    .thenComparing(Offer::seller)).toList()) {
                if (other.landedPrice().raw() > landed) break;
                long count = Math.min(remaining, Math.min(budget / other.landedPrice().raw(),
                        Math.max(0, other.quantity() - competingUsed.getOrDefault(other.seller(), 0L))));
                remaining -= count; budget -= count * other.landedPrice().raw();
                competingUsed.merge(other.seller(), count, Math::addExact);
            }
            long take = Math.min(surplus - sold, Math.min(remaining, Math.max(0, budget - buyer.fixedDelivery().raw()) / unit));
            if (affordableLimit && take >= buyer.minimumQuantity()
                    && Math.addExact(unit, Math.ceilDiv(buyer.fixedDelivery().raw(), take)) <= buyer.landedLimit().raw())
                sold += take;
        }
        return sold;
    }
    private SellerPriceDiscovery() { }
}
