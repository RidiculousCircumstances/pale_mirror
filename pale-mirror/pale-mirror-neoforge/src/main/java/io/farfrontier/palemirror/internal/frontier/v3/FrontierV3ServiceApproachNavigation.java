package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace;
import java.util.List;
import java.util.Optional;

/** Ephemeral waiting leg; never changes the retained owner goal or awards its arrival. */
final class FrontierV3ServiceApproachNavigation {
    private FrontierV3ServiceApproachNavigation() { }

    /** Travel remains legal before service admission; only crossing its boundary is fenced. */
    static FrontierV3GoalNavigation.Result pursueOutside(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.Mob body, List<SurfaceAnchor> route,
            ServiceAccessBoundary boundary, WorldBounds bounds, FrontierV3ActorActuation actuation,
            Optional<TravelPace> pace) {
        var approach = outsideGoal(route, boundary, bounds);
        if (approach.isEmpty()) {
            FrontierV3GoalNavigation.stop(body, actuation);
            return new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.BLOCKED,
                    "service-access:no-outside-approach",
                    Optional.of(FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE), Optional.empty());
        }
        return FrontierV3GoalNavigation.pursue(level, body, approach.orElseThrow(), actuation, pace);
    }

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
