package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact repair operation for one observed PM-owned route cell.
 *
 * <p>It never contains replacement waypoints and cannot alter topology.  The sole repair cell
 * remains the physical-loss evidence until an observed physical receipt closes this operation.</p>
 */
public record RouteMaintenance(SubjectId id, SubjectId settlementId, BlockPosition repairCell,
                               GrayboxSemanticPart semanticPart,
                               RouteMaintenanceStatus status, Optional<SubjectId> cargoId,
                               EngineeringRecoveryTeam team, Optional<EngineeringWorkAssembly> assembly)
        implements EngineeringWorkOrder {
    public static final int WORK_CELL_COUNT = 1;

    public RouteMaintenance {
        Objects.requireNonNull(id, "route maintenance id");
        Objects.requireNonNull(settlementId, "route maintenance settlement");
        Objects.requireNonNull(repairCell, "route maintenance repair cell");
        Objects.requireNonNull(semanticPart, "route maintenance semantic part");
        Objects.requireNonNull(status, "route maintenance status");
        cargoId = Objects.requireNonNull(cargoId, "route maintenance cargo");
        team = Objects.requireNonNull(team, "route maintenance team");
        assembly = Objects.requireNonNull(assembly, "route maintenance assembly");
        if (semanticPart != GrayboxSemanticPart.ROUTE_SURFACE && semanticPart != GrayboxSemanticPart.ROUTE_FOUNDATION) {
            throw new IllegalArgumentException("route maintenance must repair a route surface or foundation");
        }
        if (!team.ownerId().equals(id) || !team.settlementId().equals(settlementId)) {
            throw new IllegalArgumentException("route maintenance team must belong to its exact operation and settlement");
        }
    }

    @Override public List<BlockPosition> workCells() { return List.of(repairCell); }
    @Override public int confirmedCells() { return status == RouteMaintenanceStatus.READY ? WORK_CELL_COUNT : 0; }
    @Override public Optional<EngineeringRecoveryTeam> engineeringTeam() { return Optional.of(team); }
    @Override public boolean building() { return status == RouteMaintenanceStatus.BUILDING; }
    @Override public boolean readyForToolReturn() { return status == RouteMaintenanceStatus.READY; }

    public GrayboxMaterial expectedMaterial() {
        return switch (semanticPart) {
            case ROUTE_SURFACE -> GrayboxMaterial.ROUTE;
            case ROUTE_FOUNDATION -> GrayboxMaterial.ROUTE_FOUNDATION;
            default -> throw new IllegalStateException("validated route maintenance has an unsupported semantic part");
        };
    }

    /** Stable identities for the one exact repair unit before source pickup. */
    public SubjectId plannedCargoId() { return new SubjectId("cargo:route-maintenance-" + id.value().replace(':', '-')); }
    public SubjectId plannedCargoItemId() { return new SubjectId("item:route-maintenance-" + id.value().replace(':', '-')); }

    RouteMaintenance withCargo(SubjectId nextCargo) {
        if (cargoId.isPresent()) throw new IllegalArgumentException("route maintenance cargo is already assigned");
        return new RouteMaintenance(id, settlementId, repairCell, semanticPart, status,
                Optional.of(Objects.requireNonNull(nextCargo, "route maintenance next cargo")), team, assembly);
    }

    RouteMaintenance withoutCargo() {
        if (cargoId.isEmpty()) throw new IllegalArgumentException("route maintenance has no cargo to clear");
        return new RouteMaintenance(id, settlementId, repairCell, semanticPart, status, Optional.empty(), team, assembly);
    }

    RouteMaintenance withAssembly(EngineeringWorkAssembly nextAssembly) {
        if (assembly.isPresent()) throw new IllegalArgumentException("route maintenance assembly is already declared");
        return new RouteMaintenance(id, settlementId, repairCell, semanticPart, status, cargoId, team,
                Optional.of(Objects.requireNonNull(nextAssembly, "route maintenance assembly")));
    }

    RouteMaintenance withAdvancedAssembly(EngineeringWorkAssembly nextAssembly) {
        if (assembly.isEmpty()) throw new IllegalArgumentException("route maintenance has no assembly to advance");
        return new RouteMaintenance(id, settlementId, repairCell, semanticPart, status, cargoId, team,
                Optional.of(Objects.requireNonNull(nextAssembly, "route maintenance assembly")));
    }

    RouteMaintenance ready() {
        if (status != RouteMaintenanceStatus.BUILDING) throw new IllegalArgumentException("route maintenance is not building");
        return new RouteMaintenance(id, settlementId, repairCell, semanticPart, RouteMaintenanceStatus.READY,
                cargoId, team, Optional.empty());
    }

    RouteMaintenance conflict() {
        if (status != RouteMaintenanceStatus.BUILDING) throw new IllegalArgumentException("route maintenance is not building");
        return new RouteMaintenance(id, settlementId, repairCell, semanticPart, RouteMaintenanceStatus.CONFLICT,
                cargoId, team, assembly);
    }
}
