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
                         FixedScalar deliveryPerItem, List<Offer> alternatives) {
        public Demand {
            Objects.requireNonNull(buyer); Objects.requireNonNull(budget); Objects.requireNonNull(landedLimit);
            Objects.requireNonNull(deliveryPerItem); alternatives = List.copyOf(alternatives);
            if (quantity < 1 || budget.raw() <= 0 || landedLimit.raw() <= 0 || deliveryPerItem.raw() < 0
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
            long ceiling = Math.min(buyer.landedLimit().raw(), buyer.budget().raw()) - buyer.deliveryPerItem().raw();
            if (ceiling > 0) candidates.add(ceiling);
            long quantity = Math.min(surplus, buyer.quantity());
            long fullLot = Math.min(buyer.landedLimit().raw(), buyer.budget().raw() / quantity) - buyer.deliveryPerItem().raw();
            if (fullLot > 0) candidates.add(fullLot);
            for (var other : buyer.alternatives()) {
                long competitive = other.landedPrice().raw() - buyer.deliveryPerItem().raw() - 1;
                if (competitive > 0 && competitive <= ceiling) candidates.add(competitive);
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
            boolean affordableLimit = ask <= buyer.landedLimit().raw() - buyer.deliveryPerItem().raw();
            long landed = affordableLimit ? ask + buyer.deliveryPerItem().raw() : buyer.landedLimit().raw();
            long remaining = buyer.quantity(), budget = buyer.budget().raw();
            for (var other : buyer.alternatives().stream().sorted(Comparator.comparing(Offer::landedPrice)
                    .thenComparing(Offer::seller)).toList()) {
                if (other.landedPrice().raw() > landed) break;
                long count = Math.min(remaining, Math.min(budget / other.landedPrice().raw(),
                        Math.max(0, other.quantity() - competingUsed.getOrDefault(other.seller(), 0L))));
                remaining -= count; budget -= count * other.landedPrice().raw();
                competingUsed.merge(other.seller(), count, Math::addExact);
            }
            if (affordableLimit) sold += Math.min(surplus - sold, Math.min(remaining, budget / landed));
        }
        return sold;
    }
    private SellerPriceDiscovery() { }
}
