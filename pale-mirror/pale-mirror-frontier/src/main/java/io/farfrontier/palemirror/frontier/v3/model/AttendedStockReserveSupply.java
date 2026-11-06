package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Declared stock targets and complete upstream recipe dependencies. */
final class AttendedStockReserveSupply implements ReserveSupplyPort {
    public Key key() { return Key.ATTENDED_STOCK; }
    public Facts read(FrontierWorldState state, SubjectId home) {
        var needs = new TreeMap<String, Need>(); var flows = new ArrayList<Flow>(); var timing = new TreeMap<String, Timing>();
        for (var policy : state.bootstrap().ruleset().production().stockPolicies()) {
            var recipe = state.bootstrap().ruleset().production().require(policy.recipeId());
            if (AutomaticStationProduction.stations(state, home, recipe).isEmpty()) continue;
            needs.put(recipe.product().itemKind(), new Need(Rate.ZERO, policy.outputReserve()));
            var inputs = ProductionRecipeSupplyFlow.read(state.bootstrap().ruleset(), recipe);
            flows.add(new Flow(recipe.product().itemKind(), inputs));
            long duration = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE).workUnits();
            timing.put(recipe.product().itemKind(), new Timing(duration * policy.maximumBatches(), TimingBasis.LOCAL_ESTIMATE));
        }
        return new Facts(needs, flows, timing);
    }
}
