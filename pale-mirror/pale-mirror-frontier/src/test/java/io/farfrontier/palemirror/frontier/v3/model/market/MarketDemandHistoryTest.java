package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarketDemandHistoryTest {
    private static final SubjectId BUYER = new SubjectId("settlement:2");
    private static final MarketDemandHistory.Key KEY = new MarketDemandHistory.Key(BUYER, "minecraft:wheat");
    private static Map<String, MarketDemandHistory.Quote> quote(long quantity, long price) {
        return Map.of(KEY.item(), new MarketDemandHistory.Quote(quantity, price));
    }
    @Test void partialFillsDoNotEraseTheWindowAndRepeatedReviewsDoNotMultiplyDemand() {
        var history = MarketDemandHistory.empty();
        for (int tick = 0; tick <= 128; tick++)
            history = history.observe(BUYER, tick, 128, tick == 64 ? Map.of() : quote(64, 500));
        var observed = history.observations().get(KEY);
        assertEquals(0, observed.firstTick());
        assertEquals(64, observed.sustainedQuote(128, 128, 8).orElseThrow().quantity());
        assertTrue(observed.sustainedQuote(137, 128, 8).isEmpty());
        assertTrue(observed.samples().size() <= MarketDemandHistory.WINDOW_BUCKETS + 1);
    }
    @Test void oldLowPricesAgeOutAndAbsentDemandEventuallyDisappears() {
        var history = MarketDemandHistory.empty().observe(BUYER, 0, 128, quote(1, 1));
        for (int tick = 4; tick <= 256; tick += 4) history = history.observe(BUYER, tick, 128, quote(64, 500));
        assertEquals(new MarketDemandHistory.Quote(64, 500), history.observations().get(KEY).sustainedQuote(256, 128, 8).orElseThrow());
        for (int tick = 260; tick <= 388; tick += 4) history = history.observe(BUYER, tick, 128, Map.of());
        assertTrue(history.observations().isEmpty());
    }
    @Test void missingBucketsReduceDemandRatherThanPretendingContinuousInterest() {
        var history = MarketDemandHistory.empty().observe(BUYER, 0, 128, quote(64, 500));
        history = history.observe(BUYER, 128, 128, quote(64, 500));
        assertEquals(3, history.observations().get(KEY).sustainedQuote(128, 128, 8).orElseThrow().quantity());
        var observed = history;
        assertThrows(IllegalArgumentException.class, () -> observed.observe(BUYER, 127, 128, quote(64, 500)));
        assertThrows(IllegalArgumentException.class, () -> new MarketDemandHistory.Observation(0, 4, 4,
                List.of(new MarketDemandHistory.Sample(2, 1, 1))));
    }
}
