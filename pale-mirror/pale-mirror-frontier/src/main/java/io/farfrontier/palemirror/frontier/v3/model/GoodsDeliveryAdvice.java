package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.market.FundedCargoLot;
import io.farfrontier.palemirror.frontier.v3.model.supply.TripDispatchPolicy;
import java.util.*;

/** Shared provisional cash freight and dispatch eligibility; no route or trip admission.
 * Future output may use a current eligible warehouse but is never reported as stored stock. */
final class GoodsDeliveryAdvice {
    enum Supply { STORED, CONDITIONAL_PRODUCTION }
    record Advice(int quantity, long tripExpense, int minimumDispatchQuantity) {
        long deliveryPerItem() { return Math.ceilDiv(tripExpense, quantity); }
    }

    static Optional<Advice> read(FrontierWorldState state, GoodsParticipant seller,
            GoodsParticipantView buyer, String item, int quantity, long goodsPrice, long budget, Supply supply, long atTick) {
        if (quantity <= 0) throw new IllegalArgumentException("delivery advice requires a positive quantity");
        Advice best = null;
        for (var container : GoodsParticipantStorage.containers(state, seller, item)) {
            int carried = supply == Supply.STORED
                    ? Math.min(quantity, GoodsParticipantStorage.unclaimed(state, seller, container, item)) : quantity;
            if (carried == 0) continue;
            var freight = from(state, container, buyer, item, carried, goodsPrice, budget, atTick);
            if (freight.isPresent() && (best == null || freight.orElseThrow().deliveryPerItem() < best.deliveryPerItem()))
                best = freight.orElseThrow();
        }
        return Optional.ofNullable(best);
    }

    static Optional<Advice> from(FrontierWorldState state, SubjectId container,
            GoodsParticipantView buyer, String item, int quantity, long goodsPrice, long budget, long atTick) {
        if (quantity <= 0) throw new IllegalArgumentException("delivery advice requires a positive quantity");
        long expense = 0, delivery = 0;
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isPresent()) {
            var timing = ProcurementTravelForecast.read(state, container, item, buyer.participant());
            if (timing.isEmpty()) return Optional.empty();
            delivery = timing.orElseThrow().deliveryTicks();
            expense = GoodsTransportValuation.estimate(state, buyer.participant(),
                    timing.orElseThrow().journeyTicks(), 1).resourceExpense().raw();
        }
        int carried = FundedCargoLot.quantity(Math.min(quantity, state.bootstrap().ruleset().goodsTrade().maximumBatch()),
                goodsPrice, expense, budget);
        if (carried == 0) return Optional.empty();
        var cause = GoodsDispatchAdvice.assess(state, buyer, Map.of(item, carried), delivery, atTick);
        if (cause == TripDispatchPolicy.Cause.ACCUMULATING) return Optional.empty();
        int minimum = cause == TripDispatchPolicy.Cause.SUPPLY_DEADLINE ? 1
                : TripDispatchPolicy.minimumStandaloneLoad(state.bootstrap().ruleset().goodsTrade().supplyPlanning().require(item));
        return Optional.of(new Advice(carried, expense, minimum));
    }
    private GoodsDeliveryAdvice() { }
}
