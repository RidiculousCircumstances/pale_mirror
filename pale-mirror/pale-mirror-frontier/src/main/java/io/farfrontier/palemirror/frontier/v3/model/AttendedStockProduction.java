package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Institution demand/admission facts for ordinary stock recipes. Execution belongs to StationWork. */
public final class AttendedStockProduction {
    public record Ready(ProductionRecipe recipe, SettlementStructure facility, ProductionMaterialSupply.Bundle inputs) { }
    /** Advisory workforce potential precedes current economic permission. Staffing
     * itself changes capacity, so it must not disappear when that plan is invalidated. */
    public static boolean materialBackedDemand(FrontierWorldState state, SubjectId home, long tick) {
        var catalog = state.bootstrap().ruleset().production();
        for (var policy : catalog.stockPolicies()) {
            var recipe = catalog.require(policy.recipeId());
            String output = recipe.product().itemKind();
            long acceptedTarget = state.companies().goodsTrade().participants().economicState(home).accepted()
                    .map(plan -> plan.productionCeilings().getOrDefault(output, 0L)).orElse(0L);
            long target = Math.max(policy.outputReserve(), acceptedTarget);
            if (target <= ProductionStockDemand.coverage(state, home, output)) continue;
            for (var record : AutomaticStationProduction.stations(state, home, recipe)) {
                var station = record.productionStation().orElseThrow();
                if (ProductionStationSelection.usable(state, station.facilityId(), recipe.id()).isPresent()
                        && ProductionIngredientAvailability.storedBatches(state, station, recipe) > 0) return true;
            }
        }
        return false;
    }
    public static Optional<Ready> ready(FrontierWorldState state, SubjectId home, long tick) {
        var catalog = state.bootstrap().ruleset().production();
        for (var policy : catalog.stockPolicies().stream().sorted(Comparator.comparing(value -> value.recipeId().value())).toList()) {
            var recipe = catalog.require(policy.recipeId());
            long missing = ProductionStockDemand.read(state, home, recipe.product().itemKind(), tick).missing();
            int maximum = ProductionBatchSizing.batchesForDemand(recipe, missing, policy.maximumBatches());
            for (var record : AutomaticStationProduction.stations(state, home, recipe)) {
                var station = record.productionStation().orElseThrow();
                if (!SettlementCommitmentComposition.ADMISSION.facilityAvailable(state, station.facilityId())
                        || ProductionStationSelection.usable(state, station.facilityId(), recipe.id()).isEmpty()) continue;
                var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), home).structures().stream()
                        .filter(value -> value.id().equals(station.facilityId())).findFirst().orElseThrow();
                for (int count = maximum; count > 0; count--) {
                    var inputs = ProductionMaterialSupply.select(state, home, station, recipe, count);
                    if (inputs.isEmpty() || ProductionOutputCapacity.selectRecipeDestination(state, ProductionRights.publicService(home), recipe,
                            inputs.orElseThrow().quantity(), inputs.orElseThrow().sources().getFirst().containerId(),
                            SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach(), Optional.empty()).isEmpty()) continue;
                    return Optional.of(new Ready(recipe, facility, inputs.orElseThrow()));
                }
            }
        }
        return Optional.empty();
    }
    private AttendedStockProduction() { }
}
