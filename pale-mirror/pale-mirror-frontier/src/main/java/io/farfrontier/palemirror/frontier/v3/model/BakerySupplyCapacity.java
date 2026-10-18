package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Bakery-owned capability/timing adapter; neither reserves fuel nor grants machine custody. */
final class BakerySupplyCapacity implements SupplyCapacityPort {
    @Override public Key key() { return Key.BAKERY; }
    @Override public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
                                                    SupplyPlanningWindow window) {
        var capacity = BakeryProcessingForecast.capacity(state, home, window);
        if (capacity.isEmpty()) return List.of();
        var recipe = BakeryRecipes.bread(state);
        var input = recipe.ingredient().acceptedItems().stream().sorted(Comparator
                .<String>comparingLong(item -> stocks.containsKey(item) ? stocks.get(item).covered() : 0).reversed()
                .thenComparing(java.util.function.Function.identity())).findFirst().orElseThrow();
        var fuels = new TreeMap<String, Long>();
        state.bootstrap().ruleset().production().fuels().forEach(f -> fuels.put(f.itemKind(), f.heatUnits()));
        var station = capacity.orElseThrow().station();
        long heat = Math.max(0, ProductionStationEnergy.suppliedHeat(state, station)
                - BakerySupplyObligations.requiredHeat(state, station));
        var timing = capacity.orElseThrow();
        int recipesPerOperation = ProductionBatchSizing.maximumRecipeBatches(recipe);
        long operations = window.ticks() < timing.firstOutputDelay() ? 1
                : Math.addExact(1, (window.ticks() - timing.firstOutputDelay()) / timing.batchTicks());
        int maximumRecipes = Math.toIntExact(Math.multiplyExact(operations, recipesPerOperation));
        return List.of(new SupplyPlanner.Process(recipe.id().value(), recipe.product().itemKind(), recipe.product().quantity(),
                Map.of(input, recipe.ingredient().quantity()), recipe.heatPerBatch(), fuels, heat,
                capacity.orElseThrow().firstOutputDelay(), capacity.orElseThrow().batchTicks(),
                maximumRecipes, capacity.orElseThrow().labourTicks(),
                state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isPresent()
                        ? capacity.orElseThrow().labourCapacity() : Map.of(), recipesPerOperation));
    }
    @Override public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
                                                                Map<String, SupplyPlanner.Stock> stocks) {
        return BakerySupplyObligations.dependencies(state, home, stocks);
    }
}
