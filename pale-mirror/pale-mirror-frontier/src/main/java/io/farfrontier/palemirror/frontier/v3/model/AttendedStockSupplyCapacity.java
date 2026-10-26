package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Stock recipe capacity uses the shared finite labour envelope; a machine is not a worker. */
final class AttendedStockSupplyCapacity implements SupplyCapacityPort {
    public Key key() { return Key.ATTENDED_STOCK; }
    public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window) {
        return assess(state, home, stocks, window, true);
    }
    public List<SupplyPlanner.Process> valuation(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window) {
        return assess(state, home, stocks, window, false);
    }
    private List<SupplyPlanner.Process> assess(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
            SupplyPlanningWindow window, boolean executable) {
        var workers = executable ? SupplyLabourCapacity.candidates(state, home, ResidentWorkKind.PRODUCTION, HumanCapability.INDUSTRY,
                resident -> state.productionJobs().values().stream().anyMatch(job -> job.workerId().equals(resident.id())))
                : SupplyLabourCapacity.potential(state, home, ResidentWorkKind.PRODUCTION, HumanCapability.INDUSTRY);
        if (workers.isEmpty()) return List.of();
        var budget = SupplyLabourCapacity.over(state, home, workers.stream().map(ResidentProfile::id).toList(), window);
        var result = new ArrayList<SupplyPlanner.Process>();
        for (var policy : state.bootstrap().ruleset().production().stockPolicies()) {
            var recipe = state.bootstrap().ruleset().production().require(policy.recipeId());
            var definition = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE);
            int speed = workers.stream().mapToInt(worker -> ResidentWorkStatistics.speedPermille(worker, definition, state.bootstrap().ruleset().workCatalog())).min().orElseThrow();
            long duration = Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1000L), speed);
            var inputs = ProductionRecipeInputs.select(recipe, kind -> -(stocks.containsKey(kind) ? stocks.get(kind).covered() : 0L));
            for (var record : AutomaticStationProduction.stations(state, home, recipe)) {
                var station = record.productionStation().orElseThrow();
                if (executable && (ReferenceContainerCustody.blocksCanonicalUse(state, record.id())
                        || state.productionStations().require(station.id()).operation().isPresent())) continue;
                int maximum = (int) Math.min(4096, Math.max(1, window.ticks() / duration));
                result.add(new SupplyPlanner.Process(recipe.id().value() + "/" + station.id().value(), recipe.product().itemKind(),
                        recipe.product().quantity(), inputs, recipe.nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog()),
                        Map.of(), 0, duration, duration, maximum, duration, budget, 1));
            }
        }
        return List.copyOf(result);
    }
    public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }
}
