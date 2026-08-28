package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Exact, resumable work order for one replacement route; active topology remains unchanged. */
public record RouteConstruction(SubjectId id, SubjectId settlementId, List<BlockPosition> waypoints,
                                int confirmedCells, RouteConstructionStatus status) {
    public RouteConstruction {
        Objects.requireNonNull(id, "route construction id"); Objects.requireNonNull(settlementId, "route construction settlement");
        waypoints = List.copyOf(Objects.requireNonNull(waypoints, "route construction waypoints"));
        if (confirmedCells < 0) throw new IllegalArgumentException("route construction cursor is negative");
        Objects.requireNonNull(status, "route construction status");
    }

    RouteConstruction withConfirmedCells(int nextConfirmedCells, RouteConstructionStatus nextStatus) {
        return new RouteConstruction(id, settlementId, waypoints, nextConfirmedCells, nextStatus);
    }
}
