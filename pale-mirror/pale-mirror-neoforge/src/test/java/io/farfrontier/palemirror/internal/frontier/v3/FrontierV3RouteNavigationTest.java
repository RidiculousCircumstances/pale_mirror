package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3RouteNavigationTest {
    @Test void distantFallbackRetriesHintsWithoutCancellingAProgressingPath() {
        var start = SurfaceAnchor.at(288, 63, 10);
        var local = SurfaceAnchor.at(299, 63, 10);
        var target = SurfaceAnchor.at(375, 64, 14);
        var goal = new FrontierV3GoalNavigation.Goal(List.of(target), TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.ObservedWorld(new WorldBounds(-512, -512, 1024, 1024)), Optional.empty(),
                List.of(start, local, target));
        var fallback = new FrontierV3RouteNavigation.Leg(goal, 0, goal.legalStations(), 40L);
        assertFalse(fallback.retryHints(39L, FrontierV3MinecraftGoalNavigation.Status.BLOCKED));
        assertTrue(fallback.retryHints(40L, FrontierV3MinecraftGoalNavigation.Status.BLOCKED));
        assertFalse(fallback.retryHints(100L, FrontierV3MinecraftGoalNavigation.Status.IN_PROGRESS));
        assertFalse(fallback.retryHints(100L, FrontierV3MinecraftGoalNavigation.Status.ARRIVED));
        assertFalse(new FrontierV3RouteNavigation.Leg(goal, 0, List.of(local), -1L)
                .retryHints(100L, FrontierV3MinecraftGoalNavigation.Status.BLOCKED));
    }
}
