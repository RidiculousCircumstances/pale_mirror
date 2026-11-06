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
    @Test void boundedPrefixKeepsTheDistantSemanticGoal() {
        var start = SurfaceAnchor.at(288, 63, 10);
        var target = SurfaceAnchor.at(450, 63, 10);
        var goal = new FrontierV3GoalNavigation.Goal(List.of(target), TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.ObservedWorld(new WorldBounds(-512,-512,1024,1024)), Optional.empty(), List.of());
        var prefix = java.util.stream.IntStream.rangeClosed(0,24).mapToObj(i->start.offset(i,0,0)).toList();
        var leg = FrontierV3RouteNavigation.leg(goal, prefix, 0);
        assertEquals(goal,leg.goal());
        assertEquals(List.of(start.offset(12,0,0),start.offset(8,0,0),start.offset(4,0,0)),leg.stations());
        var last = FrontierV3RouteNavigation.leg(goal,prefix,20);
        assertEquals(List.of(prefix.getLast()),last.stations());
        assertFalse(last.stations().contains(target),"prefix exhaustion cannot submit the distant terminal goal to a local provider");
    }
}
