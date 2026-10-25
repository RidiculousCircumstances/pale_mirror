package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Equipment policy consumes the common delivered-input/labour valuation, never a second price model. */
final class EquipmentRecipeValuation {
    private record Query(SubjectId home, long tick) { }
    private static final ImmutableInputView<BoundedValueMemo<Query, SourcingMarket>> VIEWS = new ImmutableInputView<>();
    static SourcingMarket read(FrontierWorldState state, SubjectId home, long tick) {
        // Requests and acquisition consume identical cost facts. Exact state and
        // instant retain offer expiry and draft stock/funding invalidation.
        return VIEWS.get(List.of(state), () -> new BoundedValueMemo<Query, SourcingMarket>(256))
                .get(new Query(home, tick), () -> project(state, home, tick));
    }
    private static SourcingMarket project(FrontierWorldState state, SubjectId home, long tick) {
        var window = new SupplyPlanningWindow(state.humanPopulation().schedule(home).dayTicks());
        var stocks = new TreeMap<String, SupplyPlanner.Stock>();
        state.bootstrap().ruleset().goodsTrade().supplyPlanning().commodities().forEach((kind, policy) ->
                stocks.put(kind, new SupplyPlanner.Stock(0, 0, 0, 0, 0, 0, 1, policy)));
        var capacities = new ArrayList<>(new AutomaticStationSupplyCapacity().read(state, home, stocks, window));
        capacities.addAll(new ExtractionSupplyCapacity().read(state, home, stocks, window));
        capacities.addAll(StationRecipeValuation.read(state, home, recipe ->
                state.bootstrap().ruleset().tools().find(recipe.product().itemKind()).isPresent()));
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        return SettlementSourcingMarket.read(state, participant, capacities, tick);
    }
    private EquipmentRecipeValuation() { }
}
