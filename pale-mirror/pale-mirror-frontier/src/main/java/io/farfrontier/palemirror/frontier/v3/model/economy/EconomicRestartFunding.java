package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Funds complete owner-declared input bundles independently. Advice, never inventory or cash holds. */
final class EconomicRestartFunding {
    record Allocation(Map<String, Long> budgets, Map<String, Long> prices, Set<String> items) { }
    static Allocation allocate(EconomicSnapshot facts, SupplyPlanner.Assessment assessment) {
        var supply = assessment.plan();
        var available = new TreeMap<String, Long>();
        for (var entry : facts.stocks().entrySet()) available.put(entry.getKey(), entry.getValue().covered());
        var purchased = new TreeMap<String, Long>();
        var budgets = new TreeMap<String, Long>();
        var deadlines = new TreeMap<String, Long>();
        var order = assessment.restarts().stream().sorted(Comparator
                .<SupplyPlanner.Restart>comparingLong(restart -> supply.require(restart.product()).deadlineTicks())
                .thenComparing(restart -> !facts.stocks().get(restart.product()).policy().essential())
                .thenComparing(SupplyPlanner.Restart::product)).toList();
        for (var restart : order) {
            var remaining = new TreeMap<>(available);
            var wanted = new TreeMap<>(purchased);
            var dates = new TreeMap<>(deadlines);
            boolean obtainable = true;
            for (var entry : restart.inputs().entrySet()) {
                String item = entry.getKey(); long used = Math.min(entry.getValue(), remaining.get(item));
                remaining.put(item, remaining.get(item) - used);
                long missing = entry.getValue() - used;
                long total = Math.addExact(wanted.getOrDefault(item, 0L), missing);
                // Own production remains its own dependency, not an invented market offer.
                if (total > supply.require(item).purchase()) { obtainable = false; break; }
                if (missing > 0) {
                    wanted.put(item, total);
                    dates.merge(item, supply.require(item).deadlineTicks(), Math::min);
                }
            }
            if (!obtainable) continue;
            var quoted = quotes(facts.market(), wanted, dates, false);
            if (quoted.isEmpty() || cost(quoted.orElseThrow()) > facts.market().availableMoney())
                quoted = quotes(facts.market(), wanted, dates, true);
            if (quoted.isEmpty() || cost(quoted.orElseThrow()) > facts.market().availableMoney()) continue;
            available = remaining; purchased = wanted; deadlines = dates; budgets = quoted.orElseThrow();
        }
        var prices = new TreeMap<String, Long>();
        for (var item : purchased.keySet()) prices.put(item, Math.ceilDiv(budgets.get(item), purchased.get(item)));
        return new Allocation(Map.copyOf(budgets), Map.copyOf(prices), Set.copyOf(purchased.keySet()));
    }
    private static Optional<TreeMap<String, Long>> quotes(SourcingMarket market, Map<String, Long> quantities,
            Map<String, Long> deadlines, boolean allowLate) {
        var result = new TreeMap<String, Long>();
        for (var entry : quantities.entrySet()) {
            var quote = SourcingPurchaseCost.quote(market, entry.getKey(), entry.getValue(),
                    allowLate ? Long.MAX_VALUE : deadlines.get(entry.getKey()));
            if (quote.quantity() != entry.getValue()) return Optional.empty();
            result.put(entry.getKey(), quote.cash());
        }
        return Optional.of(result);
    }
    private static long cost(Map<String, Long> budgets) { return budgets.values().stream().reduce(0L, Math::addExact); }
    private EconomicRestartFunding() { }
}
