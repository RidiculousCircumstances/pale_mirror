package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** One exact bioform's bounded COLD approach into a named settlement assault. */
public record SettlementAssaultAttacker(SubjectId actorId, List<BlockPosition> route, int routeIndex) {
    public SettlementAssaultAttacker {
        Objects.requireNonNull(actorId, "assault attacker");
        route = List.copyOf(route);
        if (route.isEmpty() || route.size() > 128 || routeIndex < 0 || routeIndex >= route.size()) {
            throw new IllegalArgumentException("assault attacker route cursor is invalid");
        }
        if (route.stream().distinct().count() != route.size()) {
            throw new IllegalArgumentException("assault attacker route cannot repeat a position");
        }
    }

    BlockPosition position() { return route.get(routeIndex); }
    boolean atDestination() { return routeIndex == route.size() - 1; }

    SettlementAssaultAttacker advance(int nextRouteIndex) {
        if (atDestination() || nextRouteIndex != routeIndex + 1) {
            throw new IllegalArgumentException("assault attacker advancement is not sequential");
        }
        return new SettlementAssaultAttacker(actorId, route, nextRouteIndex);
    }
}
