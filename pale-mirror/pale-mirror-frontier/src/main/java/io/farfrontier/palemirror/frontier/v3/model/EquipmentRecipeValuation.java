package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Equipment policy consumes the common delivered-input/labour valuation, never a second price model. */
final class EquipmentRecipeValuation {
    private record Query(SubjectId home, long tick) { }
    private static final ImmutableSnapshotView<Query, SourcingMarket> VIEWS = new ImmutableSnapshotView<>(4, 256);
    static SourcingMarket read(FrontierWorldState state, SubjectId home, long tick) {
        // Requests and acquisition consume identical cost facts. Exact state and
        // instant retain offer expiry and draft stock/funding invalidation.
        return VIEWS.get(state, new Query(home, tick), () -> project(state, home, tick));
    }
    private static SourcingMarket project(FrontierWorldState state, SubjectId home, long tick) {
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var capacities = new ArrayList<>(SupplyCapacityComposition.forValuation(state, participant));
        capacities.addAll(StationRecipeValuation.read(state, home, recipe -> recipe.outcome() instanceof ProductionRecipeOutcome.Repair));
        return SettlementSourcingMarket.costs(state, participant, capacities, tick);
    }
    private EquipmentRecipeValuation() { }
}
