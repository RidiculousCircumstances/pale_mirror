package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * Immutable public departure port for a settlement Hall.
 *
 * <p>The port is deliberately derived from the Hall's semantic geometry rather than being an
 * unowned spawn offset. Its throat is an absence in the wall plan; its single outside surface is
 * still an owned, auditable graybox cell. Future assembly and operation travel therefore share
 * the exact same outward connection to the route graph.</p>
 */
public record SettlementAccessPort(SubjectId settlementId, SubjectId hallId, FacilityFacing facing,
                                   SurfaceAnchor interiorSurface, SurfaceAnchor throatSurface,
                                   SurfaceAnchor assemblySurface, SurfaceAnchor routeSurface) {
    public SettlementAccessPort {
        Objects.requireNonNull(settlementId, "access port settlement"); Objects.requireNonNull(hallId, "access port hall");
        Objects.requireNonNull(facing, "access port facing");
        Objects.requireNonNull(interiorSurface, "access port interior"); Objects.requireNonNull(throatSurface, "access port throat");
        Objects.requireNonNull(assemblySurface, "access port assembly"); Objects.requireNonNull(routeSurface, "access port route");
        if (!adjacent(interiorSurface, throatSurface) || !adjacent(throatSurface, assemblySurface) || !adjacent(assemblySurface, routeSurface)) {
            throw new IllegalArgumentException("settlement access port must connect through adjacent floor cells");
        }
    }

    public static SettlementAccessPort forHall(SettlementStructure hall) {
        Objects.requireNonNull(hall, "settlement Hall");
        if (hall.kind() != StructureKind.HALL) throw new IllegalArgumentException("only a settlement Hall owns a public access port");
        SurfaceAnchor center = new SurfaceAnchor(hall.anchor()); FacilityFacing facing = hall.facing();
        return new SettlementAccessPort(hall.settlementId(), hall.id(), facing, facing.step(center, 3), facing.step(center, 4),
                facing.step(center, 5), facing.step(center, 6));
    }

    /** The two body cells that must remain absent from the Hall wall projection. */
    public List<BlockPosition> throatAirCells() { return topologyPort().thresholdHeadroomCells(); }
    /** One owned, support-required outside sill; the route floor itself remains route-network owned. */
    public List<SurfaceAnchor> ownedSurfaces() { return List.of(assemblySurface); }
    public FacilityTraversalPort topologyPort() {
        return new FacilityTraversalPort(hallId, facing, List.of(routeSurface, assemblySurface), throatSurface,
                interiorSurface, List.of(assemblySurface), java.util.Set.of(TraversalCapability.PEDESTRIAN));
    }

    /** Explicit convenience projections for block-plan writers; movement code must use surfaces. */
    public BlockPosition interiorFloor() { return interiorSurface.support(); }
    public BlockPosition throatFloor() { return throatSurface.support(); }
    public BlockPosition assemblyFloor() { return assemblySurface.support(); }
    public BlockPosition routeFloor() { return routeSurface.support(); }

    private static boolean adjacent(SurfaceAnchor first, SurfaceAnchor second) {
        return first.y() == second.y() && Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z()) == 1;
    }
}
