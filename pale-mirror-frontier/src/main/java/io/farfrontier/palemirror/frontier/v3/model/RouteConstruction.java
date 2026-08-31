package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact, resumable work order for one replacement route; active topology remains unchanged. */
public record RouteConstruction(SubjectId id, SubjectId settlementId, List<BlockPosition> waypoints,
                                int confirmedCells, RouteConstructionStatus status, Optional<SubjectId> cargoId) {
    public RouteConstruction {
        Objects.requireNonNull(id, "route construction id"); Objects.requireNonNull(settlementId, "route construction settlement");
        waypoints = List.copyOf(Objects.requireNonNull(waypoints, "route construction waypoints"));
        if (confirmedCells < 0) throw new IllegalArgumentException("route construction cursor is negative");
        Objects.requireNonNull(status, "route construction status");
        cargoId = Objects.requireNonNull(cargoId, "route construction cargo");
    }

    public RouteConstruction(SubjectId id, SubjectId settlementId, List<BlockPosition> waypoints,
                             int confirmedCells, RouteConstructionStatus status) {
        this(id, settlementId, waypoints, confirmedCells, status, Optional.empty());
    }

    /** Stable planned identities for the one exact replacement-cell cargo, before it is loaded. */
    public SubjectId plannedCargoId() {
        return new SubjectId("cargo:route-build-" + id.value().substring("construction:".length()) + "-" + confirmedCells);
    }

    public SubjectId plannedCargoItemId() {
        return new SubjectId("item:route-build-" + id.value().substring("construction:".length()) + "-" + confirmedCells);
    }

    RouteConstruction withConfirmedCells(int nextConfirmedCells, RouteConstructionStatus nextStatus) {
        return new RouteConstruction(id, settlementId, waypoints, nextConfirmedCells, nextStatus, cargoId);
    }

    RouteConstruction withCargo(SubjectId nextCargoId) {
        if (cargoId.isPresent()) throw new IllegalArgumentException("route construction cargo is already assigned");
        return new RouteConstruction(id, settlementId, waypoints, confirmedCells, status, Optional.of(Objects.requireNonNull(nextCargoId, "route construction cargo")));
    }

    RouteConstruction withoutCargo() {
        if (cargoId.isEmpty()) throw new IllegalArgumentException("route construction has no cargo to clear");
        return new RouteConstruction(id, settlementId, waypoints, confirmedCells, status, Optional.empty());
    }
}
