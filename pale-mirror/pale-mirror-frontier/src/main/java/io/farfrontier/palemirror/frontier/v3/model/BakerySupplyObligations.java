package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Production owner exposes unfinished obligations, independent of new-work capacity or actor activity. */
final class BakerySupplyObligations {
    static long requiredHeat(FrontierWorldState state, SubjectId station) {
        return unfunded(state).filter(job -> job.stationWork().orElseThrow().stationId().equals(station))
                .mapToLong(job -> heat(state, job)).reduce(0, Math::addExact);
    }
    static List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
                                                     Map<String, SupplyPlanner.Stock> stocks) {
        var available = new HashMap<SubjectId, Long>();
        var fuels = new TreeMap<String, Long>();
        state.bootstrap().ruleset().production().fuels().forEach(fuel -> fuels.put(fuel.itemKind(), fuel.heatUnits()));
        // Aggregate energy by product/station before rounding to whole fuel items.
        var needed = new TreeMap<SubjectId, Map<String, Long>>();
        unfunded(state).filter(job -> job.rights().resourceOwner().id().equals(home)).forEach(job ->
                needed.computeIfAbsent(job.stationWork().orElseThrow().stationId(), ignored -> new TreeMap<>())
                        .merge(job.outputItemKind(), heat(state, job), Math::addExact));
        var result = new ArrayList<SupplyPlanner.Dependency>();
        needed.forEach((station, products) -> {
            available.put(station, ProductionStationEnergy.suppliedHeat(state, station));
            products.forEach((product, required) -> {
                long retained = available.get(station);
                available.put(station, Math.max(0, retained - required));
                if (required <= retained) return;
                var fuel = FuelRequirement.select(required - retained, fuels, stocks);
                result.add(new SupplyPlanner.Dependency(product, fuel.item(), fuel.quantity(), SupplyPlanner.Reason.PRODUCTION_FUEL));
            });
        });
        return List.copyOf(result);
    }
    private static java.util.stream.Stream<ProductionJob> unfunded(FrontierWorldState state) {
        return state.productionJobs().values().stream().filter(job -> job.stationWork().filter(work ->
                work.phase() == StationWorkState.Phase.DEPOT_PICKUP || work.phase() == StationWorkState.Phase.STATION_LOAD).isPresent());
    }
    private static long heat(FrontierWorldState state, ProductionJob job) {
        var recipe = state.bootstrap().ruleset().production().require(job.stationWork().orElseThrow().recipeId());
        return Math.multiplyExact(recipe.heatPerBatch(), job.inputCount() / recipe.ingredient().quantity());
    }
    private BakerySupplyObligations() { }
}
