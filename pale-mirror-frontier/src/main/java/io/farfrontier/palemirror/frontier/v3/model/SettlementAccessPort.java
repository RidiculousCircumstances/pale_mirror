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
public record SettlementAccessPort(SubjectId settlementId, SubjectId hallId, BlockPosition interiorFloor,
                                   BlockPosition throatFloor, BlockPosition assemblyFloor, BlockPosition routeFloor) {
    public SettlementAccessPort {
        Objects.requireNonNull(settlementId, "access port settlement"); Objects.requireNonNull(hallId, "access port hall");
        Objects.requireNonNull(interiorFloor, "access port interior"); Objects.requireNonNull(throatFloor, "access port throat");
        Objects.requireNonNull(assemblyFloor, "access port assembly"); Objects.requireNonNull(routeFloor, "access port route");
        if (!adjacent(interiorFloor, throatFloor) || !adjacent(throatFloor, assemblyFloor) || !adjacent(assemblyFloor, routeFloor)) {
            throw new IllegalArgumentException("settlement access port must connect through adjacent floor cells");
        }
    }

    public static SettlementAccessPort forHall(SettlementStructure hall) {
        Objects.requireNonNull(hall, "settlement Hall");
        if (hall.kind() != StructureKind.HALL) throw new IllegalArgumentException("only a settlement Hall owns a public access port");
        BlockPosition interior = hall.anchor().offset(-3, 0, 0);
        BlockPosition throat = hall.anchor().offset(-4, 0, 0);
        BlockPosition assembly = hall.anchor().offset(-5, 0, 0);
        return new SettlementAccessPort(hall.settlementId(), hall.id(), interior, throat, assembly, hall.anchor().offset(-6, 0, 0));
    }

    /** The two body cells that must remain absent from the Hall wall projection. */
    public List<BlockPosition> throatAirCells() { return List.of(throatFloor.offset(0, 1, 0), throatFloor.offset(0, 2, 0)); }
    /** One owned, support-required outside sill; the route floor itself remains route-network owned. */
    public List<BlockPosition> ownedSurfaceCells() { return List.of(assemblyFloor); }

    private static boolean adjacent(BlockPosition first, BlockPosition second) {
        return first.y() == second.y() && Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z()) == 1;
    }
}
