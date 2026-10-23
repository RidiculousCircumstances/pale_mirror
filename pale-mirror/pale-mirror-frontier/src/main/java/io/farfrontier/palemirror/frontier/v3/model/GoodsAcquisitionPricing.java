package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.market.AcquisitionQuote;
import io.farfrontier.palemirror.frontier.v3.model.market.FundedCargoLot;
import java.util.*;

/** Current stock and shared travel valuation -> provisional landed-price advice. */
final class GoodsAcquisitionPricing {
    static Optional<AcquisitionQuote> estimate(FrontierWorldState state, GoodsParticipant buyer,
                                              String item, long shortage, long now) {
        return estimate(state, buyer, item, shortage, now, Optional.empty());
    }
    static Optional<AcquisitionQuote> purchase(FrontierWorldState state, GoodsParticipant buyer,
                                              String item, long shortage, long now, FixedScalar funding) {
        if (funding.raw() < 0) throw new IllegalArgumentException("negative acquisition funding");
        return estimate(state, buyer, item, shortage, now, Optional.of(funding));
    }
    private static Optional<AcquisitionQuote> estimate(FrontierWorldState state, GoodsParticipant buyer,
                                              String item, long shortage, long now, Optional<FixedScalar> funding) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        if (shortage <= 0 || rules.supplyPlanning().sourcing().isEmpty()) return Optional.empty();
        var trade = state.companies().goodsTrade();
        AcquisitionQuote best = null;
        for (var order : trade.orders().values().stream().sorted(Comparator.comparing(GoodsTradeOrder::id)).toList()) {
            if (order.side() != GoodsTradeOrder.Side.SELL || !order.counterparty().equals(buyer.party())
                    || !order.itemKind().equals(item) || order.expiresAtTick() <= now || order.availableQuantity() == 0
                    || buyer.known().stream().noneMatch(known -> known.party().equals(order.party()))) continue;
            var seller = trade.participants().participants().get(order.party().id());
            if (seller == null || !GoodsParticipantStorage.eligible(state, seller, order.containerId(), item)) continue;
            int quantity = (int) Math.min(shortage, Math.min(GoodsTransportOptions.maximumQuantity(state, item), Math.min(order.availableQuantity(),
                    GoodsParticipantStorage.unclaimed(state, seller, order.containerId(), item))));
            if (quantity == 0) continue;
            var timing = ProcurementTravelForecast.read(state, order.containerId(), item, buyer,
                    TransportOrganization.Itinerary.DESTINATION_COLLECTION);
            if (timing.isEmpty()) continue;
            // Buyer collection spends owned provisions; only goods require a cash payment.
            // Their economic cost still contributes to the landed acquisition quote below.
            if (funding.isPresent()) {
                quantity = FundedCargoLot.quantity(quantity, order.unitPriceLimit().raw(),
                        0, funding.orElseThrow().raw());
                if (quantity == 0) continue;
            }
            var cost = GoodsTransportOptions.forecast(state, buyer, timing.orElseThrow().journeyTicks(),
                    Math.ceilDiv(quantity, GoodsTransportOptions.parcelSize(state, item))).orElseThrow().cost();
            var candidate = new AcquisitionQuote(order.unitPriceLimit(),
                    new FixedScalar(Math.ceilDiv(cost.resourceExpense().raw(), quantity)));
            if (best == null || candidate.total().compareTo(best.total()) < 0) best = candidate;
        }
        return Optional.ofNullable(best);
    }
    private GoodsAcquisitionPricing() { }
}
