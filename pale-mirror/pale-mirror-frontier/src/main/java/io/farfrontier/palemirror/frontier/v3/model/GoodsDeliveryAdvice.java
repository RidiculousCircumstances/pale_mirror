package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.TripDispatchPolicy;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection;
import java.util.*;

/** Both delivery modes share material/funding advice; only seller delivery invoices freight. No route or trip admission.
 * Future output may use a current eligible warehouse but is never reported as stored stock. */
final class GoodsDeliveryAdvice {
    enum Supply { STORED, CONDITIONAL_PRODUCTION }
    record Advice(int quantity, long tripExpense, int minimumDispatchQuantity, long cashDelivery,
                  GoodsTradeFulfillment fulfillment, long deliveryTicks, long workerTicks, io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Departure departure) {
        long deliveryPerItem() { return Math.ceilDiv(tripExpense, quantity); }
        long cashDeliveryPerItem() { return Math.ceilDiv(cashDelivery, quantity); }
    }

    static Optional<Advice> read(FrontierWorldState state, GoodsParticipant seller,
            GoodsParticipantView buyer, String item, int quantity, long goodsPrice, long budget, Supply supply, long atTick) {
        if (quantity <= 0) throw new IllegalArgumentException("delivery advice requires a positive quantity");
        var choices = new TreeMap<SubjectId, Advice>();
        for (var container : GoodsParticipantStorage.containers(state, seller, item)) {
            int carried = supply == Supply.STORED
                    ? Math.min(quantity, GoodsParticipantStorage.unclaimed(state, seller, container, item)) : quantity;
            if (carried == 0) continue;
            var freight = from(state, seller, container, buyer, item, carried, goodsPrice, budget, atTick);
            freight.ifPresent(value -> choices.put(container, value));
        }
        var need = new ProcurementSelection.Requirement(item, quantity,
                buyer.supplyPlan().map(p -> p.require(item).deadlineTicks()).orElse(Long.MAX_VALUE));
        var offers = choices.entrySet().stream().map(e -> new ProcurementSelection.Offer(e.getKey(),
                Map.of(item, new ProcurementSelection.Line(e.getValue().quantity(), new FixedScalar(goodsPrice))),
                new FixedScalar(e.getValue().tripExpense()), e.getValue().deliveryTicks(), e.getValue().workerTicks(), e.getValue().departure())).toList();
        long labour = state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing()
                .map(io.farfrontier.palemirror.frontier.v3.model.supply.SourcingRules::labourValuePerTick).orElse(0L);
        return new ProcurementSelection().preferred(need, offers, labour).map(value -> choices.get(value.id()));
    }

    static Optional<Advice> from(FrontierWorldState state, GoodsParticipant seller, SubjectId container,
            GoodsParticipantView buyer, String item, int quantity, long goodsPrice, long budget, long atTick) {
        if (quantity <= 0) throw new IllegalArgumentException("delivery advice requires a positive quantity");
        var quote = GoodsProcurementTransport.best(state, seller, container, buyer.participant(), item, quantity, goodsPrice, budget, atTick,
                buyer.supplyPlan().map(p -> p.require(item).deadlineTicks()).orElse(Long.MAX_VALUE));
        if (quote.isEmpty()) return Optional.empty();
        var value = quote.orElseThrow();
        var cause = GoodsDispatchAdvice.assess(state, buyer, value.organizer(), value.mode(), Map.of(item, value.quantity()), Map.of(item, goodsPrice),
                value.deliveryTicks(), value.journeyTicks(), atTick);

        int minimum = !cause.permitsDeparture() || cause == TripDispatchPolicy.Cause.SUPPLY_DEADLINE ? 1
                : Math.toIntExact(Math.max(1, GoodsDispatchAdvice.plannedLot(state, buyer, value.organizer(), value.mode(), item, goodsPrice, value.journeyTicks(), atTick)));
        return Optional.of(new Advice(value.quantity(), value.expense(), minimum, value.cashFreight(),
                new GoodsTradeFulfillment(value.mode(), value.organizer().party()), value.deliveryTicks(), value.workerTicks(), cause.permitsDeparture()
                        ? io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Departure.READY
                        : io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Departure.DEFERRED));
    }
    private GoodsDeliveryAdvice() { }
}
