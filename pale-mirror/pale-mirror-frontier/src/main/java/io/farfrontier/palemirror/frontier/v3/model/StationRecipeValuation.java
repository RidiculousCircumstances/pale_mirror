package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;
import java.util.function.Predicate;

/** Installed attended recipe costs, shared by manufacture and maintenance advice.
 * No input/output availability, labour allocation or operation promise. */
final class StationRecipeValuation {
    static List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Predicate<ProductionRecipe> selected) {
        var result = new ArrayList<SupplyPlanner.Process>(); var catalog = state.bootstrap().ruleset().production();
        var fuels = new TreeMap<String, Long>(); catalog.fuels().forEach(f -> fuels.put(f.itemKind(), f.heatUnits()));
        for (var recipe : catalog.recipes()) {
            if (!selected.test(recipe) || recipe.processingMode() != ProductionRecipe.ProcessingMode.ATTENDED
                    || AutomaticStationProduction.stations(state, home, recipe).isEmpty()) continue;
            var definition = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE);
            var workers = SupplyLabourCapacity.potential(state, home, ResidentWorkKind.PRODUCTION, definition.capability());
            int speed = workers.stream().mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition,
                    state.bootstrap().ruleset().workCatalog())).min().orElse(0);
            if (speed == 0) continue;
            long duration = Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1000L), speed);
            var inputs = Map.of(recipe.ingredient().acceptedItems().stream().sorted().findFirst().orElseThrow(), recipe.ingredient().quantity());
            result.add(new SupplyPlanner.Process(recipe.id().value(), recipe.product().itemKind(), recipe.product().quantity(),
                    inputs, recipe.heatPerBatch(), fuels, 0, duration, duration, 1, duration, Map.of(), 1));
        }
        return List.copyOf(result);
    }
    private StationRecipeValuation() { }
}
