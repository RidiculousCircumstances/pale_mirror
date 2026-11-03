package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3RouteNavigationTest {
    @Test void unchangedSemanticGoalRetainsItsAcceptedRouteWhenAdvisoryCacheDisappears() {
        var start=SurfaceAnchor.at(0,63,0);var target=SurfaceAnchor.at(60,63,0);
        var scope=new FrontierV3NavigationScope.ObservedWorld(new WorldBounds(-128,-128,256,256));
        var retained=new FrontierV3GoalNavigation.Goal(List.of(target),TraversalCapability.PEDESTRIAN,scope,Optional.empty(),
                List.of(start,start.offset(12,0,0),target));
        var refreshed=new FrontierV3GoalNavigation.Goal(List.of(target),TraversalCapability.PEDESTRIAN,scope,Optional.empty(),List.of());
        assertSame(retained,FrontierV3RouteNavigation.continuedGoal(refreshed,retained));
        var changed=new FrontierV3GoalNavigation.Goal(List.of(target.offset(0,0,1)),TraversalCapability.PEDESTRIAN,scope,Optional.empty(),List.of());
        assertSame(changed,FrontierV3RouteNavigation.continuedGoal(changed,retained));
        var restricted=new FrontierV3GoalNavigation.Goal(List.of(target),TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.along(List.of(start,target),List.of(target))),Optional.empty(),List.of());
        assertSame(restricted,FrontierV3RouteNavigation.continuedGoal(restricted,retained),"hard scope changes cannot reuse old permission");
    }
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
