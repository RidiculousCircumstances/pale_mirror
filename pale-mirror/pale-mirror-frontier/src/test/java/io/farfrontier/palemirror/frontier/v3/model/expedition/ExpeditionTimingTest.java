package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.model.TransportOrganization;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionTimingTest {
    @Test void sellerDeliversBeforeReturningButBuyerMustCollectAndBringGoodsHome() {
        var rules = ExpeditionRules.initial();
        var seller = ExpeditionTiming.assess(rules, 300, 400, TransportOrganization.Itinerary.SOURCE_DELIVERY);
        var buyer = ExpeditionTiming.assess(rules, 400, 300, TransportOrganization.Itinerary.DESTINATION_COLLECTION);
        assertEquals(rules.plannedDuration(300, 0), seller.deliveryTicks());
        assertTrue(seller.deliveryTicks() < seller.durationTicks());
        assertEquals(seller.durationTicks(), buyer.durationTicks());
        assertEquals(buyer.durationTicks(), buyer.deliveryTicks());
    }
}
