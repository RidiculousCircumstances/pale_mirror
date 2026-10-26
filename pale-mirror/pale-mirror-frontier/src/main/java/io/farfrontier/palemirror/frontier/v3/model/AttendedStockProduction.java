package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Institution demand/admission facts for ordinary stock recipes. Execution belongs to StationWork. */
public final class AttendedStockProduction {
    public record Ready(ProductionRecipe recipe, SettlementStructure facility, ProductionMaterialSupply.Bundle inputs) { }
    public static Optional<Ready> ready(FrontierWorldState state, SubjectId home, long tick) {
        var catalog = state.bootstrap().ruleset().production();
        for (var policy : catalog.stockPolicies().stream().sorted(Comparator.comparing(value -> value.recipeId().value())).toList()) {
            var recipe = catalog.require(policy.recipeId());
            long missing = ProductionStockDemand.read(state, home, recipe.product().itemKind(), tick).missing();
            int maximum = (int) Math.min(policy.maximumBatches(), Math.min(ProductionBatchSizing.maximumRecipeBatches(recipe), missing / recipe.product().quantity()));
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
