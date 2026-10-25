package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.math.BigInteger;
import java.util.*;

/** Static response to visible orders, not a sale or a guarantee of future revenue. */
public final class SellerPriceDiscovery {
    public record Offer(SubjectId seller, int quantity, FixedScalar goodsPrice, FixedScalar resourceFreight,
                        FixedScalar cashFreight, long arrivalDelayTicks, long workerTicks, int minimumQuantity,
                        ProcurementSelection.Departure departure) {
        public Offer(SubjectId seller, int quantity, FixedScalar landedPrice) {
            this(seller, quantity, landedPrice, FixedScalar.ZERO, FixedScalar.ZERO, 0, 0, 1, ProcurementSelection.Departure.READY);
        }
        public Offer(SubjectId seller, int quantity, FixedScalar landedPrice, FixedScalar cashPrice) {
            this(seller, quantity, cashPrice, new FixedScalar(Math.multiplyExact(landedPrice.raw() - cashPrice.raw(), quantity)), FixedScalar.ZERO,
                    0, 0, 1, ProcurementSelection.Departure.READY);
        }
        public FixedScalar landedPrice() { return goodsPrice.plus(new FixedScalar(Math.ceilDiv(resourceFreight.raw(), quantity))); }
        public FixedScalar cashPrice() { return goodsPrice.plus(new FixedScalar(Math.ceilDiv(cashFreight.raw(), quantity))); }
        public Offer {
            Objects.requireNonNull(seller); Objects.requireNonNull(goodsPrice); Objects.requireNonNull(resourceFreight);
            Objects.requireNonNull(cashFreight); Objects.requireNonNull(departure);
            if (quantity < 1 || goodsPrice.raw() < 1 || cashFreight.raw() < 0 || resourceFreight.compareTo(cashFreight) < 0
                    || arrivalDelayTicks < 0 || workerTicks < 0 || minimumQuantity < 1) throw new IllegalArgumentException("invalid competing offer");
        }
    }
    /** Timed whole-lot advice uses the same buyer choice as actual procurement. */
    public record Demand(SubjectId buyer, int quantity, FixedScalar budget, FixedScalar landedLimit,
                         FixedScalar deliveryPerItem, List<Offer> alternatives, FixedScalar fixedDelivery, int minimumQuantity, FixedScalar cashDelivery,
                         long deadlineTicks, long arrivalDelayTicks, long workerTicks, long labourValuePerTick,
                         ProcurementSelection.Departure departure) {
        public Demand(SubjectId buyer, int quantity, FixedScalar budget, FixedScalar landedLimit, FixedScalar deliveryPerItem,
                      List<Offer> alternatives, FixedScalar fixedDelivery, int minimumQuantity, FixedScalar cashDelivery) {
            this(buyer, quantity, budget, landedLimit, deliveryPerItem, alternatives, fixedDelivery, minimumQuantity, cashDelivery,
                    Long.MAX_VALUE, 0, 0, 0, ProcurementSelection.Departure.READY);
        }
        public Demand(SubjectId buyer, int quantity, FixedScalar budget, FixedScalar landedLimit, FixedScalar deliveryPerItem,
                      List<Offer> alternatives, FixedScalar fixedDelivery, int minimumQuantity) {
            this(buyer, quantity, budget, landedLimit, deliveryPerItem, alternatives, fixedDelivery, minimumQuantity, fixedDelivery);
        }
        public Demand(SubjectId buyer, int quantity, FixedScalar budget, FixedScalar landedLimit, FixedScalar deliveryPerItem,
                      List<Offer> alternatives, FixedScalar fixedDelivery) {
            this(buyer, quantity, budget, landedLimit, deliveryPerItem, alternatives, fixedDelivery, 1);
        }
        public Demand(SubjectId buyer, int quantity, FixedScalar budget, FixedScalar landedLimit, FixedScalar deliveryPerItem, List<Offer> alternatives) {
            this(buyer, quantity, budget, landedLimit, deliveryPerItem, alternatives, FixedScalar.ZERO);
        }
        public Demand {
            Objects.requireNonNull(buyer); Objects.requireNonNull(budget); Objects.requireNonNull(landedLimit);
            Objects.requireNonNull(deliveryPerItem); alternatives = List.copyOf(alternatives);
            Objects.requireNonNull(fixedDelivery); Objects.requireNonNull(cashDelivery); Objects.requireNonNull(departure);
            if (quantity < 1 || quantity > 4096 || budget.raw() <= 0 || landedLimit.raw() <= 0 || deliveryPerItem.raw() < 0
                    || fixedDelivery.raw() < 0 || cashDelivery.raw() < 0 || cashDelivery.compareTo(fixedDelivery) > 0 || minimumQuantity < 1
                    || deadlineTicks < 0 || arrivalDelayTicks < 0 || workerTicks < 0 || labourValuePerTick < 0
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
        var candidates = candidates(anchor, minimum, surplus, market);
        long best = anchor; var revenue = gain(best, surplus, market, retainedValue.raw());
        for (long candidate : candidates) {
            if (candidate < minimum) continue;
            var value = gain(candidate, surplus, market, retainedValue.raw());
            // Equal proceeds do not justify giving away more of the owner's stock.
            if (value.compareTo(revenue) > 0 || value.equals(revenue)
                    && sold(candidate, surplus, market) < sold(best, surplus, market)) { best = candidate; revenue = value; }
        }
        return new FixedScalar(best);
    }
    static SortedSet<Long> candidates(long anchor, long minimum, long surplus, Market market) {
        var candidates = new TreeSet<Long>(); candidates.add(anchor); candidates.add(minimum);
        for (var buyer : market.buyers()) {
            long cash = Math.max(0, buyer.budget().raw() - buyer.cashDelivery().raw());
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
                for (long count = buyer.minimumQuantity(); count <= Math.min(4096, quantity); count++) {
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
        return candidates;
    }
    static long sold(long ask, long surplus, Market market) { return SellerMarketAllocation.sold(ask, surplus, market); }
    private static BigInteger gain(long ask, long surplus, Market market, long retained) {
        return BigInteger.valueOf(ask - retained).multiply(BigInteger.valueOf(sold(ask, surplus, market)));
    }
    private SellerPriceDiscovery() { }
}
