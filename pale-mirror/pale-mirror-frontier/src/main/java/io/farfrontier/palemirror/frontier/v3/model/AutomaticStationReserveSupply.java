package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Finite institution reserve and recipe dependencies, not fictional consumption. */
final class AutomaticStationReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.AUTOMATIC_STATIONS; }
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        var catalog = state.bootstrap().ruleset().production();
        var needs = new TreeMap<String, Need>(); var flows = new ArrayList<Flow>(); var timing = new TreeMap<String, Timing>();
        for (var policy : catalog.automaticPolicies()) {
            var recipe = catalog.require(policy.recipeId());
            if (AutomaticStationProduction.stations(state, home, recipe).isEmpty()) continue;
            needs.put(recipe.product().itemKind(), new Need(Rate.ZERO, policy.outputReserve()));
            var inputs = new TreeMap<String, Rate>();
            inputs.put(recipe.ingredient().acceptedItems().stream().sorted().findFirst().orElseThrow(),
                    Rate.of(recipe.ingredient().quantity(), recipe.product().quantity()));
            if (recipe.heatPerBatch() > 0) {
                var fuel = catalog.fuels().stream().sorted(Comparator.comparingLong(ProductionCatalog.Fuel::heatUnits).reversed()
                        .thenComparing(ProductionCatalog.Fuel::itemKind)).findFirst().orElseThrow();
                inputs.merge(fuel.itemKind(), Rate.of(recipe.heatPerBatch(), Math.multiplyExact(fuel.heatUnits(), recipe.product().quantity())), Rate::plus);
            }
            flows.add(new Flow(recipe.product().itemKind(), inputs));
            long duration = Math.multiplyExact(state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE).workUnits(), policy.maximumBatches());
            timing.put(recipe.product().itemKind(), new Timing(duration, TimingBasis.LOCAL_ESTIMATE));
        }
        return new Facts(needs, flows, timing);
    }
}
