package io.farfrontier.palemirror.frontier.v3.model.supply;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReplenishmentLotTest {
    @Test void capacityEconomiesUseAnAnimalWithoutInventingDemandOrCash() {
        var walking = new LotTransportCost.Option(192, 64, 20, 0);
        var animal = new LotTransportCost.Option(192, 192, 20, 0);
        var options = new LotTransportCost(List.of(walking, animal));
        assertEquals(100, ReplenishmentLot.choose(100, 1000, 2, options));
        assertEquals(192, ReplenishmentLot.choose(1000, 1000, 2, options));
        assertEquals(30, ReplenishmentLot.choose(100, 60, 2, options));
        assertEquals(10, ReplenishmentLot.choose(10, 1000, 2, options));
        assertEquals(0, ReplenishmentLot.choose(100, 1, 2, options));
        assertEquals(64, ReplenishmentLot.choose(100, 1000, 2, new LotTransportCost(List.of(walking))));
    }
    @Test void ownedProvisionsDoNotBecomeAnInvoiceAndRepeatedAllocationDoesNotPayTwice() {
        var own = LotTransportCost.fixed(100, 50, 0);
        var invoiced = LotTransportCost.fixed(100, 50, 50);
        assertEquals(50, ReplenishmentLot.choose(100, 100, 2, own));
        assertEquals(25, ReplenishmentLot.choose(100, 100, 2, invoiced));
        var first = invoiced.funded(0, new LotTransportCost.Cost(0, 0), 10, 2, 100).orElseThrow();
        var second = invoiced.funded(10, first.totalTransport(), 30, 2, 30).orElseThrow();
        assertEquals(15, second.added());
        assertEquals(first.totalTransport(), second.totalTransport());
    }
    @Test void deferredSmallNetDeficitDoesNotRecreateATwoItemTripEveryReview() {
        var target = ReplenishmentLot.choose(100, 1000, 2, LotTransportCost.fixed(192, 20, 0));
        var need = new SupplyPlanner.Demand(2, 0, 0, Long.MAX_VALUE,
                java.util.Set.of(SupplyPlanner.Reason.STOCK_REPLENISHMENT), java.util.Optional.empty());
        assertEquals(TripDispatchPolicy.Cause.ACCUMULATING, TripDispatchPolicy.assess(
                List.of(new TripDispatchPolicy.Goods(2, target, need, false)), 1000, 400));
        assertEquals(TripDispatchPolicy.Cause.ECONOMIC_LOT, TripDispatchPolicy.assess(
                List.of(new TripDispatchPolicy.Goods(10, 10, need, false)), 1000, 400));
    }
}
