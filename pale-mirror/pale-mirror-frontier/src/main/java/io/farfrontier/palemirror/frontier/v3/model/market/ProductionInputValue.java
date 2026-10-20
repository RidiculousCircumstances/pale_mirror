package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.math.BigInteger;
import java.util.*;

/** Residual substitution value of an ingredient or fuel, from declared recipes.
 * No resource names, physical jobs or paid fictional wages. */
public final class ProductionInputValue {
    public static long assess(String item, List<SupplyPlanner.Process> processes, Map<String, Long> prices, long labourPerTick) {
        if (labourPerTick < 0) throw new IllegalArgumentException("negative labour value");
        long best = 0;
        for (var process : processes) {
            if (!process.inputs().containsKey(item) && !process.fuels().containsKey(item)) continue;
            long output = Math.multiplyExact(Objects.requireNonNull(prices.get(process.product())), process.outputPerBatch());
            long other = Math.ceilDiv(Math.multiplyExact(process.labourTicksPerOperation(), labourPerTick), process.recipesPerOperation());
            for (var input : process.inputs().entrySet()) if (!input.getKey().equals(item))
                other = Math.addExact(other, Math.multiplyExact(input.getValue(), Objects.requireNonNull(prices.get(input.getKey()))));
            if (process.heatPerBatch() > 0 && !process.fuels().containsKey(item)) {
                long fuel = process.fuels().entrySet().stream().mapToLong(f -> Math.ceilDiv(
                        Math.multiplyExact(Objects.requireNonNull(prices.get(f.getKey())), process.heatPerBatch()), f.getValue())).min().orElseThrow();
                other = Math.addExact(other, fuel);
            }
            long remaining = Math.max(0, output - other);
            long denominator = Math.addExact(Math.multiplyExact(process.inputs().getOrDefault(item, 0).longValue(),
                    process.fuels().getOrDefault(item, 1L)), process.fuels().containsKey(item) ? process.heatPerBatch() : 0);
            if (denominator == 0) continue;
            long value = BigInteger.valueOf(remaining).multiply(BigInteger.valueOf(process.fuels().getOrDefault(item, 1L)))
                    .divide(BigInteger.valueOf(denominator)).longValueExact();
            best = Math.max(best, value);
        }
        return best;
    }
    private ProductionInputValue() { }
}
