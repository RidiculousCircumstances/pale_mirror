package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Resource-neutral replacement and labour valuation. Biological/machine waiting is not labour. */
public final class SourcingValuation {
    private SourcingValuation() { }
    public static Map<String, Long> processes(List<SupplyPlanner.Process> capacities, Map<String, Long> references,
            List<SourcingMarket.Offer> offers, long labourValuePerTick) {
        if (labourValuePerTick < 1 || references.values().stream().anyMatch(value -> value <= 0))
            throw new IllegalArgumentException("invalid replacement valuation");
        var prices = new HashMap<>(references);
        var quoted = new HashMap<String, Long>();
        offers.forEach(offer -> quoted.merge(offer.item(), Math.addExact(offer.deliveredCostPerItem(),
                Math.ceilDiv(offer.tripCost(), offer.quantity())), Math::min));
        // A historical reference is a fallback, not an executable cheap supplier.
        // Compare current quoted replacement costs with actual local capabilities.
        prices.putAll(quoted);
        var result = new TreeMap<String, Long>();
        var visiting = new HashSet<String>();
        for (var process : capacities) cost(process, capacities, prices, quoted.keySet(), result, visiting, labourValuePerTick);
        return Map.copyOf(result);
    }
    private static long cost(SupplyPlanner.Process process, List<SupplyPlanner.Process> capacities,
            Map<String, Long> prices, Set<String> quoted, Map<String, Long> costs, Set<String> visiting, long labour) {
        if (costs.containsKey(process.id())) return costs.get(process.id());
        if (!visiting.add(process.product())) throw new IllegalArgumentException("cyclic sourcing capability");
        // Long-run unit valuation amortizes an operation over its actual recipe capacity.
        // Forecast/admission still charge a full operation for a partial batch.
        long total = Math.ceilDiv(Math.multiplyExact(process.labourTicksPerOperation(), labour), process.recipesPerOperation());
        for (var input : process.inputs().entrySet())
            total = Math.addExact(total, Math.multiplyExact(input.getValue(),
                    replacement(input.getKey(), capacities, prices, quoted, costs, visiting, labour)));
        if (process.heatPerBatch() > 0) {
            long fuel = Long.MAX_VALUE;
            for (var entry : process.fuels().entrySet()) {
                long unit = replacement(entry.getKey(), capacities, prices, quoted, costs, visiting, labour);
                fuel = Math.min(fuel, Math.ceilDiv(Math.multiplyExact(unit, process.heatPerBatch()), entry.getValue()));
            }
            total = Math.addExact(total, fuel);
        }
        visiting.remove(process.product());
        long unit = Math.max(1, Math.ceilDiv(total, process.outputPerBatch()));
        costs.put(process.id(), unit);
        return unit;
    }
    private static long replacement(String item, List<SupplyPlanner.Process> capacities, Map<String, Long> prices,
            Set<String> quoted, Map<String, Long> costs, Set<String> visiting, long labour) {
        long reference = Objects.requireNonNull(prices.get(item), "missing explicit replacement estimate " + item);
        long result = quoted.contains(item) ? reference : Long.MAX_VALUE;
        for (var candidate : capacities) if (candidate.product().equals(item))
            result = Math.min(result, cost(candidate, capacities, prices, quoted, costs, visiting, labour));
        return result == Long.MAX_VALUE ? reference : result;
    }
}
