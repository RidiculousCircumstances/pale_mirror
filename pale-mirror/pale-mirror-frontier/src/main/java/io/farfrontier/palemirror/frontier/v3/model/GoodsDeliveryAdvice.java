package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Shared provisional cash freight and dispatch eligibility; no route or trip admission.
 * Future output may use a current eligible warehouse but is never reported as stored stock. */
final class GoodsDeliveryAdvice {
    enum Supply { STORED, CONDITIONAL_PRODUCTION }

    static Optional<FixedScalar> read(FrontierWorldState state, GoodsParticipant seller,
            GoodsParticipantView buyer, String item, int quantity, Supply supply, long atTick) {
        if (quantity <= 0) throw new IllegalArgumentException("delivery advice requires a positive quantity");
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isEmpty()) return Optional.of(FixedScalar.ZERO);
        FixedScalar best = null;
        for (var container : GoodsParticipantStorage.containers(state, seller, item)) {
            int carried = supply == Supply.STORED
                    ? Math.min(quantity, GoodsParticipantStorage.unclaimed(state, seller, container, item)) : quantity;
            if (carried == 0) continue;
            var freight = from(state, container, buyer, item, carried, atTick);
            if (freight.isPresent() && (best == null || freight.orElseThrow().compareTo(best) < 0)) best = freight.orElseThrow();
        }
        return Optional.ofNullable(best);
    }

    static Optional<FixedScalar> from(FrontierWorldState state, SubjectId container,
            GoodsParticipantView buyer, String item, int quantity, long atTick) {
        if (quantity <= 0) throw new IllegalArgumentException("delivery advice requires a positive quantity");
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isEmpty()) return Optional.of(FixedScalar.ZERO);
        return ProcurementTravelForecast.read(state, container, item, buyer.participant())
                .filter(timing -> GoodsDispatchAdvice.assess(state, buyer, Map.of(item, quantity), timing.deliveryTicks(), atTick)
                        != io.farfrontier.palemirror.frontier.v3.model.supply.TripDispatchPolicy.Cause.ACCUMULATING)
                .map(timing -> new FixedScalar(Math.ceilDiv(GoodsTransportValuation
                        .estimate(state, buyer.participant(), timing.journeyTicks(), 1).resourceExpense().raw(), quantity)));
    }
    private GoodsDeliveryAdvice() { }
}
