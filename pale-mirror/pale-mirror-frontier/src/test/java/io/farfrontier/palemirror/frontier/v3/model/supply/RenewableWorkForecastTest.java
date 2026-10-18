package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenewableWorkForecastTest {
    private static final RenewableWorkForecast.Source RIPE = new RenewableWorkForecast.Source(1, 0, 10, 0, 100, 5, true, false);
    private static final RenewableWorkForecast.Source BARE = new RenewableWorkForecast.Source(1, 15, 10, 100, 100, 5, false, true);
    @Test void addedBareLandDoesNotDelayFirstRipeYieldAndSharedWorkersAreNotMultiplied() {
        var sources = new ArrayList<RenewableWorkForecast.Source>(); sources.add(RIPE);
        sources.addAll(Collections.nCopies(100, BARE));
        var forecast = RenewableWorkForecast.assess(sources, 1, 1000, 1000, 1000);
        assertEquals(new SupplyPlanner.OutputRelease(10, 1), forecast.getFirst());
        assertTrue(forecast.getLast().recipes() <= 100, "finite shared work, not a worker per cell");
        var parallel = RenewableWorkForecast.assess(List.of(RIPE, RIPE), 2, 1000, 1000, 100);
        assertEquals(List.of(new SupplyPlanner.OutputRelease(10, 2)), parallel);
        assertEquals(List.of(new SupplyPlanner.OutputRelease(10, 1), new SupplyPlanner.OutputRelease(20, 2)),
                RenewableWorkForecast.assess(List.of(RIPE, RIPE), 1, 1000, 1000, 100));
    }
    @Test void growthDoesNotOccupyWorkersAndRenewalNeedsNewSowingAndGrowth() {
        var forecast = RenewableWorkForecast.assess(List.of(BARE, BARE), 1, 1000, 1000, 260);
        assertEquals(List.of(new SupplyPlanner.OutputRelease(125, 1), new SupplyPlanner.OutputRelease(140, 2),
                new SupplyPlanner.OutputRelease(240, 3), new SupplyPlanner.OutputRelease(255, 4)), forecast);
        assertTrue(RenewableWorkForecast.assess(List.of(BARE), 1, 1000, 500, 500).isEmpty());
    }
}
