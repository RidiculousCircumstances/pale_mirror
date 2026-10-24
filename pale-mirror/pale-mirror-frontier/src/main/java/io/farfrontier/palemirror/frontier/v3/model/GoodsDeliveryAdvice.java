package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.TripDispatchPolicy;
import java.util.*;

/** Both delivery modes share material/funding advice; only seller delivery invoices freight. No route or trip admission.
 * Future output may use a current eligible warehouse but is never reported as stored stock. */
final class GoodsDeliveryAdvice {
    enum Supply { STORED, CONDITIONAL_PRODUCTION }
    record Advice(int quantity, long tripExpense, int minimumDispatchQuantity, long cashDelivery) {
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
            var freight = from(state, seller, container, buyer, item, carried, goodsPrice, budget, atTick);
            if (freight.isPresent() && (best == null || freight.orElseThrow().deliveryPerItem() < best.deliveryPerItem()))
                best = freight.orElseThrow();
        }
        return Optional.ofNullable(best);
    }

    static Optional<Advice> from(FrontierWorldState state, GoodsParticipant seller, SubjectId container,
            GoodsParticipantView buyer, String item, int quantity, long goodsPrice, long budget, long atTick) {
        if (quantity <= 0) throw new IllegalArgumentException("delivery advice requires a positive quantity");
        var quote = GoodsProcurementTransport.best(state, seller, container, buyer.participant(), item, quantity, goodsPrice, budget, atTick);
        if (quote.isEmpty()) return Optional.empty();
        var value = quote.orElseThrow();
        var cause = GoodsDispatchAdvice.assess(state, buyer, value.organizer(), Map.of(item, value.quantity()), Map.of(item, goodsPrice),
                value.deliveryTicks(), value.journeyTicks(), atTick);
        if (cause == TripDispatchPolicy.Cause.ACCUMULATING) return Optional.empty();
        int minimum = cause == TripDispatchPolicy.Cause.SUPPLY_DEADLINE ? 1
                : Math.toIntExact(Math.max(1, GoodsDispatchAdvice.plannedLot(state, buyer, value.organizer(), item, goodsPrice, value.journeyTicks(), atTick)));
        return Optional.of(new Advice(value.quantity(), value.expense(), minimum, value.cashFreight()));
    }
    private GoodsDeliveryAdvice() { }
}
