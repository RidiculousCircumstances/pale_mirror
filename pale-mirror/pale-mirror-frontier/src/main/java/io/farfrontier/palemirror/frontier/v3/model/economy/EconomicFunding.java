package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** One shared spending envelope. Future liquidity is advice, never a second financial hold. */
final class EconomicFunding {
    record Allocation(Map<String, Long> budgets, long liquidity, long unfunded) { }
    Allocation allocate(EconomicSnapshot facts, SupplyPlanner.Plan supply, EconomicRequirements.Projection horizon, long horizonTicks) {
        var prices = new TreeMap<>(facts.replacementPrices());
        var quoted = new TreeMap<String, Long>();
        for (var offer : facts.market().offers()) quoted.merge(offer.item(), offer.cashPerItem(), Math::min);
        prices.putAll(quoted); // An old cheaper reference cannot fund a currently dearer offer.
        var restart = new TreeMap<String, Long>(); var requested = new TreeMap<String, Long>();
        var processes = new HashMap<String, SupplyPlanner.Process>(); facts.processes().forEach(p -> processes.put(p.id(), p));
        for (var entry : supply.demands().entrySet()) {
            String item = entry.getKey(); var demand = entry.getValue(); var stock = facts.stocks().get(item);
            long minimum = Math.min(demand.purchase(), Math.max(0, demand.protectedInput() - stock.covered()));
            restart.put(item, Math.multiplyExact(minimum, prices.get(item)));
            var process = demand.process().map(processes::get);
            // A missing manufactured output and its inputs are alternatives, not two
            // independent shopping lists. Explicitly selected finished purchases remain.
            boolean manufacture = process.filter(p -> !p.inputs().isEmpty() || p.heatPerBatch() > 0).isPresent();
            long current = manufacture ? Math.max(0, demand.purchase() - demand.recoveryProduction()) : demand.purchase();
            long future = manufacture ? 0 : Math.max(0, horizon.missing().get(item) - EconomicForecast.directOutputBy(facts, supply, item, horizonTicks));
            long spending = Math.max(supply.preferredPurchaseSpending().getOrDefault(item, 0L),
                    Math.multiplyExact(Math.max(current, future), prices.get(item)));
            requested.put(item, Math.max(restart.get(item), spending));
        }
        long available = facts.market().availableMoney(); var budgets = new TreeMap<String, Long>();
        long restartTotal = restart.values().stream().reduce(0L, Math::addExact);
        if (restartTotal <= available) {
            budgets.putAll(restart); available -= restartTotal;
        } else {
            // An unaffordable input bundle cannot consume the last money while
            // preventing direct survival purchases. No imaginary complete restart.
            for (var entry : facts.stocks().entrySet()) if (entry.getValue().policy().essential())
                requested.merge(entry.getKey(), Math.multiplyExact(supply.require(entry.getKey()).purchase(), prices.get(entry.getKey())), Math::max);
        }
        var order = new ArrayList<>(requested.keySet());
        order.sort(Comparator.<String>comparingInt(item -> priority(facts, supply, item, restartTotal > facts.market().availableMoney()))
                .thenComparingLong(item -> supply.require(item).deadlineTicks()).thenComparing(java.util.function.Function.identity()));
        for (var item : order) {
            long missing = Math.max(0, requested.get(item) - budgets.getOrDefault(item, 0L));
            long allocation = Math.min(available, missing);
            budgets.merge(item, allocation, Math::addExact); available -= allocation;
        }
        long liquidity = 0;
        for (var entry : budgets.entrySet()) {
            long current = Math.multiplyExact(supply.require(entry.getKey()).purchase(), prices.get(entry.getKey()));
            liquidity = Math.addExact(liquidity, Math.max(0, entry.getValue() - current));
        }
        long cost = requested.values().stream().reduce(0L, Math::addExact);
        return new Allocation(Map.copyOf(budgets), liquidity, Math.max(0, cost - facts.market().availableMoney()));
    }
    private static int priority(EconomicSnapshot facts, SupplyPlanner.Plan plan, String item, boolean restartImpossible) {
        var stock = facts.stocks().get(item); var demand = plan.require(item);
        if (restartImpossible && stock.policy().essential()) return 0;
        if (demand.protectedInput() > stock.covered()) return 1;
        if (stock.policy().essential() && demand.deadlineTicks() <= stock.policy().leadTicks()) return 2;
        if (facts.flows().stream().anyMatch(flow -> flow.inputsPerOutput().containsKey(item))
                && facts.processes().stream().noneMatch(process -> process.product().equals(item))) return 3;
        return 4;
    }
}
