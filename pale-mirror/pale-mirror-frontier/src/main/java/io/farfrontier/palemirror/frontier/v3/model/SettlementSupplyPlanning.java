package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Adapts canonical owner facts to pure advice; its result grants no resource or worker authority. */
public final class SettlementSupplyPlanning {
    private static final SupplyPlanner PLANNER = new SupplyPlanner();
    private SettlementSupplyPlanning() { }
    public static SupplyPlanner.Plan read(FrontierWorldState state, SubjectId home, int residents,
                                         Map<String, GoodsParticipantView.Stock> observed) {
        return read(state, home, residents, observed, (stocks, window) -> Optional.empty());
    }
    /** Uses the same economic solver; hypothetical capacity is never inserted into the world state. */
    static SupplyPlanner.Plan assessCultivation(FrontierWorldState state, SubjectId home, int residents,
            Map<String, GoodsParticipantView.Stock> observed, List<FieldSupplyCapacity.Cell> cells) {
        return read(state, home, residents, observed, (stocks, window) -> Optional.of(
                new SupplyCapacityComposition.Option(SupplyCapacityPort.Key.FIELD,
                        FieldSupplyCapacity.assess(state, home, stocks, window, cells))));
    }
    private static SupplyPlanner.Plan read(FrontierWorldState state, SubjectId home, int residents,
            Map<String, GoodsParticipantView.Stock> observed,
            java.util.function.BiFunction<Map<String, SupplyPlanner.Stock>, SupplyPlanningWindow,
                    Optional<SupplyCapacityComposition.Option>> option) {
        var rules = state.bootstrap().ruleset(); var trade = rules.goodsTrade(); var life = rules.residentLife();
        if (!trade.supplyPlanning().enabled()) throw new IllegalArgumentException("supply planning was not selected by ruleset");
        var facts = new TreeMap<String, SupplyPlanner.Stock>();
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var deliveries = SettlementDeliveryForecast.read(state, participant);
        var reserves = SettlementReservePlanning.enabled(state) ? SettlementReservePlanning.read(state, home)
                : Map.<String, io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.Target>of();
        long metabolism = state.humanPopulation().residents().values().stream()
                .filter(r -> r.settlementId().equals(home) && state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE)
                .mapToLong(r -> r.characteristics().effectiveMetabolismPermille(state.humanPopulation().nutrition(r.id()).lastEvaluatedTick())).sum();
        for (var commodity : trade.policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT)) {
            var stock = Objects.requireNonNull(observed.get(commodity.itemKind()), "missing canonical supply stock");
            long promised = StockSupplyComposition.pending(state, StockSupplyPort.Key.BAKERY).stream()
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
        var market = SettlementSourcingMarket.read(state, participant, capacities);
        if (trade.supplyPlanning().economics().isPresent())
            return SettlementEconomicPlanning.read(state, home, facts, reserves, capacities, commitments, market);
        var planned = PLANNER.plan(facts, capacities, commitments, market);
        return new SupplyPlanner.Plan(planned.demands(), reserves, planned.preferredPurchaseSpending());
    }
}
