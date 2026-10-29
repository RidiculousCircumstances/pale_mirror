package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import io.farfrontier.palemirror.frontier.v3.model.supply.ProductionCost;
import java.util.*;

/** Sequential declared sources are conditional production capacity, never available stock. */
final class ExtractionSupplyCapacity implements SupplyCapacityPort {
    private record ValuationQuery(SubjectId home, Set<String> products) {
        private ValuationQuery { products = Set.copyOf(products); }
    }
    private static final ImmutableKeyedInputView<ValuationQuery, List<ProductionCost>> VALUATIONS = new ImmutableKeyedInputView<>(256);
    @Override public Key key() { return Key.EXTRACTION; }
    @Override public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
                                                    SupplyPlanningWindow window) {
        return assess(state, home, stocks, window);
    }
    @Override public List<ProductionCost> valuation(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) {
        var query = new ValuationQuery(home, stocks.keySet());
        var inputs = new ArrayList<>(SupplyLabourCapacity.potentialInputs(state, home));
        inputs.add(state.extractionSites().depositsIn(home));
        return VALUATIONS.getPublished(state.bootstrap(), query, inputs, () -> valueInstalled(state, home, query.products()));
    }
    private static List<ProductionCost> valueInstalled(FrontierWorldState state, SubjectId home, Set<String> products) {
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isEmpty()
                || SupplyPlanningWindow.dailyWork(state.humanPopulation().schedule(home)) == 0) return List.of();
        var result = new ArrayList<ProductionCost>();
        for (var material : materials(state, home)) {
            if (material.coldOutput().size() != 1) continue;
            var product = material.coldOutput().getFirst();
            if (!products.contains(product.itemKind())) continue;
            var definition = state.bootstrap().ruleset().workCatalog().require(material.before().kind(), WorkOperation.EXTRACT);
            var workers = SupplyLabourCapacity.potential(state, home, ResidentWorkKind.EXTRACTION, definition.capability());
            if (workers.isEmpty()) continue;
            var forecasts = state.extractionSites().depositsIn(home).stream()
                    .map(d -> ExtractionProductionForecast.valuation(state, d, material, workers)).filter(f -> f.outputs() > 0).toList();
            long reachable = forecasts.stream().mapToLong(f -> f.outputs()).sum();
            if (reachable == 0) continue;
            long work = Math.max(1, Math.ceilDiv(forecasts.stream().mapToLong(f -> f.labourTicks()).sum(), reachable));
            result.add(new ProductionCost("extraction-capacity:" + home.value() + ":" + material.id(), product.itemKind(),
                    product.quantity(), Map.of(), 0, Map.of(), work, 1));
        }
        return List.copyOf(result);
    }
    private static List<BlockExtraction.Definition> materials(FrontierWorldState state, SubjectId home) {
        var materials = new HashSet<BlockExtraction.Definition>();
        for (var deposit : state.extractionSites().depositsIn(home))
            materials.addAll(deposit.presentMaterials());
        return materials.stream().sorted(Comparator.comparing(BlockExtraction.Definition::id)).toList();
    }
    private static List<SupplyPlanner.Process> assess(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
                                                     SupplyPlanningWindow window) {
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isEmpty()) return List.of();
        var schedule = state.humanPopulation().schedule(home);
        long daily = schedule.segments().stream().filter(s -> s.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(s -> s.endExclusive() - s.startInclusive()).sum();
        if (daily == 0) return List.of();
        var result = new ArrayList<SupplyPlanner.Process>();
        for (var material : materials(state, home)) {
            // The current single-output process capability cannot promise independent
            // co-products. Extraction itself still supports its full output vector.
            if (material.coldOutput().size() != 1) continue;
            var product = material.coldOutput().getFirst();
            if (!stocks.containsKey(product.itemKind())) continue;
            var definition = state.bootstrap().ruleset().workCatalog().require(material.before().kind(), WorkOperation.EXTRACT);
            var candidates = candidates(state, home, definition.capability());
            List<ExactItemStack> equipment = EquipmentProductiveCapacity.items(state, home,
                    candidates.stream().map(ResidentProfile::id).collect(java.util.stream.Collectors.toSet()),
                    io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirements.allOf(Set.of(material.toolRequirement())));
            var workers = candidates.stream().limit(equipment.size()).toList();
            if (workers.isEmpty()) continue;
            var forecasts = state.extractionSites().depositsIn(home).stream()
                    .map(d -> ExtractionProductionForecast.assess(state, d, material, workers)).filter(f -> f.outputs() > 0).toList();
            if (forecasts.isEmpty()) continue;
            long reachable = forecasts.stream().mapToLong(f -> f.outputs()).sum();
            long quantity = Math.min(reachable, EquipmentProductiveCapacity.remainingOperations(state, equipment, material.toolRequirement()));
            if (quantity == 0) continue;
            long work = Math.max(1, Math.ceilDiv(forecasts.stream().mapToLong(f -> f.labourTicks()).sum(), reachable));
            long first = forecasts.stream().mapToLong(f -> f.firstOutputTicks()).min().orElseThrow();
            long period = Math.max(1, Math.ceilDiv(Math.multiplyExact(work, schedule.dayTicks()), Math.multiplyExact(daily, workers.size())));
            long opening = SupplyPlanningWindow.completionDelay(schedule, first);
            result.add(new SupplyPlanner.Process("extraction-capacity:" + home.value() + ":" + material.id(),
                    product.itemKind(), product.quantity(), Map.of(), 0, Map.of(), 0,
                    Math.max(opening, period), period, Math.toIntExact(quantity), work,
                    SupplyLabourCapacity.over(state, home, workers.stream().map(ResidentProfile::id).toList(), window)));
        }
        return List.copyOf(result);
    }
    private static List<ResidentProfile> candidates(FrontierWorldState state, SubjectId home,
            HumanCapability capability) {
        var assignments = HumanAssignmentProjection.compile(state);
        return SupplyLabourCapacity.candidates(state, home, ResidentWorkKind.EXTRACTION, capability,
                resident -> assignments.assignment(resident.id()).kind() == HumanAssignmentKind.EXTRACTION).stream()
                .filter(resident -> !assignments.assignment(resident.id()).active()
                        || assignments.assignment(resident.id()).kind() == HumanAssignmentKind.EXTRACTION)
                .sorted(Comparator.comparing(ResidentProfile::id)).toList();
    }
    @Override public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }
}
