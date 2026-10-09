package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Optional;

/** Ephemeral waiting leg; never changes the retained owner goal or awards its arrival. */
final class FrontierV3ServiceApproachNavigation {
    private FrontierV3ServiceApproachNavigation() { }

    static Optional<FrontierV3GoalNavigation.Goal> outsideGoal(List<SurfaceAnchor> route,
            ServiceAccessBoundary boundary, WorldBounds bounds) {
        int end = 0;
        while (end < route.size() && boundary.cleared(route.get(end).standingBody())) end++;
        if (end == 0) return Optional.empty(); // An incumbent must use the witnessed exit protocol instead.
        var prefix = List.copyOf(route.subList(0, end));
        var target = prefix.getLast();
        return Optional.of(new FrontierV3GoalNavigation.Goal(List.of(target), TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.OutsideService(new FrontierV3NavigationScope.ObservedWorld(bounds), boundary),
                Optional.empty(), prefix));
    }
}
