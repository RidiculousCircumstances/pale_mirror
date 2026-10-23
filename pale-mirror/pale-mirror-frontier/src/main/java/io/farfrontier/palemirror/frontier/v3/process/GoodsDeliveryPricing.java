package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.market.DeliveryCost;
import java.util.*;

/** Composition of declared journey, appetite and market prices. Neither a
 * navigator nor an inventory/finance owner; unadmitted quotes cannot move goods. */
final class GoodsDeliveryPricing {
    record Reserved(FrontierWorldState state, List<ProposedEvent> events, List<GoodsTripAssembly.Parcel> parcels) { }
    /** Rebuild only the uncommitted draft with complete delivered prices. The
     * same reservation then covers partial acceptance, loss and cancellation. */
    static Optional<Reserved> reserve(FrontierWorldState initial, List<ProposedEvent> reservations,
                                      List<GoodsTripAssembly.Parcel> parcels, DeliveryCost cost, long now) {
        var total = new FixedScalar(parcels.stream().mapToLong(p -> p.contract().goodsUnitPrice().multiply(p.quantity()).raw())
                .reduce(0, Math::addExact));
        var events = new ArrayList<ProposedEvent>(); var priced = new ArrayList<GoodsTripAssembly.Parcel>();
        var state = initial;
        for (int i = 0; i < parcels.size(); i++) {
            var parcel = parcels.get(i); var original = parcel.contract();
            var allocatedCost = cost.unitCharge(original.goodsUnitPrice(), total);
            var acquisitionPrice = original.goodsUnitPrice().plus(allocatedCost);
            var unitCharge = original.fulfillment().mode() == GoodsTradeFulfillment.Mode.SELLER_DELIVERY
                    ? allocatedCost : FixedScalar.ZERO;
            var contract = original.quoteDelivery(unitCharge);
            var buyer = state.companies().goodsTrade().participants().participants().get(contract.buyer().id());
            var bid = GoodsOrderMatching.intent(state, buyer, contract.itemKind(), GoodsTradeOrder.Side.BUY, now);
            // Collection spends the buyer's provisions instead of paying a freight
            // invoice. It is not free acquisition, nor a second monetary debit.
            if (bid.isEmpty() || acquisitionPrice.compareTo(bid.orElseThrow().limit()) > 0
                    || acquisitionPrice.compareTo(state.companies().goodsTrade().orders().get(contract.buyOrderId()).unitPriceLimit()) > 0
                    || GoodsParticipantFunding.purchase(GoodsParticipantAssessment.trading(state, buyer, now), state.bootstrap().ruleset().goodsTrade(),
                            contract.itemKind()).compareTo(contract.outstandingPrice()) < 0) return Optional.empty();
            var originalEvent = (GoodsTradeReserved) reservations.get(i).payload();
            var event = new GoodsTradeReserved(contract, originalEvent.allocations());
            state = GoodsTradeStateSupport.reserve(state, contract.seller().id(), contract, event.allocations(), now);
            events.add(new ProposedEvent(contract.seller().id(), event));
            priced.add(new GoodsTripAssembly.Parcel(contract, parcel.stock()));
        }
        return Optional.of(new Reserved(state, List.copyOf(events), List.copyOf(priced)));
    }
    private GoodsDeliveryPricing() { }
}
