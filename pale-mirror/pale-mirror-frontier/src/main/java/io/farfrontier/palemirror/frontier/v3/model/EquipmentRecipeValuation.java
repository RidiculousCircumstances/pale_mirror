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
        var catalog = state.bootstrap().ruleset().production();
        var window = new SupplyPlanningWindow(state.humanPopulation().schedule(home).dayTicks());
        var stocks = new TreeMap<String, SupplyPlanner.Stock>();
        state.bootstrap().ruleset().goodsTrade().supplyPlanning().commodities().forEach((kind, policy) ->
                stocks.put(kind, new SupplyPlanner.Stock(0, 0, 0, 0, 0, 0, 1, policy)));
        var capacities = new ArrayList<>(new AutomaticStationSupplyCapacity().read(state, home, stocks, window));
        capacities.addAll(new ExtractionSupplyCapacity().read(state, home, stocks, window));
        var fuels = new TreeMap<String, Long>(); catalog.fuels().forEach(f -> fuels.put(f.itemKind(), f.heatUnits()));
        var workers = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.PRODUCTION).stream()
                .map(state.humanPopulation()::resident).filter(Objects::nonNull).toList();
        for (var recipe : catalog.recipes()) {
            if (state.bootstrap().ruleset().tools().find(recipe.product().itemKind()).isEmpty()
                    || recipe.processingMode() != ProductionRecipe.ProcessingMode.ATTENDED
                    || AutomaticStationProduction.stations(state, home, recipe).isEmpty()) continue;
            var definition = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE);
            int speed = workers.stream().mapToInt(worker -> ResidentWorkStatistics.speedPermille(worker, definition,
                    state.bootstrap().ruleset().workCatalog())).min().orElse(state.bootstrap().ruleset().workCatalog().minSpeedPermille());
            long duration = Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1000L), speed);
            var inputs = Map.of(recipe.ingredient().acceptedItems().stream().sorted().findFirst().orElseThrow(), recipe.ingredient().quantity());
            capacities.add(new SupplyPlanner.Process(recipe.id().value(), recipe.product().itemKind(), recipe.product().quantity(),
                    inputs, recipe.heatPerBatch(), fuels, 0, duration, duration, 1, duration, Map.of(), 1));
        }
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        return SettlementSourcingMarket.read(state, participant, capacities, tick);
    }
    private EquipmentRecipeValuation() { }
}
