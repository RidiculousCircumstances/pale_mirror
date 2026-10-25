package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Equipment-owned financial renewal facts. Healthy tools consume service life,
 * but this forecast grants neither a repair order nor fictitious input stock. */
final class EquipmentRenewalForecast {
    private record Option(ProductionRecipe recipe, long operations, long cash, long heat) { }
    static Map<String, Long> read(FrontierWorldState state, SubjectId home, long tick) {
        var result = new TreeMap<String, Long>(); long heat = 0;
        var units = EquipmentStockProjection.units(state, home);
        var catalog = state.bootstrap().ruleset().production();
        var party = state.companies().goodsTrade().participants().participants().get(home);
        if (party == null) return Map.of();
        for (var demand : EquipmentDemandComposition.read(state, home)) {
            var kinds = units.stream().filter(u -> demand.supports(state.bootstrap().ruleset().tools().require(u.kind())))
                    .map(EquipmentStockProjection.Unit::kind).collect(java.util.stream.Collectors.toSet());
            Option best = null;
            for (var recipe : catalog.recipes()) {
                if (!kinds.contains(recipe.product().itemKind()) || AutomaticStationProduction.stations(state, home, recipe).isEmpty()) continue;
                var tool = state.bootstrap().ruleset().tools().require(recipe.product().itemKind());
                long useful = switch (recipe.outcome()) {
                    case ProductionRecipeOutcome.Repair repair -> Math.min(repair.restoredDurability(), tool.durability() - tool.maintenanceReserve());
                    case ProductionRecipeOutcome.Individual ignored -> tool.durability() - tool.maintenanceReserve();
                    default -> 0;
                };
                if (useful < 1) continue;
                long operations = Math.ceilDiv(demand.plannedWear(), useful);
                long material = recipe.ingredient().acceptedItems().stream().mapToLong(item ->
                        GoodsResourceValuation.replacement(state, party, item, tick).raw()).min().orElseThrow();
                long neededHeat = Math.multiplyExact(operations, recipe.heatPerBatch());
                long cash = Math.multiplyExact(Math.multiplyExact(operations, recipe.ingredient().quantity()), material);
                if (neededHeat > 0) cash = Math.addExact(cash, fuelCost(state, party, neededHeat, tick));
                var candidate = new Option(recipe, operations, cash, neededHeat);
                if (best == null || candidate.cash() < best.cash()
                        || candidate.cash() == best.cash() && recipe.id().value().compareTo(best.recipe().id().value()) < 0) best = candidate;
            }
            if (best == null) continue;
            var chosen = best;
            String input = chosen.recipe().ingredient().acceptedItems().stream().sorted(Comparator
                    .comparingLong((String item) -> GoodsResourceValuation.replacement(state, party, item, tick).raw())
                    .thenComparing(item -> item)).findFirst().orElseThrow();
            result.merge(input, Math.multiplyExact(chosen.operations(), chosen.recipe().ingredient().quantity()), Math::addExact);
            heat = Math.addExact(heat, chosen.heat());
        }
        long supplied = state.inventory().containers().values().stream().filter(c -> c.ownerId().equals(home))
                .flatMap(c -> c.productionStation().stream()).filter(s -> s.capability() == ProductionStationSpec.Capability.FABRICATION)
                .mapToLong(s -> ProductionStationEnergy.suppliedHeat(state, s.id())).sum();
        if (heat > supplied) {
            long missing = heat - supplied;
            var fuel = catalog.fuels().stream().min(Comparator.comparingLong((ProductionCatalog.Fuel f) ->
                    Math.multiplyExact(Math.ceilDiv(missing, f.heatUnits()), GoodsResourceValuation.replacement(state, party, f.itemKind(), tick).raw()))
                    .thenComparing(ProductionCatalog.Fuel::itemKind)).orElseThrow();
            result.merge(fuel.itemKind(), Math.ceilDiv(missing, fuel.heatUnits()), Math::addExact);
        }
        return Map.copyOf(result);
    }
    private static long fuelCost(FrontierWorldState state, GoodsParticipant party, long heat, long tick) {
        return state.bootstrap().ruleset().production().fuels().stream().mapToLong(f -> Math.multiplyExact(
                Math.ceilDiv(heat, f.heatUnits()), GoodsResourceValuation.replacement(state, party, f.itemKind(), tick).raw())).min().orElseThrow();
    }
    private EquipmentRenewalForecast() { }
}
