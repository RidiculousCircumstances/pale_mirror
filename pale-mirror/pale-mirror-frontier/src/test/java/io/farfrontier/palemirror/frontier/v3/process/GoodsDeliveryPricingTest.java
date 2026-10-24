package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.market.DeliveryCost;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GoodsDeliveryPricingTest {
    @Test void bothOrganizersRespectAcquisitionCostButOnlySellerDeliveryInvoicesFreight() {
        var state = ConsolidatedTradeFixture.initial(new WorldId("frontier:acquisition-cost"), 125L, false);
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var buyer = state.companies().goodsTrade().participants().participants().get(seller.known().getFirst().party().id());
        var sell = new GoodsTradeOrder(new SubjectId("order:acquisition-sell"), seller.party(), buyer.party(),
                GoodsTradeOrder.Side.SELL, FrontierWorldState.depotId(seller.settlementId()), "minecraft:bread", 16, 0, FixedScalar.whole(2), 10000);
        var buy = new GoodsTradeOrder(new SubjectId("order:acquisition-buy"), buyer.party(), seller.party(),
                GoodsTradeOrder.Side.BUY, FrontierWorldState.depotId(buyer.settlementId()), "minecraft:bread", 16, 0, FixedScalar.whole(3), 10000);
        state = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().place(sell).place(buy)));
        state = AcceptedEconomyFixture.publish(state, seller.party().id(), 400);
        state = AcceptedEconomyFixture.publish(state, buyer.party().id(), 400);
        var search = GoodsOrderMatching.first(state, buyer, 400, Optional.of(seller.party()), Optional.of(buyer.party()));
        assertTrue(search.match().isPresent(), search.disposition());
        var match = search.match().orElseThrow();
        var allocation = match.allocations().getFirst();
        for (var mode : GoodsTradeFulfillment.Mode.values()) {
            var contract = match.contract().declareFulfillment(new GoodsTradeFulfillment(mode,
                    mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? seller.party() : buyer.party()));
            var events = List.of(new ProposedEvent(seller.party().id(), new GoodsTradeReserved(contract, match.allocations())));
            var parcels = List.of(new GoodsTripAssembly.Parcel(contract, allocation.claim(), allocation.accountId()));
            assertTrue(GoodsDeliveryPricing.reserve(state, events, parcels, new DeliveryCost(FixedScalar.whole(1000), 10000), 400).isEmpty(),
                    "own provisions cannot bypass the acquisition limit: " + mode);
            var accepted = GoodsDeliveryPricing.reserve(state, events, parcels, new DeliveryCost(FixedScalar.whole(1), 10000), 400).orElseThrow();
            var priced = accepted.parcels().getFirst().contract();
            assertEquals(mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY, priced.deliveryUnitPrice().raw() > 0);
            assertEquals(priced.outstandingPrice(), accepted.state().inventory().economics().reservations().get(priced.financialReservationId()).amount());
            assertEquals(state.inventory().economics().require(buyer.party().id()).balance(), accepted.state().inventory().economics().require(buyer.party().id()).balance(),
                    "valuation and reservation cannot settle a sale or debit provisions twice");
        }
        assertTrue(state.companies().goodsTrade().contracts().isEmpty());
    }
}
