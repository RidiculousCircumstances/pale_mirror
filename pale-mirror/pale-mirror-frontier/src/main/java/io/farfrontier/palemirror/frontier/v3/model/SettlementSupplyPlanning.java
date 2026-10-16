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
        var rules = state.bootstrap().ruleset(); var trade = rules.goodsTrade(); var life = rules.residentLife();
        if (!trade.supplyPlanning().enabled()) throw new IllegalArgumentException("supply planning was not selected by ruleset");
        var facts = new TreeMap<String, SupplyPlanner.Stock>();
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var deliveries = SettlementDeliveryForecast.read(state, home, participant.endpoint().containerId());
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
            long period = food == null ? 1 : Math.multiplyExact(Math.multiplyExact(life.satietyUnitTicks(), 1000L), food.nutritionPerItem());
            facts.put(commodity.itemKind(), new SupplyPlanner.Stock(stock.unclaimedAtEndpoint(), stock.expectedIncoming(), promised,
                    StockTargetDemand.target(commodity, residents, stock.protectedMinimum()), stock.protectedMinimum(),
                    food == null ? 0 : metabolism, period, trade.supplyPlanning().require(commodity.itemKind()),
                    deliveries.getOrDefault(commodity.itemKind(), List.of())));
        }
        return PLANNER.plan(facts, SupplyCapacityComposition.read(state, home, facts),
                SupplyCapacityComposition.dependencies(state, home, facts));
    }
}
