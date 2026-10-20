package io.farfrontier.palemirror.frontier.v3.model.supply;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SourcingPurchaseCostTest {
    @Test void fixedTripIsPaidOncePerActualLotNotOncePerItemOrForAbsentSupply() {
        var market = new SourcingMarket(100, 0, Map.of(), List.of(
                new SourcingMarket.Offer("warehouse-a", "ore", 10, 2, 2, 5, 6, 6),
                new SourcingMarket.Offer("warehouse-b", "ore", 4, 3, 3, 5, 4, 4)));
        assertEquals(new SourcingPurchaseCost.Quote(1, 7), SourcingPurchaseCost.quote(market, "ore", 1, 5));
        assertEquals(new SourcingPurchaseCost.Quote(10, 26), SourcingPurchaseCost.quote(market, "ore", 10, 5));
        assertEquals(new SourcingPurchaseCost.Quote(14, 42), SourcingPurchaseCost.quote(market, "ore", 20, 5));
        assertEquals(new SourcingPurchaseCost.Quote(0, 0), SourcingPurchaseCost.quote(market, "ore", 10, 4));
        assertEquals(72, SourcingPurchaseCost.forecast(market, "ore", 20, 5));
    }
}
