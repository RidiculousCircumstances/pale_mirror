package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Adapts canonical owner facts to pure advice; its result grants no resource or worker authority. */
public final class SettlementSupplyPlanning {
    private SettlementSupplyPlanning() { }
    static SettlementSupplyFacts collect(FrontierWorldState state, GoodsParticipantView observed, long atTick) {
        return collect(state, observed.participant().party().id(), observed.residents(), observed.stocks(),
                atTick, (stocks, window) -> Optional.empty());
    }
    /** Uses the same economic solver; hypothetical capacity is never inserted into the world state. */
    static SupplyPlanner.Plan assessCultivation(FrontierWorldState state, SubjectId home, int residents,
            Map<String, GoodsParticipantView.Stock> observed, List<FieldSupplyCapacity.Cell> cells, long atTick) {
        return assessCultivation(state, home, residents, observed, cells, false, atTick);
    }
    static SupplyPlanner.Plan assessCultivation(FrontierWorldState state, SubjectId home, int residents,
            Map<String, GoodsParticipantView.Stock> observed, List<FieldSupplyCapacity.Cell> cells, boolean requireSustainedExport, long atTick) {
        var facts = collect(state, home, residents, observed, atTick, (stocks, window) -> Optional.of(
                new SupplyCapacityComposition.Option(SupplyCapacityPort.Key.FIELD,
                        FieldSupplyCapacity.assess(state, home, stocks, window, cells))));
        var planner = new io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlanner();
        var domestic = planner.domestic(SettlementEconomicPlanning.facts(state, facts),
                state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics().orElseThrow());
        return planner.withExports(domestic, SettlementEconomicPlanning.exports(state, facts,
                requireSustainedExport, planner.potentialExports(domestic)));
    }
    private static SettlementSupplyFacts collect(FrontierWorldState state, SubjectId home, int residents,
            Map<String, GoodsParticipantView.Stock> observed, long atTick,
            java.util.function.BiFunction<Map<String, SupplyPlanner.Stock>, SupplyPlanningWindow,
                    Optional<SupplyCapacityComposition.Option>> option) {
        var rules = state.bootstrap().ruleset(); var trade = rules.goodsTrade(); var life = rules.residentLife();
        if (!trade.supplyPlanning().enabled()) throw new IllegalArgumentException("supply planning was not selected by ruleset");
        var facts = new TreeMap<String, SupplyPlanner.Stock>();
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var deliveries = SettlementDeliveryForecast.read(state, participant);
        var basis = SettlementReservePlanning.basis(state, home, atTick);
        var reserves = SettlementReservePlanning.enabled(state) ? SettlementReservePlanning.targets(state, basis)
                : Map.<String, io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.Target>of();
        long metabolism = state.humanPopulation().residents().values().stream()
                .filter(r -> r.settlementId().equals(home) && state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE)
                .mapToLong(r -> r.characteristics().effectiveMetabolismPermille(atTick)).sum();
        for (var commodity : trade.policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT)) {
            var stock = Objects.requireNonNull(observed.get(commodity.itemKind()), "missing canonical supply stock");
            long promised = StockSupplyComposition.pending(state, StockSupplyPort.Key.STATION_WORK).stream()
                    .filter(c -> c.economicOwner().equals(home) && c.itemKind().equals(commodity.itemKind())).mapToLong(StockSupplyPort.Commitment::incoming).sum();
            promised = Math.addExact(promised, StockSupplyComposition.pending(state, StockSupplyPort.Key.FIELD).stream()
                    .filter(c -> c.economicOwner().equals(home) && c.itemKind().equals(commodity.itemKind())).mapToLong(StockSupplyPort.Commitment::incoming).sum());
            var food = life.foods().foods().get(commodity.itemKind());
            var meal = food == null ? null : FoodConsumptionForecast.atThreshold(food, life);
            long period = meal == null ? 1 : Math.multiplyExact(Math.multiplyExact(life.satietyUnitTicks(), 1000L), meal.usefulNutrition());
            var reserve = reserves.get(commodity.itemKind());
            facts.put(commodity.itemKind(), new SupplyPlanner.Stock(stock.unclaimedInStorage(), stock.expectedIncoming(), promised,
                    reserve == null ? StockTargetDemand.target(commodity, residents, stock.protectedMinimum()) : reserve.quantity(), stock.protectedMinimum(),
                    meal == null ? 0 : Math.multiplyExact(metabolism, meal.items()), period, trade.supplyPlanning().require(commodity.itemKind()),
                    deliveries.getOrDefault(commodity.itemKind(), List.of()),
                    reserve == null ? OptionalLong.empty() : OptionalLong.of(reserve.reorderPoint())));
        }
        var window = SupplyPlanningWindow.forSettlement(state, home, reserves.values());
        var capacities = SupplyCapacityComposition.read(state, home, facts, window, option.apply(facts, window));
        var commitments = SupplyCapacityComposition.dependencies(state, home, facts);
        var market = SettlementSourcingMarket.read(state, participant, capacities, atTick);
        return new SettlementSupplyFacts(home, atTick, basis, facts, reserves, capacities, commitments, market);
    }
}
