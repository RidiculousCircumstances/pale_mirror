package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Shared departure comparison for price advice and exact trip admission.
 * Forecasts never grant route, cargo or worker authority. */
public final class GoodsDispatchAdvice {
    public static TripDispatchPolicy.Cause assess(FrontierWorldState state, GoodsParticipantView view, GoodsParticipant organizer, GoodsTradeFulfillment.Mode mode,
                                                  Map<String, Integer> quantities, Map<String, Long> unitPrices, long journeyTicks, long durationTicks, long atTick) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        var accepted = state.companies().goodsTrade().participants().economicState(view.participant().party().id()).accepted();
        if (accepted.isEmpty()) return TripDispatchPolicy.Cause.AWAITING_ECONOMIC_PLAN;
        var needs = accepted.orElseThrow().needs();
        var incoming = SettlementDeliveryForecast.read(state, view.participant());
        return TripDispatchPolicy.assess(quantities.entrySet().stream().map(e -> {
            var demand = view.supplyPlan().map(p -> p.require(e.getKey()))
                    .orElseGet(() -> new SupplyPlanner.Demand(0, 0, 0, Long.MAX_VALUE, Set.of(), Optional.empty()));
            long age = Math.max(0, atTick - accepted.orElseThrow().atTick());
            if (demand.deadlineTicks() != Long.MAX_VALUE) demand = new SupplyPlanner.Demand(demand.purchase(), demand.production(),
                    demand.recoveryProduction(), demand.protectedInput(), Math.max(0, demand.deadlineTicks() - age),
                    demand.reasons(), demand.process(), demand.urgentPurchase());
            var stock = view.stocks().get(e.getKey());
            return new TripDispatchPolicy.Goods(e.getValue(), plannedLot(state, view, organizer, mode, e.getKey(), unitPrices.get(e.getKey()), durationTicks, atTick), demand,
                    TripDispatchPolicy.uncovered(demand, needs.get(e.getKey()), stock.unclaimedInStorage(),
                            incoming.getOrDefault(e.getKey(), List.of()), journeyTicks, rules.reviewInterval()));
        }).toList(), journeyTicks, rules.reviewInterval());
    }
    public static long plannedLot(FrontierWorldState state, GoodsParticipantView view, GoodsParticipant organizer, GoodsTradeFulfillment.Mode mode, String item, long price, long durationTicks, long atTick) {
        if (price < 1) throw new IllegalArgumentException("lot advice lacks its real unit price");
        var plan = view.supplyPlan().orElseThrow();
        // Gross requirements over the common horizon, not today's tiny net
        // remainder: otherwise every review starts another two-item caravan.
        long useful = plan.economy().map(e -> e.domesticTargets().get(item))
                .orElseGet(() -> (long) view.target(state.bootstrap().ruleset().goodsTrade().policies().get(view.participant().policy())
                        .stream().filter(c -> c.itemKind().equals(item)).findFirst().orElseThrow()));
        useful = Math.max(useful, plan.require(item).purchase());
        long funding = GoodsParticipantFunding.purchase(view, state.bootstrap().ruleset().goodsTrade(), item).raw();
        return ReplenishmentLot.choose(useful, funding, price,
                GoodsTransportOptions.costCurve(state, organizer, durationTicks, item, mode, atTick));
    }
    private GoodsDispatchAdvice() { }
}
