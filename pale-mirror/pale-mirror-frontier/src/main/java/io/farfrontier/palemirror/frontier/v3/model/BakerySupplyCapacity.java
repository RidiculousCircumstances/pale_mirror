package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Bakery-owned capability/timing adapter; neither reserves fuel nor grants machine custody. */
final class BakerySupplyCapacity implements SupplyCapacityPort {
    @Override public Key key() { return Key.BAKERY; }
    @Override public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) {
        var capacity = BakeryProcessingForecast.capacity(state, home);
        if (capacity.isEmpty()) return List.of();
        var recipe = BakeryRecipes.bread(state);
        var input = recipe.ingredient().acceptedItems().stream().sorted(Comparator
                .<String>comparingLong(item -> stocks.containsKey(item) ? stocks.get(item).covered() : 0).reversed()
                .thenComparing(java.util.function.Function.identity())).findFirst().orElseThrow();
        var fuels = new TreeMap<String, Long>();
        state.bootstrap().ruleset().production().fuels().forEach(f -> fuels.put(f.itemKind(), f.heatUnits()));
        long heat = state.productionStations().require(capacity.orElseThrow().station()).storedHeat();
        return List.of(new SupplyPlanner.Process(recipe.id().value(), recipe.product().itemKind(), recipe.product().quantity(),
                Map.of(input, recipe.ingredient().quantity()), recipe.heatPerBatch(), fuels, heat,
                capacity.orElseThrow().firstOutputDelay(), capacity.orElseThrow().batchTicks(),
                state.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumOrderItems()));
    }
}
