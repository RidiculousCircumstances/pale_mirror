package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Cheap optimistic material closure before detailed allocation. Presence here
 * grants no quantity: labour, fuel, prices and competing uses still need the solver.
 * Absence proves that neither stock nor a remaining recipe chain can supply an item. */
public final class ProductionReachability {
    public static Set<String> items(List<SupplyPlanner.Process> processes, Map<String, Long> stock,
            Map<String, Long> usedRecipes, long deadline) {
        if (deadline < 0 || stock.values().stream().anyMatch(n -> n < 0)
                || usedRecipes.values().stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("invalid production reachability facts");
        var reachable = new HashSet<String>();
        stock.forEach((item, quantity) -> { if (quantity > 0) reachable.add(item); });
        var remaining = processes.stream().filter(p -> p.recipesAvailableBy(deadline)
                > usedRecipes.getOrDefault(p.id(), 0L)).toList();
        boolean changed;
        do {
            changed = false;
            for (var process : remaining) {
                if (reachable.containsAll(process.inputs().keySet())) changed |= reachable.add(process.product());
            }
        } while (changed);
        return Set.copyOf(reachable);
    }
    /** Products that could consume this material, directly or through recipes.
     * Include fuel edges: an export can create upstream fuel production too. */
    public static Set<String> consumers(List<SupplyPlanner.Process> processes, String item) {
        var consumers = new HashSet<String>(); consumers.add(Objects.requireNonNull(item));
        boolean changed;
        do {
            changed = false;
            for (var process : processes) {
                if (process.inputs().keySet().stream().anyMatch(consumers::contains)
                        || process.fuels().keySet().stream().anyMatch(consumers::contains))
                    changed |= consumers.add(process.product());
            }
        } while (changed);
        return Set.copyOf(consumers);
    }
    private ProductionReachability() { }
}
