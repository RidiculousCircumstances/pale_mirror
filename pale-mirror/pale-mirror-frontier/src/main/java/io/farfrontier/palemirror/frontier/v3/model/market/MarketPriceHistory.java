package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.math.BigInteger;
import java.util.*;

/** Bounded completed-sale observations. No quotes, balances or inventory are owned here. */
public record MarketPriceHistory(Map<String, List<Sale>> commodities) {
    public static final int MAX_COMMODITIES = 256;
    public static final int MAX_SALES = 32;
    public record Sale(SubjectId receipt, SubjectId seller, SubjectId buyer, long atTick,
                       int quantity, FixedScalar unitPrice, FixedScalar deliveryUnitPrice) {
        public Sale(SubjectId receipt, SubjectId seller, SubjectId buyer, long atTick, int quantity, FixedScalar unitPrice) {
            this(receipt, seller, buyer, atTick, quantity, unitPrice, FixedScalar.ZERO);
        }
        public Sale {
            Objects.requireNonNull(receipt); Objects.requireNonNull(seller); Objects.requireNonNull(buyer);
            Objects.requireNonNull(unitPrice); Objects.requireNonNull(deliveryUnitPrice);
            if (seller.equals(buyer) || atTick < 0 || quantity < 1 || unitPrice.raw() <= 0 || deliveryUnitPrice.raw() < 0)
                throw new IllegalArgumentException("invalid completed market sale");
            unitPrice.plus(deliveryUnitPrice).multiply(quantity);
        }
        public FixedScalar totalPrice() { return unitPrice.plus(deliveryUnitPrice).multiply(quantity); }
    }
    public MarketPriceHistory {
        var copy = new TreeMap<String, List<Sale>>();
        if (commodities.size() > MAX_COMMODITIES) throw new IllegalArgumentException("unbounded market history");
        var receipts = new HashSet<SubjectId>();
        commodities.forEach((item, sales) -> {
            if (item == null || item.isBlank() || sales.isEmpty() || sales.size() > MAX_SALES)
                throw new IllegalArgumentException("invalid market history bucket");
            long previous = -1;
            for (var sale : sales) {
                if (sale.atTick() < previous || !receipts.add(sale.receipt()))
                    throw new IllegalArgumentException("duplicate or nonchronological sale history");
                previous = sale.atTick();
            }
            copy.put(item, List.copyOf(sales));
        });
        commodities = Map.copyOf(copy);
    }
    public static MarketPriceHistory empty() { return new MarketPriceHistory(Map.of()); }
    public MarketPriceHistory record(String item, Sale sale) {
        var next = new HashMap<>(commodities);
        var sales = new ArrayList<>(commodities.getOrDefault(item, List.of()));
        sales.add(sale);
        if (sales.size() > MAX_SALES) sales.removeFirst();
        next.put(item, sales);
        return new MarketPriceHistory(next);
    }
    /** Recency is measured relative to the last completed sale. With no new trades,
     * the reference does not drift simply because simulation time advances. */
    public Optional<FixedScalar> reference(String item, long recencyTicks) {
        if (recencyTicks < 1) throw new IllegalArgumentException("invalid market recency horizon");
        var sales = commodities.getOrDefault(item, List.of());
        if (sales.isEmpty()) return Optional.empty();
        long last = sales.getLast().atTick();
        BigInteger total = BigInteger.ZERO, weights = BigInteger.ZERO;
        for (var sale : sales) {
            long age = Math.min(recencyTicks - 1, last - sale.atTick());
            var weight = BigInteger.valueOf(recencyTicks - age).multiply(BigInteger.valueOf(sale.quantity()));
            total = total.add(weight.multiply(BigInteger.valueOf(sale.unitPrice().raw())));
            weights = weights.add(weight);
        }
        return Optional.of(new FixedScalar(total.divide(weights).longValueExact()));
    }
}
