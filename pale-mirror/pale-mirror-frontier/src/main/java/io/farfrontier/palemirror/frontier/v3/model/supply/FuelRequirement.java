package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Pure conversion of an uncovered energy obligation to a declared fuel alternative. */
public record FuelRequirement(String item, long quantity) {
    public FuelRequirement {
        Objects.requireNonNull(item);
        if (item.isBlank() || quantity < 1) throw new IllegalArgumentException("invalid fuel requirement");
    }
    public static FuelRequirement select(long heat, Map<String, Long> fuels, Map<String, SupplyPlanner.Stock> stocks) {
        if (heat < 1 || fuels.isEmpty() || fuels.values().stream().anyMatch(value -> value < 1))
            throw new IllegalArgumentException("positive heat and declared fuels required");
        var selected = fuels.entrySet().stream().sorted(Comparator
                .<Map.Entry<String, Long>>comparingLong(e -> Math.max(0,
                        Math.ceilDiv(heat, e.getValue()) - Objects.requireNonNull(stocks.get(e.getKey()), "undeclared fuel").covered()))
                .thenComparing(Map.Entry::getKey)).findFirst().orElseThrow();
        return new FuelRequirement(selected.getKey(), Math.ceilDiv(heat, selected.getValue()));
    }
}
