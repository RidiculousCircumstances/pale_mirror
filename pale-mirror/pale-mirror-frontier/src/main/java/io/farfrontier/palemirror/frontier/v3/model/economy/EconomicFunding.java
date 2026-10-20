package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** One shared spending envelope. Future liquidity is advice, never a second financial hold. */
final class EconomicFunding {
    record Allocation(Map<String, Long> budgets, long liquidity, long unfunded,
                      Map<String, Long> prices, Set<String> fundedRestarts, boolean restartCashShortfall) { }
    Allocation allocate(EconomicSnapshot facts, SupplyPlanner.Plan supply, EconomicRequirements.Projection horizon, long horizonTicks) {
        var prices = new TreeMap<>(facts.replacementPrices());
        var quoted = new TreeMap<String, Long>();
        for (var offer : facts.market().offers()) quoted.merge(offer.item(),
                Math.addExact(offer.cashPerItem(), Math.ceilDiv(offer.tripCashCost(), offer.quantity())), Math::min);
        prices.putAll(quoted); // An old cheaper reference cannot fund a currently dearer offer.
        var restart = new TreeMap<String, Long>(); var requested = new TreeMap<String, Long>();
        var quotedRestarts = new HashSet<String>();
        var processes = new HashMap<String, SupplyPlanner.Process>(); facts.processes().forEach(p -> processes.put(p.id(), p));
        for (var entry : supply.demands().entrySet()) {
            String item = entry.getKey(); var demand = entry.getValue(); var stock = facts.stocks().get(item);
            long minimum = Math.min(demand.purchase(), Math.max(0, demand.protectedInput() - stock.covered()));
            var actual = quotedUnitPrice(facts, item, Math.max(1, minimum), demand.deadlineTicks());
            // Missing a consumption deadline does not make a purchasable restart
            // unfunded. Preserve its money; timing/dispatch still report lateness.
            if (actual.isEmpty() && minimum > 0)
                actual = quotedUnitPrice(facts, item, minimum, Long.MAX_VALUE);
            if (actual.isPresent()) {
                prices.put(item, actual.getAsLong());
                if (minimum > 0) quotedRestarts.add(item);
            }
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
        var restartItems = restart.entrySet().stream().filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean fullyQuoted = quotedRestarts.containsAll(restartItems);
        return new Allocation(Map.copyOf(budgets), liquidity, Math.max(0, cost - facts.market().availableMoney()),
                Map.copyOf(prices), fullyQuoted && restartTotal <= facts.market().availableMoney() ? restartItems : Set.of(),
                !restartItems.isEmpty() && restartTotal > facts.market().availableMoney());
    }
    /** A cheap single item cannot price a larger restart whose remaining supply is dearer or absent. */
    private static OptionalLong quotedUnitPrice(EconomicSnapshot facts, String item, long quantity, long deadline) {
        long remaining = quantity, cost = 0;
        for (var offer : facts.market().offers().stream().filter(o -> o.item().equals(item) && o.deliveryTicks() <= deadline)
                .sorted(Comparator.<SourcingMarket.Offer>comparingLong(o -> Math.addExact(o.cashPerItem(),
                        Math.ceilDiv(o.tripCashCost(), Math.min(quantity, o.quantity()))))
                        .thenComparing(SourcingMarket.Offer::id)).toList()) {
            long take = Math.min(remaining, offer.quantity());
            cost = Math.addExact(cost, Math.addExact(Math.multiplyExact(take, offer.cashPerItem()), offer.tripCashCost())); remaining -= take;
            if (remaining == 0) return OptionalLong.of(Math.ceilDiv(cost, quantity));
        }
        return OptionalLong.empty();
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
