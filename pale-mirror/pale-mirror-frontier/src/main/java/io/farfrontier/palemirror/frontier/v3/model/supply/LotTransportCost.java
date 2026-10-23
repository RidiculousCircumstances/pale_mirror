package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Finite capacity breakpoints. Economic opportunity cost and actual cash are
 * distinct; no vehicle, worker, stock or budget is reserved by this quotation. */
public record LotTransportCost(List<Option> options) {
    public record Cost(long economic, long cash) {
        public Cost { if (economic < 0 || cash < 0 || cash > economic) throw new IllegalArgumentException("invalid transport cost"); }
    }
    public record Option(long capacity, long unitsPerCharge, long economicCharge, long cashCharge) {
        public Option {
            if (capacity < 1 || unitsPerCharge < 1 || economicCharge < 0 || cashCharge < 0 || cashCharge > economicCharge)
                throw new IllegalArgumentException("invalid transport capacity/cost");
        }
        public Cost quote(long quantity) {
            if (quantity < 0 || quantity > capacity) throw new IllegalArgumentException("load exceeds quoted capacity");
            long charges = Math.ceilDiv(quantity, unitsPerCharge);
            return new Cost(Math.multiplyExact(charges, economicCharge), Math.multiplyExact(charges, cashCharge));
        }
    }
    public LotTransportCost {
        options = List.copyOf(options);
        if (options.isEmpty() || options.size() > 64) throw new IllegalArgumentException("unbounded transport options");
    }
    public Cost quote(long quantity) {
        if (quantity < 0) throw new IllegalArgumentException("negative load");
        return options.stream().filter(o -> o.capacity() >= quantity).map(o -> o.quote(quantity))
                .min(Comparator.comparingLong(Cost::economic).thenComparingLong(Cost::cash))
                .orElseThrow(() -> new IllegalArgumentException("no quoted load capacity"));
    }
    public record Purchase(long added, Cost totalTransport) { }
    /** Extend an uncommitted planning allocation. Each tariff is monotone, even
     * when the cheapest funded mode changes at a capacity/cash breakpoint. */
    public Optional<Purchase> funded(long priorQuantity, Cost priorTransport, long maximumAdded,
            long unitPrice, long availableCash) {
        if (priorQuantity < 0 || maximumAdded < 0 || unitPrice < 1 || availableCash < 0)
            throw new IllegalArgumentException("invalid funded load request");
        Purchase best = null;
        var budget = java.math.BigInteger.valueOf(availableCash).add(java.math.BigInteger.valueOf(priorTransport.cash()));
        for (var option : options) {
            if (option.capacity() <= priorQuantity) continue;
            long low = 0, high = Math.min(maximumAdded, option.capacity() - priorQuantity);
            while (low < high) {
                long middle = low + (high - low + 1) / 2;
                var cost = java.math.BigInteger.valueOf(middle).multiply(java.math.BigInteger.valueOf(unitPrice))
                        .add(java.math.BigInteger.valueOf(option.quote(priorQuantity + middle).cash()));
                if (cost.compareTo(budget) <= 0) low = middle; else high = middle - 1;
            }
            if (low == 0) continue;
            var candidate = new Purchase(low, option.quote(priorQuantity + low));
            if (best == null || candidate.added() > best.added() || candidate.added() == best.added()
                    && (candidate.totalTransport().economic() < best.totalTransport().economic()
                        || candidate.totalTransport().economic() == best.totalTransport().economic()
                            && candidate.totalTransport().cash() < best.totalTransport().cash())) best = candidate;
        }
        return Optional.ofNullable(best);
    }
    public static LotTransportCost fixed(long capacity, long economic, long cash) {
        return new LotTransportCost(List.of(new Option(capacity, capacity, economic, cash)));
    }
}
