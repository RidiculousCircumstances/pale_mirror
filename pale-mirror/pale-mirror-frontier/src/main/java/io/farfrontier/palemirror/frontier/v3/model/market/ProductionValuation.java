package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.math.BigInteger;
import java.util.*;

/** Material replacement valuation, not wages, inventory, utility or a production executor. */
public final class ProductionValuation {
    public record Fuel(FixedScalar price, long heatUnits) {
        public Fuel { Objects.requireNonNull(price); if (price.raw() <= 0 || heatUnits < 1) throw new IllegalArgumentException("invalid valued fuel"); }
    }
    /** Costs are amortized across batches: one coal's unused heat is not charged again to each loaf. */
    public static Optional<FixedScalar> unitCost(Map<String, Integer> inputs, int output, long heat,
                                                 Map<String, FixedScalar> prices, List<Fuel> fuels) {
        if (inputs.isEmpty() || output < 1 || heat < 0) throw new IllegalArgumentException("invalid production valuation");
        BigInteger cost = BigInteger.ZERO;
        for (var entry : inputs.entrySet()) {
            var price = prices.get(entry.getKey());
            if (price == null) return Optional.empty();
            if (price.raw() <= 0 || entry.getValue() < 1) throw new IllegalArgumentException("invalid input price or quantity");
            cost = cost.add(BigInteger.valueOf(price.raw()).multiply(BigInteger.valueOf(entry.getValue())));
        }
        if (heat > 0) {
            var fuel = fuels.stream().min((a, b) -> BigInteger.valueOf(a.price().raw()).multiply(BigInteger.valueOf(b.heatUnits()))
                    .compareTo(BigInteger.valueOf(b.price().raw()).multiply(BigInteger.valueOf(a.heatUnits())))).orElse(null);
            if (fuel == null) return Optional.empty();
            var energy = BigInteger.valueOf(fuel.price().raw()).multiply(BigInteger.valueOf(heat));
            var divisor = BigInteger.valueOf(fuel.heatUnits());
            cost = cost.add(energy.add(divisor).subtract(BigInteger.ONE).divide(divisor));
        }
        var divisor = BigInteger.valueOf(output);
        return Optional.of(new FixedScalar(cost.add(divisor).subtract(BigInteger.ONE).divide(divisor).longValueExact()));
    }
    private ProductionValuation() { }
}
