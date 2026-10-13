package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketPricingTest {
    @Test void completedVolumeAndRecencySetReferenceWithoutTimeOnlyDriftOrDuplicateSales() {
        var first = sale(1, 100, 1, 10);
        var history = MarketPriceHistory.empty().record("grain", first).record("grain", sale(2, 100, 9, 2));
        assertEquals(new FixedScalar(2_800_000), history.reference("grain", 1000).orElseThrow());
        var before = history;
        assertThrows(IllegalArgumentException.class, () -> before.record("grain", first));
        history = history.record("grain", sale(3, 1100, 10, 4));
        assertTrue(history.reference("grain", 1000).orElseThrow().raw() > 3_900_000);
        for (int i = 4; i <= 60; i++) history = history.record("grain", sale(i, 1100 + i, 1, 4));
        assertEquals(MarketPriceHistory.MAX_SALES, history.commodities().get("grain").size());
    }
    @Test void quotationUsesRealSignalsAndBoundedCadenceInsteadOfRouteWaitingOrRepeatedWakeups() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.initial();
        var initial = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 64,
                Optional.empty(), Optional.empty(), 0, rules);
        var waiting = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 64,
                Optional.empty(), Optional.of(initial), 2400, rules);
        assertEquals(initial.ask(), waiting.ask()); assertEquals(initial.bid(), waiting.bid());
        var sold = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 32,
                Optional.of(new SubjectId("receipt:sold")), Optional.of(waiting), 4800, rules);
        assertEquals(new FixedScalar(2_100_000), sold.ask());
        assertEquals(sold, policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 128,
                sold.lastSale(), Optional.of(sold), 4801, rules));
        var accumulated = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 128,
                sold.lastSale(), Optional.of(sold), 7200, rules);
        assertEquals(new FixedScalar(1_995_000), accumulated.ask());
    }
    private static MarketPriceHistory.Sale sale(int n, long tick, int count, long price) {
        return new MarketPriceHistory.Sale(new SubjectId("receipt:" + n), new SubjectId("settlement:1"),
                new SubjectId("settlement:2"), tick, count, FixedScalar.whole(price));
    }
}
