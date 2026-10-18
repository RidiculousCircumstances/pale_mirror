package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarketDemandHistoryTest {
    private static final SubjectId BUYER = new SubjectId("settlement:2");
    private static final MarketDemandHistory.Key KEY = new MarketDemandHistory.Key(BUYER, "minecraft:wheat");
    @Test void repeatedObservationEstablishesContinuityWithoutSummingDemand() {
        var history = MarketDemandHistory.empty();
        for (int tick = 0; tick <= 100; tick += 10)
            history = history.observe(BUYER, tick, 20, Map.of(KEY.item(), new MarketDemandHistory.Quote(64, 500)));
        var observed = history.observations().get(KEY);
        assertEquals(64, observed.minimumQuantity());
        assertTrue(observed.sustained(100, 100, 20));
        assertFalse(observed.sustained(121, 100, 20));
        assertEquals(100, observed.lastTick());
    }
    @Test void LostFundingOrAnObservationGapResetsContinuity() {
        var funded = Map.of(KEY.item(), new MarketDemandHistory.Quote(64, 500));
        var history = MarketDemandHistory.empty().observe(BUYER, 0, 20, funded);
        assertTrue(history.observe(BUYER, 10, 20, Map.of()).observations().isEmpty());
        var returned = history.observe(BUYER, 30, 20, funded).observations().get(KEY);
        assertEquals(30, returned.firstTick());
        assertFalse(returned.sustained(30, 20, 20));
    }
}
