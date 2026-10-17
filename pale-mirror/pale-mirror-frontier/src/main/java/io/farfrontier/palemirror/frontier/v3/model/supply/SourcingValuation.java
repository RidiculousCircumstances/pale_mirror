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
        offers.forEach(offer -> prices.merge(offer.item(), Math.addExact(offer.deliveredCostPerItem(),
                Math.ceilDiv(offer.tripCost(), offer.quantity())), Math::min));
        var result = new TreeMap<String, Long>();
        var visiting = new HashSet<String>();
        for (var process : capacities) cost(process, capacities, prices, result, visiting, labourValuePerTick);
        return Map.copyOf(result);
    }
    private static long cost(SupplyPlanner.Process process, List<SupplyPlanner.Process> capacities,
            Map<String, Long> prices, Map<String, Long> costs, Set<String> visiting, long labour) {
        if (costs.containsKey(process.id())) return costs.get(process.id());
        if (!visiting.add(process.product())) throw new IllegalArgumentException("cyclic sourcing capability");
        // Long-run unit valuation amortizes an operation over its actual recipe capacity.
        // Forecast/admission still charge a full operation for a partial batch.
        long total = Math.ceilDiv(Math.multiplyExact(process.labourTicksPerOperation(), labour), process.recipesPerOperation());
        for (var input : process.inputs().entrySet())
            total = Math.addExact(total, Math.multiplyExact(input.getValue(),
                    replacement(input.getKey(), capacities, prices, costs, visiting, labour)));
        if (process.heatPerBatch() > 0) {
            long fuel = Long.MAX_VALUE;
            for (var entry : process.fuels().entrySet()) {
                long unit = replacement(entry.getKey(), capacities, prices, costs, visiting, labour);
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
            Map<String, Long> costs, Set<String> visiting, long labour) {
        long result = Objects.requireNonNull(prices.get(item), "missing explicit replacement estimate " + item);
        for (var candidate : capacities) if (candidate.product().equals(item))
            result = Math.min(result, cost(candidate, capacities, prices, costs, visiting, labour));
        return result;
    }
}
