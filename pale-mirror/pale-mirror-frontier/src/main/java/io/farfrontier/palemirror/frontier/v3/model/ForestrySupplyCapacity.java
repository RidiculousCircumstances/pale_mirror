package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Forestry-owned finite source and labour projection, backed by declared trees and tools. */
final class ForestrySupplyCapacity implements SupplyCapacityPort {
    public Key key() { return Key.FORESTRY; }
    public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window) {
        return assess(state, home, stocks, window, true);
    }
    public List<SupplyPlanner.Process> valuation(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window) {
        return assess(state, home, stocks, window, false);
    }
    private List<SupplyPlanner.Process> assess(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window, boolean backed) {
        if (!ForestryStockDemand.enabled(state, home)) return List.of();
        var rules = state.bootstrap().ruleset().forestry();
        var workers = SupplyLabourCapacity.potential(state, home, ResidentWorkKind.FORESTRY, HumanCapability.EXTRACTION);
        if (workers.isEmpty()) return List.of();
        var result = new ArrayList<SupplyPlanner.Process>();
        for (var profile : rules.harvests()) {
            if (!stocks.containsKey(profile.outputItem())) continue;
            long logs = ForestKnowledge.knownBy(state, home).stream().map(header -> ForestKnowledge.require(state, header.tree()))
                    .filter(tree -> tree.declaration().species().equals(profile.species()))
                    .mapToLong(ForestTree::remaining).sum();
            if (backed) {
                var available = EquipmentProductiveCapacity.items(state, home, workers.stream().map(ResidentProfile::id).collect(java.util.stream.Collectors.toSet()),
                        io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirements.allOf(Set.of(rules.tool())));
                logs = Math.min(logs, EquipmentProductiveCapacity.remainingOperations(state, available, rules.tool()));
            }
            var definition = state.bootstrap().ruleset().workCatalog().require(profile.logKind(), WorkOperation.EXTRACT);
            long promised = stocks.get(profile.outputItem()).promisedProduction();
            int maximum = Math.toIntExact(Math.min(4096, Math.max(0, logs - promised)));
            if (maximum == 0) continue;
            long labour = definition.workUnits();
            // A finite forecast includes approach/haul via the common navigation estimate at admission;
            // this productive capacity is not a promise of physical arrival or a new route query.
            long period = Math.max(1, labour / Math.min(workers.size(), rules.workersPerSettlement()));
            maximum = (int) Math.min(maximum, Math.max(1, window.ticks() / period));
            result.add(new SupplyPlanner.Process("forestry:" + home.value() + "/" + profile.id(), profile.outputItem(), 1,
                    Map.of(), 0, Map.of(), 0, labour, period, maximum, labour,
                    SupplyLabourCapacity.over(state, home, workers.stream().map(ResidentProfile::id).toList(), window), 1));
        }
        return List.copyOf(result);
    }
    public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }
}
