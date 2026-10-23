package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.market.FundedCargoLot;
import io.farfrontier.palemirror.frontier.v3.model.supply.TripDispatchPolicy;
import java.util.*;

/** Buyer collection: provisions have economic value but are not a cash freight invoice; no route or trip admission.
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
        long expense = 0, delivery = 0, duration = 0;
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isPresent()) {
            var timing = ProcurementTravelForecast.read(state, container, item, buyer.participant(),
                    TransportOrganization.Itinerary.DESTINATION_COLLECTION);
            if (timing.isEmpty()) return Optional.empty();
            delivery = timing.orElseThrow().deliveryTicks();
            duration = timing.orElseThrow().journeyTicks();
        }
        int carried = FundedCargoLot.quantity(Math.min(quantity, GoodsTransportOptions.maximumQuantity(state, item)),
                goodsPrice, 0, budget);
        if (carried == 0) return Optional.empty();
        if (duration > 0) expense = GoodsTransportOptions.forecast(state, buyer.participant(), duration,
                Math.ceilDiv(carried, GoodsTransportOptions.parcelSize(state, item))).orElseThrow().cost().resourceExpense().raw();
        var cause = GoodsDispatchAdvice.assess(state, buyer, buyer.participant(), Map.of(item, carried), Map.of(item, goodsPrice), delivery, duration, atTick);
        if (cause == TripDispatchPolicy.Cause.ACCUMULATING) return Optional.empty();
        int minimum = cause == TripDispatchPolicy.Cause.SUPPLY_DEADLINE ? 1
                : Math.toIntExact(Math.max(1, GoodsDispatchAdvice.plannedLot(state, buyer, buyer.participant(), item, goodsPrice, duration)));
        return Optional.of(new Advice(carried, expense, minimum));
    }
    private GoodsDeliveryAdvice() { }
}
