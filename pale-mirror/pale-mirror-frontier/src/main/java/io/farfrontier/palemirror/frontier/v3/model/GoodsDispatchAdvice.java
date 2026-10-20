package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Shared departure comparison for price advice and exact trip admission.
 * Forecasts never grant route, cargo or worker authority. */
public final class GoodsDispatchAdvice {
    public static TripDispatchPolicy.Cause assess(FrontierWorldState state, GoodsParticipantView view,
                                                  Map<String, Integer> quantities, long journeyTicks) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        var needs = SettlementReservePlanning.basis(state, view.participant().settlementId()).needs();
        return TripDispatchPolicy.assess(quantities.entrySet().stream().map(e -> {
            var demand = view.supplyPlan().map(p -> p.require(e.getKey()))
                    .orElseGet(() -> new SupplyPlanner.Demand(0, 0, 0, Long.MAX_VALUE, Set.of(), Optional.empty()));
            var stock = view.stocks().get(e.getKey());
            return new TripDispatchPolicy.Goods(e.getValue(), rules.supplyPlanning().require(e.getKey()), demand,
                    TripDispatchPolicy.uncovered(demand, needs.get(e.getKey()), stock.unclaimedInStorage(),
                            stock.expectedIncoming(), journeyTicks, rules.reviewInterval()));
        }).toList(), journeyTicks, rules.reviewInterval());
    }
    private GoodsDispatchAdvice() { }
}
