package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.market.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Converts declared production capabilities into price constraints, without inspecting concrete jobs. */
final class GoodsProductionPricing {
    static Map<String, FixedScalar> floors(ProductionCatalog catalog, Map<String, FixedScalar> prices) {
        var result = new HashMap<String, FixedScalar>();
        var fuels = catalog.fuels().stream().filter(f -> prices.containsKey(f.itemKind()))
                .map(f -> new ProductionValuation.Fuel(prices.get(f.itemKind()), f.heatUnits())).toList();
        for (var recipe : catalog.recipes()) {
            var input = recipe.ingredient().acceptedItems().stream().filter(prices::containsKey)
                    .min(Comparator.comparing(prices::get));
            if (input.isEmpty()) continue;
            ProductionValuation.unitCost(Map.of(input.orElseThrow(), recipe.ingredient().quantity()), recipe.product().quantity(),
                    recipe.heatPerBatch(), prices, fuels).ifPresent(cost -> result.merge(recipe.product().itemKind(), cost,
                        (a, b) -> a.compareTo(b) <= 0 ? a : b));
        }
        return Map.copyOf(result);
    }
    static Map<String, FixedScalar> alternatives(FrontierWorldState state, GoodsParticipantView view,
                                                Map<String, FixedScalar> prices) {
        if (view.participant().policy() != GoodsPolicyKind.PUBLIC_SETTLEMENT || view.supplyPlan().isEmpty()) return Map.of();
        var facts = new TreeMap<String, SupplyPlanner.Stock>(); var rules = state.bootstrap().ruleset().goodsTrade();
        view.stocks().forEach((item, stock) -> facts.put(item, new SupplyPlanner.Stock(stock.unclaimedInStorage(), stock.expectedIncoming(),
                0, 0, stock.protectedMinimum(), 0, 1, rules.supplyPlanning().require(item))));
        var result = new HashMap<String, FixedScalar>();
        var home = view.participant().party().id();
        var window = SupplyPlanningWindow.forSettlement(state, home, view.supplyPlan().orElseThrow().reserves().values());
        var capacities = SupplyCapacityComposition.read(state, home, facts, window);
        var economic = state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isPresent()
                ? SettlementSourcingMarket.read(state, view.participant(), capacities).processUnitCosts() : Map.<String, Long>of();
        for (var process : capacities) {
            var demand = view.supplyPlan().orElseThrow().require(process.product());
            long batches = Math.max(1, Math.ceilDiv(demand.purchase(), process.outputPerBatch()));
            if (batches > process.maximumBatches()
                    || process.completionTicks(batches) > demand.deadlineTicks()) continue;
            if (process.inputs().entrySet().stream().anyMatch(e -> !facts.containsKey(e.getKey())
                    || facts.get(e.getKey()).covered() < Math.multiplyExact(e.getValue(), batches))) continue;
            long heat = Math.max(0, Math.multiplyExact(process.heatPerBatch(), batches) - process.availableHeat());
            var fuels = process.fuels().entrySet().stream().filter(e -> prices.containsKey(e.getKey()) && facts.containsKey(e.getKey())
                    && facts.get(e.getKey()).covered() >= Math.ceilDiv(heat, e.getValue()))
                    .map(e -> new ProductionValuation.Fuel(prices.get(e.getKey()), e.getValue())).toList();
            var inputs = new TreeMap<String, Integer>();
            process.inputs().forEach((item, quantity) -> inputs.put(item, Math.toIntExact(Math.multiplyExact(quantity, batches))));
            var cost = ProductionValuation.unitCost(inputs, Math.toIntExact(Math.multiplyExact(process.outputPerBatch(), batches)),
                    heat, prices, fuels);
            if (economic.containsKey(process.id())) cost = Optional.of(new FixedScalar(economic.get(process.id())));
            cost.filter(value -> value.raw() > 0).ifPresent(value -> result.merge(process.product(), value, (a, b) -> a.compareTo(b) <= 0 ? a : b));
        }
        return Map.copyOf(result);
    }
    private GoodsProductionPricing() { }
}
