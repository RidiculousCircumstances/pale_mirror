package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact, resumable work order for one replacement route; active topology remains unchanged. */
public record RouteConstruction(SubjectId id, SubjectId settlementId, List<BlockPosition> waypoints, List<BlockPosition> workCells,
                                int confirmedCells, RouteConstructionStatus status, Optional<SubjectId> cargoId,
                                Optional<EngineeringRecoveryTeam> team, Optional<EngineeringWorkAssembly> assembly) {
    public RouteConstruction {
        Objects.requireNonNull(id, "route construction id"); Objects.requireNonNull(settlementId, "route construction settlement");
        waypoints = List.copyOf(Objects.requireNonNull(waypoints, "route construction waypoints"));
        workCells = List.copyOf(Objects.requireNonNull(workCells, "route construction work cells"));
        if (workCells.size() > 65_535 || workCells.stream().anyMatch(Objects::isNull)
                || !workCells.equals(workCells.stream().sorted(java.util.Comparator.comparingInt(BlockPosition::x)
                .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z)).distinct().toList())) {
            throw new IllegalArgumentException("route construction work cells must be bounded, distinct and ordered");
        }
        if (confirmedCells < 0) throw new IllegalArgumentException("route construction cursor is negative");
        Objects.requireNonNull(status, "route construction status");
        cargoId = Objects.requireNonNull(cargoId, "route construction cargo");
        team = Objects.requireNonNull(team, "route construction team");
        assembly = Objects.requireNonNull(assembly, "route construction assembly");
        if (team.isPresent() && (!team.orElseThrow().ownerId().equals(id) || !team.orElseThrow().settlementId().equals(settlementId))) {
            throw new IllegalArgumentException("route construction team must belong to its exact project and settlement");
        }
        if (assembly.isPresent() && team.isEmpty()) throw new IllegalArgumentException("route construction assembly needs its exact crew");
    }

    public RouteConstruction(SubjectId id, SubjectId settlementId, List<BlockPosition> waypoints,
                             int confirmedCells, RouteConstructionStatus status) {
        this(id, settlementId, waypoints, List.of(), confirmedCells, status, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public RouteConstruction(SubjectId id, SubjectId settlementId, List<BlockPosition> waypoints,
                             int confirmedCells, RouteConstructionStatus status, Optional<SubjectId> cargoId) {
        this(id, settlementId, waypoints, List.of(), confirmedCells, status, cargoId, Optional.empty(), Optional.empty());
    }

    /** Compatibility constructor for schema-84 construction owners before COLD crew approach existed. */
    public RouteConstruction(SubjectId id, SubjectId settlementId, List<BlockPosition> waypoints,
                             int confirmedCells, RouteConstructionStatus status, Optional<SubjectId> cargoId,
                             Optional<EngineeringRecoveryTeam> team) {
        this(id, settlementId, waypoints, List.of(), confirmedCells, status, cargoId, team, Optional.empty());
    }

    /** Compatibility constructor for pre-schema-86 records without their compiled immutable work plan. */
    public RouteConstruction(SubjectId id, SubjectId settlementId, List<BlockPosition> waypoints,
                             int confirmedCells, RouteConstructionStatus status, Optional<SubjectId> cargoId,
                             Optional<EngineeringRecoveryTeam> team, Optional<EngineeringWorkAssembly> assembly) {
        this(id, settlementId, waypoints, List.of(), confirmedCells, status, cargoId, team, assembly);
    }

    /** Stable planned identities for the one exact replacement-cell cargo, before it is loaded. */
    public SubjectId plannedCargoId() {
        return new SubjectId("cargo:route-build-" + id.value().substring("construction:".length()) + "-" + confirmedCells);
    }

    public SubjectId plannedCargoItemId() {
        return new SubjectId("item:route-build-" + id.value().substring("construction:".length()) + "-" + confirmedCells);
    }

    RouteConstruction withConfirmedCells(int nextConfirmedCells, RouteConstructionStatus nextStatus) {
        Optional<EngineeringWorkAssembly> nextAssembly = nextStatus == RouteConstructionStatus.BUILDING ? assembly : Optional.empty();
        return new RouteConstruction(id, settlementId, waypoints, workCells, nextConfirmedCells, nextStatus, cargoId, team, nextAssembly);
    }

    RouteConstruction withCargo(SubjectId nextCargoId) {
        if (cargoId.isPresent()) throw new IllegalArgumentException("route construction cargo is already assigned");
        return new RouteConstruction(id, settlementId, waypoints, workCells, confirmedCells, status, Optional.of(Objects.requireNonNull(nextCargoId, "route construction cargo")), team, assembly);
    }

    RouteConstruction withoutCargo() {
        if (cargoId.isEmpty()) throw new IllegalArgumentException("route construction has no cargo to clear");
        return new RouteConstruction(id, settlementId, waypoints, workCells, confirmedCells, status, Optional.empty(), team, assembly);
    }

    RouteConstruction withAssembly(EngineeringWorkAssembly nextAssembly) {
        if (assembly.isPresent()) throw new IllegalArgumentException("route construction assembly is already declared");
        return new RouteConstruction(id, settlementId, waypoints, workCells, confirmedCells, status, cargoId, team,
                Optional.of(Objects.requireNonNull(nextAssembly, "route construction assembly")));
    }

    RouteConstruction withAdvancedAssembly(EngineeringWorkAssembly nextAssembly) {
        if (assembly.isEmpty()) throw new IllegalArgumentException("route construction has no assembly to advance");
        return new RouteConstruction(id, settlementId, waypoints, workCells, confirmedCells, status, cargoId, team,
                Optional.of(Objects.requireNonNull(nextAssembly, "route construction assembly")));
    }

    RouteConstruction withWorkCells(List<BlockPosition> nextWorkCells) {
        if (!workCells.isEmpty()) throw new IllegalArgumentException("route construction work plan is already declared");
        return new RouteConstruction(id, settlementId, waypoints, nextWorkCells, confirmedCells, status, cargoId, team, assembly);
    }
}
