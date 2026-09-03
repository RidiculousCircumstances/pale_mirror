package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable public ingress and two-stage work surface for one settlement workshop. */
public record SettlementWorkshopServicePort(SubjectId settlementId, SubjectId workshopId, FacilityFacing facing,
                                           SurfaceAnchor exteriorApproach, SurfaceAnchor approachSurface,
                                           SurfaceAnchor throatSurface, SurfaceAnchor interiorSurface,
                                           SurfaceAnchor inputStation, SurfaceAnchor workStation) {
    public SettlementWorkshopServicePort {
        settlementId = Objects.requireNonNull(settlementId, "workshop port settlement");
        workshopId = Objects.requireNonNull(workshopId, "workshop port id");
        facing = Objects.requireNonNull(facing, "workshop port facing");
        exteriorApproach = Objects.requireNonNull(exteriorApproach, "workshop exterior approach");
        approachSurface = Objects.requireNonNull(approachSurface, "workshop approach");
        throatSurface = Objects.requireNonNull(throatSurface, "workshop throat");
        interiorSurface = Objects.requireNonNull(interiorSurface, "workshop interior");
        inputStation = Objects.requireNonNull(inputStation, "workshop input station");
        workStation = Objects.requireNonNull(workStation, "workshop work station");
        if (!adjacent(exteriorApproach, approachSurface) || !adjacent(approachSurface, throatSurface)
                || !adjacent(throatSurface, interiorSurface) || !adjacent(inputStation, interiorSurface)
                || !adjacent(workStation, inputStation) || inputStation.equals(workStation)) {
            throw new IllegalArgumentException("workshop service port geometry is invalid");
        }
    }

    public static SettlementWorkshopServicePort forWorkshop(SettlementStructure workshop) {
        Objects.requireNonNull(workshop, "settlement workshop");
        if (workshop.kind() != StructureKind.WORKSHOP) throw new IllegalArgumentException("only a workshop owns a workshop service port");
        FacilityFacing facing = workshop.facing(); SurfaceAnchor center = new SurfaceAnchor(workshop.anchor());
        SurfaceAnchor interior = facing.step(center, 2); SurfaceAnchor throat = facing.step(center, 3);
        SurfaceAnchor approach = facing.step(center, 4); SurfaceAnchor exterior = facing.step(approach, 1);
        SurfaceAnchor input = facing.stepLeft(interior, 1);
        return new SettlementWorkshopServicePort(workshop.settlementId(), workshop.id(), facing, exterior, approach, throat, interior,
                input, facing.stepLeft(input, 1));
    }

    public List<SurfaceAnchor> ownedAccessSurfaces() { return List.of(approachSurface); }
    /**
     * The workshop entrance is deliberately a three-wide, two-high loading portal.
     *
     * <p>Only its centre column is the retained traversal throat. The flanking apertures are
     * structural presentation, not alternate pathfinding edges: they make the worker, input
     * station and active work station readable from the public approach without giving a HOT
     * body permission to cut through a different wall cell.</p>
     */
    public List<BlockPosition> throatAirCells() {
        List<SurfaceAnchor> columns = List.of(facing.stepLeft(throatSurface, 1), throatSurface, facing.stepRight(throatSurface, 1));
        return columns.stream().flatMap(surface -> java.util.stream.Stream.of(surface.support().offset(0, 1, 0), surface.support().offset(0, 2, 0))).toList();
    }
    public FacilityTraversalPort topologyPort() {
        return new FacilityTraversalPort(workshopId, facing, List.of(exteriorApproach, approachSurface), throatSurface, interiorSurface,
                List.of(inputStation, workStation), Set.of(TraversalCapability.PEDESTRIAN));
    }
    private static boolean adjacent(SurfaceAnchor first, SurfaceAnchor second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z()) == 1 && Math.abs(first.y() - second.y()) <= 1;
    }
}
