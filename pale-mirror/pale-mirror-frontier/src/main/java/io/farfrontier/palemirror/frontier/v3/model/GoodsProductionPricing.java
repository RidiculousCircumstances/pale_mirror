package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.market.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Converts declared production capabilities into price constraints, without inspecting concrete jobs. */
final class GoodsProductionPricing {
    record Assessment(Map<String, FixedScalar> opportunities, Map<String, FixedScalar> alternatives,
                      List<SupplyPlanner.Process> processes) {
        Assessment { opportunities = Map.copyOf(opportunities); alternatives = Map.copyOf(alternatives); processes = List.copyOf(processes); }
    }
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
    static Map<String, FixedScalar> alternatives(FrontierWorldState state, GoodsParticipantAssessment.Assessment assessment,
                                                Map<String, FixedScalar> prices) {
        return assess(state, assessment, prices).alternatives();
    }
    static Assessment assess(FrontierWorldState state, GoodsParticipantAssessment.Assessment assessment, Map<String, FixedScalar> prices) {
        var view = assessment.view();
        if (assessment.facts().isEmpty())
            return new Assessment(Map.of(), Map.of(), List.of());
        var collected = assessment.facts().orElseThrow();
        var facts = collected.stocks();
        var result = new HashMap<String, FixedScalar>();
        var capacities = collected.capacities();
        var economic = collected.market().processUnitCosts();
        var opportunities = new TreeMap<String, FixedScalar>();
        var sourcing = state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing();
        var potential = sourcing.isPresent() ? SupplyCapacityComposition.forValuation(state, view.participant()) : capacities;
        var estimates = new TreeMap<String, Long>(); prices.forEach((kind, price) -> estimates.put(kind, price.raw()));
        var replacementCosts = sourcing.isPresent() ? io.farfrontier.palemirror.frontier.v3.model.supply.SourcingValuation.processes(potential,
                estimates, collected.market().offers(), sourcing.orElseThrow().labourValuePerTick()) : economic;
        for (var process : potential) if (replacementCosts.containsKey(process.id())) opportunities.merge(process.product(), new FixedScalar(replacementCosts.get(process.id())),
                (a, b) -> a.compareTo(b) <= 0 ? a : b);
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
        return new Assessment(opportunities, result, potential);
    }
    private GoodsProductionPricing() { }
}
