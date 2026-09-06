package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** One exact bioform's persisted COLD approach route into a route engagement. */
public record EngagementAttacker(SubjectId actorId, List<BlockPosition> route, int routeIndex) {
    public EngagementAttacker {
        Objects.requireNonNull(actorId, "engagement attacker"); route = List.copyOf(route);
        if (route.isEmpty() || route.size() > 128 || routeIndex < 0 || routeIndex >= route.size()) {
            throw new IllegalArgumentException("engagement attacker route cursor is invalid");
        }
        if (route.stream().distinct().count() != route.size()) throw new IllegalArgumentException("engagement attacker route cannot repeat a position");
    }

    public BlockPosition position() { return route.get(routeIndex); }
    public boolean atDestination() { return routeIndex == route.size() - 1; }

    public EngagementAttacker advance(int nextRouteIndex) {
        if (atDestination() || nextRouteIndex != routeIndex + 1) throw new IllegalArgumentException("engagement attacker advancement is not sequential");
        return new EngagementAttacker(actorId, route, nextRouteIndex);
    }
}
