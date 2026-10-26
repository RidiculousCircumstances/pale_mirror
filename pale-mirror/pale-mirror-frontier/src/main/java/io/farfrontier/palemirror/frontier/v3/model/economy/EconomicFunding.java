package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** One shared spending envelope. Future liquidity is advice, never a second financial hold. */
final class EconomicFunding {
    record Allocation(Map<String, Long> budgets, long liquidity, long unfunded,
                      Map<String, Long> prices, Set<String> fundedRestarts, boolean restartCashShortfall, Map<String, Long> operatingBudgets) { }
    Allocation allocate(EconomicSnapshot facts, SupplyPlanner.Assessment assessment, EconomicRequirements.Projection horizon, long horizonTicks) {
        var supply = assessment.plan();
        var prices = new TreeMap<>(facts.replacementPrices());
        var quoted = new TreeMap<String, Long>();
        for (var offer : facts.market().offers()) quoted.merge(offer.item(),
                Math.addExact(offer.cashPerItem(), Math.ceilDiv(offer.tripCashCost(), offer.quantity())), Math::min);
        prices.putAll(quoted); // An old cheaper reference cannot fund a currently dearer offer.
        var forecastPrices = Map.copyOf(prices);
        var restart = new TreeMap<String, Long>(); var requested = new TreeMap<String, Long>();
        var restartQuantities = new TreeMap<String, Long>();
        var currentCosts = new TreeMap<String, Long>();
        var desiredCosts = new TreeMap<String, Long>();
        var processes = new HashMap<String, SupplyPlanner.Process>(); facts.processes().forEach(p -> processes.put(p.id(), p));
        for (var entry : supply.demands().entrySet()) {
            String item = entry.getKey(); var demand = entry.getValue(); var stock = facts.stocks().get(item);
            long minimum = Math.min(demand.purchase(), Math.max(0, demand.protectedInput() - stock.covered()));
            restartQuantities.put(item, minimum);
            var actual = SourcingPurchaseCost.quote(facts.market(), item, minimum, demand.deadlineTicks());
            // Missing a consumption deadline does not make a purchasable restart
            // unfunded. Preserve its money; timing/dispatch still report lateness.
            if (actual.quantity() < minimum)
                actual = SourcingPurchaseCost.quote(facts.market(), item, minimum, Long.MAX_VALUE);
            if (minimum > 0 && actual.quantity() == minimum) {
                prices.put(item, Math.ceilDiv(actual.cash(), minimum));
            }
            restart.put(item, actual.quantity() == minimum ? actual.cash()
                    : SourcingPurchaseCost.forecast(facts.market(), item, minimum, prices.get(item)));
            var process = demand.process().map(processes::get);
            // A missing manufactured output and its inputs are alternatives, not two
            // independent shopping lists. Explicitly selected finished purchases remain.
            boolean manufacture = process.filter(p -> !p.inputs().isEmpty() || p.heatPerBatch() > 0).isPresent();
            long current = manufacture ? Math.max(0, demand.purchase() - demand.recoveryProduction()) : demand.purchase();
            long future = manufacture ? 0 : Math.max(0, horizon.missing().get(item) - EconomicForecast.directOutputBy(facts, supply, item, horizonTicks));
            long spending = Math.max(supply.preferredPurchaseSpending().getOrDefault(item, 0L),
                    SourcingPurchaseCost.forecast(facts.market(), item, Math.max(current, future), forecastPrices.get(item)));
            currentCosts.put(item, SourcingPurchaseCost.forecast(facts.market(), item, demand.purchase(), forecastPrices.get(item)));
            desiredCosts.put(item, spending);
            requested.put(item, Math.max(restart.get(item), spending));
        }
        long available = facts.market().availableMoney(); var budgets = new TreeMap<String, Long>();
        long restartTotal = restart.values().stream().reduce(0L, Math::addExact);
        if (restartTotal > available) {
            // A timely but unaffordable bundle must not veto a cheaper late
            // recovery. This changes financing, never the consumption deadline.
            for (var entry : restartQuantities.entrySet()) {
                if (entry.getValue() == 0) continue;
                var late = SourcingPurchaseCost.quote(facts.market(), entry.getKey(), entry.getValue(), Long.MAX_VALUE);
                if (late.quantity() == entry.getValue() && late.cash() < restart.get(entry.getKey())) {
                    restart.put(entry.getKey(), late.cash());
                    requested.put(entry.getKey(), Math.max(late.cash(), desiredCosts.get(entry.getKey())));
                    prices.put(entry.getKey(), Math.ceilDiv(late.cash(), late.quantity()));
                }
            }
            restartTotal = restart.values().stream().reduce(0L, Math::addExact);
        }
        var operating = new TreeMap<>(restart);
        facts.renewalInputs().forEach((item, quantity) -> {
            var stock = facts.stocks().get(item);
            long arrived = stock.arrivals().stream().filter(a -> a.afterTicks() <= horizonTicks).mapToLong(SupplyPlanner.Arrival::quantity).sum();
            long protectedInput = supply.require(item).protectedInput();
            long usable = Math.max(0, Math.addExact(stock.available(), arrived) - protectedInput);
            long missing = Math.max(0, quantity - usable);
            long cost = SourcingPurchaseCost.forecast(facts.market(), item, missing, prices.get(item));
            operating.merge(item, cost, Math::addExact);
            requested.merge(item, operating.get(item), Math::max);
        });
        var funded = EconomicRestartFunding.allocate(facts, assessment);
        budgets.putAll(funded.budgets());
        prices.putAll(funded.prices());
        funded.budgets().forEach((item, cost) -> requested.merge(item, cost, Math::max));
        available -= budgets.values().stream().reduce(0L, Math::addExact);
        // An unavailable sibling cannot erase a complete quoted bundle. Conversely,
        // partial optional funding below never promotes an incomplete bundle.
        if (restartTotal > facts.market().availableMoney())
            for (var entry : facts.stocks().entrySet()) if (entry.getValue().policy().essential())
                requested.merge(entry.getKey(), currentCosts.get(entry.getKey()), Math::max);
        var order = new ArrayList<>(requested.keySet());
        boolean restartImpossible = restartTotal > facts.market().availableMoney();
        order.sort(Comparator.<String>comparingInt(item -> priority(facts, supply, item, restartImpossible))
                .thenComparingLong(item -> supply.require(item).deadlineTicks()).thenComparing(java.util.function.Function.identity()));
        for (var item : order) {
            long missing = Math.max(0, requested.get(item) - budgets.getOrDefault(item, 0L));
            long allocation = Math.min(available, missing);
            budgets.merge(item, allocation, Math::addExact); available -= allocation;
        }
        long liquidity = 0;
        for (var entry : budgets.entrySet()) {
            long current = currentCosts.get(entry.getKey());
            liquidity = Math.addExact(liquidity, Math.max(0, entry.getValue() - current));
        }
        long cost = requested.values().stream().reduce(0L, Math::addExact);
        return new Allocation(Map.copyOf(budgets), liquidity, Math.max(0, cost - facts.market().availableMoney()),
                Map.copyOf(prices), funded.items(), restartTotal > facts.market().availableMoney(), Map.copyOf(operating));
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
