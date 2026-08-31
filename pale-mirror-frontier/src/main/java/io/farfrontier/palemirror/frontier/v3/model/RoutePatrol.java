package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One exact guard traversal. It observes a physical route; it never edits it. */
public record RoutePatrol(SubjectId taskId, SubjectId settlementId, SubjectId guardId, List<BlockPosition> route,
                   int routeIndex, RoutePatrolStatus status, Optional<BlockPosition> obstruction) {
    public RoutePatrol {
        Objects.requireNonNull(taskId, "patrol task"); Objects.requireNonNull(settlementId, "patrol settlement");
        Objects.requireNonNull(guardId, "patrol guard"); route = List.copyOf(route); Objects.requireNonNull(status, "patrol status");
        Objects.requireNonNull(obstruction, "patrol obstruction");
        if (route.size() < 2 || routeIndex < 0 || routeIndex >= route.size()) throw new IllegalArgumentException("patrol route cursor is invalid");
        if (status == RoutePatrolStatus.OBSTRUCTION_CONFIRMED != obstruction.isPresent()) {
            throw new IllegalArgumentException("patrol evidence does not match its status");
        }
        if (status == RoutePatrolStatus.ROUTE_CLEAR && routeIndex != route.size() - 1) {
            throw new IllegalArgumentException("clear patrol must finish its complete route");
        }
        if (status == RoutePatrolStatus.OBSTRUCTION_CONFIRMED && obstruction.isEmpty()) throw new IllegalArgumentException("obstructed patrol needs exact evidence");
    }
    RoutePatrol advance(int nextIndex) {
        if (status != RoutePatrolStatus.EN_ROUTE || nextIndex != routeIndex + 1) throw new IllegalArgumentException("patrol advancement is not sequential");
        return new RoutePatrol(taskId, settlementId, guardId, route, nextIndex,
                nextIndex == route.size() - 1 ? RoutePatrolStatus.ROUTE_CLEAR : RoutePatrolStatus.EN_ROUTE, Optional.empty());
    }
    RoutePatrol confirm(BlockPosition position) {
        if (status != RoutePatrolStatus.EN_ROUTE || !FrontierRouteNetwork.containsOperationSurfaceCell(route, position)) {
            throw new IllegalArgumentException("patrol cannot confirm a foreign obstruction");
        }
        return new RoutePatrol(taskId, settlementId, guardId, route, routeIndex, RoutePatrolStatus.OBSTRUCTION_CONFIRMED, Optional.of(position));
    }
    RoutePatrol fail() {
        if (status != RoutePatrolStatus.EN_ROUTE) throw new IllegalArgumentException("only an active patrol may fail");
        return new RoutePatrol(taskId, settlementId, guardId, route, routeIndex, RoutePatrolStatus.FAILED, Optional.empty());
    }
}
