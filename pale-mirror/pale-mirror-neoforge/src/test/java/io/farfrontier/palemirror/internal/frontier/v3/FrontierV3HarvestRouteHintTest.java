package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3HarvestRouteHintTest {
    @Test void aHotGoalRetainsItsOrderWithoutAcquiringAnUnusedColdSearch() throws Exception {
        var state = FrontierV3FixtureCatalog.resourceSiteHarvestConfiguration(new WorldId("frontier:hot-harvest-hint"), 421L).initialState();
        var job = state.resourceSites().site(new SubjectId("site:1-wheat-field")).harvestJobs().values().stream()
                .reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        var goal = ResourceSiteHarvestGoal.current(state, job);
        try (var binding = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            throw new AssertionError("a loaded physical navigator cannot acquire a COLD route merely for an optional hint");
        })) {
            var nativeGoal = FrontierV3ResourceSiteHarvestSceneExecutor.hotGoal(state, job, goal);
            assertEquals(java.util.Optional.of(goal.movementOrder()), nativeGoal.order());
            assertEquals(goal.movementOrder().legalStations(), nativeGoal.legalStations());
            assertTrue(nativeGoal.routeHint().isEmpty(), "missing optional advice does not block the native navigation goal");
        }
    }
}
