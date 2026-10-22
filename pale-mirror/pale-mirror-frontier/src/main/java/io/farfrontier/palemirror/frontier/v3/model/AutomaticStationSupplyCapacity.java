package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Installed automatic equipment advertises finite throughput and actual stored energy to the common planner. */
final class AutomaticStationSupplyCapacity implements SupplyCapacityPort {
    @Override public Key key() { return Key.AUTOMATIC_STATIONS; }
    @Override public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
                                                    SupplyPlanningWindow window) {
        var result = new ArrayList<SupplyPlanner.Process>(); var catalog = state.bootstrap().ruleset().production();
        for (var policy : catalog.automaticPolicies()) {
            var recipe = catalog.require(policy.recipeId());
            for (var record : AutomaticStationProduction.stations(state, home, recipe)) {
                var station = record.productionStation().orElseThrow();
                var machine = state.productionStations().require(station.id());
                if (ReferenceContainerCustody.blocksCanonicalUse(state, record.id())) continue;
                var fuels = new TreeMap<String, Long>(); catalog.fuels().forEach(f -> fuels.put(f.itemKind(), f.heatUnits()));
                var ingredient = recipe.ingredient().acceptedItems().stream().sorted(Comparator
                        .<String>comparingLong(kind -> stocks.containsKey(kind) ? stocks.get(kind).covered() : 0).reversed()
                        .thenComparing(java.util.function.Function.identity())).findFirst().orElseThrow();
                long duration = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE).workUnits();
                long occupied = machine.operation().filter(operation -> !operation.transformed())
                        .map(operation -> Math.multiplyExact(duration, operation.inputQuantity() / recipe.ingredient().quantity())).orElse(0L);
                long first = Math.addExact(occupied, duration);
                int maximum = (int) Math.min(4096, Math.max(1, window.ticks() / duration));
                result.add(new SupplyPlanner.Process(recipe.id().value() + "/" + station.id().value(), recipe.product().itemKind(),
                        recipe.product().quantity(), Map.of(ingredient, recipe.ingredient().quantity()), recipe.heatPerBatch(), fuels,
                        ProductionStationEnergy.suppliedHeat(state, station.id()), first, duration, maximum, 0, Map.of(), 1));
            }
        }
        return List.copyOf(result);
    }
    @Override public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) {
        return List.of(); // Active inputs are already held by the station; committed output comes from StockSupplyPort.
    }
}
