package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable semantic interface of an infirmary: public ingress, throat, interior connector and
 * bounded care stations.
 *
 * <p>Surfaces and body cells are intentionally different values. This lets the same compiled
 * port represent a real one-block grade between public ground and a raised building foundation,
 * without inventing a hidden horizontal lane or a second entrance at runtime.</p>
 */
public record SettlementInfirmaryTreatmentPort(SubjectId settlementId, SubjectId infirmaryId,
                                               FacilityFacing facing, SurfaceAnchor interiorSurface,
                                               SurfaceAnchor throatSurface, SurfaceAnchor approachSurface,
                                               SurfaceAnchor exteriorApproachSurface, SurfaceAnchor patientSurface,
                                               List<SurfaceAnchor> medicSurfaces) {
    public SettlementInfirmaryTreatmentPort {
        Objects.requireNonNull(settlementId, "infirmary port settlement");
        Objects.requireNonNull(infirmaryId, "infirmary port id");
        Objects.requireNonNull(facing, "infirmary port facing");
        Objects.requireNonNull(interiorSurface, "infirmary port interior");
        Objects.requireNonNull(throatSurface, "infirmary port throat");
        Objects.requireNonNull(approachSurface, "infirmary port approach");
        Objects.requireNonNull(exteriorApproachSurface, "infirmary port exterior approach");
        Objects.requireNonNull(patientSurface, "infirmary patient surface");
        medicSurfaces = List.copyOf(Objects.requireNonNull(medicSurfaces, "infirmary medic surfaces"));
        if (medicSurfaces.size() != 2 || !adjacent(interiorSurface, throatSurface) || !adjacent(throatSurface, approachSurface)) {
            throw new IllegalArgumentException("infirmary port must retain one entrance and two medic stations");
        }
        if (medicSurfaces.stream().anyMatch(surface -> surface.y() != patientSurface.y()) || medicSurfaces.contains(patientSurface)) {
            throw new IllegalArgumentException("infirmary treatment formation must use distinct same-level surfaces");
        }
        new TraversalPath(composeArrival(facing, approachSurface, exteriorApproachSurface, throatSurface, interiorSurface));
    }

    /**
     * The current graybox layout is a west-facing compiled provider. The facing is part of the
     * port itself, so a future terrain/provider compiler must supply a different oriented port
     * rather than make a HOT executor infer another entrance from a structure centre.
     */
    public static SettlementInfirmaryTreatmentPort forInfirmary(SettlementStructure infirmary) {
        Objects.requireNonNull(infirmary, "settlement infirmary");
        if (infirmary.kind() != StructureKind.INFIRMARY) throw new IllegalArgumentException("only an infirmary owns a treatment port");
        FacilityFacing facing = FacilityFacing.WEST;
        SurfaceAnchor center = new SurfaceAnchor(infirmary.anchor());
        SurfaceAnchor interior = facing.step(center, 2);
        SurfaceAnchor throat = facing.step(center, 3);
        SurfaceAnchor approach = facing.step(center, 4);
        // Graybox foundations sit one block above the surrounding natural street. This is a
        // declared grade, not an ambiguous feet cell passed through aboveFloor compatibility.
        SurfaceAnchor exterior = approach.offset(facing.x() * 2, -1, -4);
        return new SettlementInfirmaryTreatmentPort(infirmary.settlementId(), infirmary.id(), facing, interior, throat, approach, exterior,
                center.offset(1, 0, 0), List.of(center.offset(0, 0, 1), center.offset(2, 0, 1)));
    }

    /** Two body cells absent from the wall/roof projection above the throat. */
    public List<BlockPosition> throatAirCells() { return List.of(throatSurface.support().offset(0, 1, 0), throatSurface.support().offset(0, 2, 0)); }

    /** Structure-owned access support; exterior street ground remains unowned. */
    public List<SurfaceAnchor> ownedAccessSurfaces() {
        return composeOwnedAccess(facing, approachSurface);
    }

    /** All ingress nodes, from naturally observed street support to internal connector. */
    public List<SurfaceAnchor> arrivalSurfaces() {
        return composeArrival(facing, approachSurface, exteriorApproachSurface, throatSurface, interiorSurface);
    }

    public TraversalPath arrivalPath() { return new TraversalPath(arrivalSurfaces()); }
    public List<SurfaceAnchor> ingressSurfaces() { return List.of(approachSurface, throatSurface, interiorSurface); }

    /** Patient first, then the bounded ordered medical-team positions. */
    public SurfaceAnchor treatmentSurface(int ordinal) {
        return switch (ordinal) {
            case 0 -> patientSurface;
            case 1, 2 -> medicSurfaces.get(ordinal - 1);
            default -> throw new IllegalArgumentException("medical scene exceeds bounded treatment formation");
        };
    }

    private static boolean adjacent(SurfaceAnchor first, SurfaceAnchor second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z()) == 1 && Math.abs(first.y() - second.y()) <= 1;
    }

    private static List<SurfaceAnchor> composeOwnedAccess(FacilityFacing facing, SurfaceAnchor approach) {
        List<SurfaceAnchor> values = new ArrayList<>();
        for (int z = -4; z <= 0; z++) values.add(approach.offset(facing.x(), 0, z));
        values.add(approach);
        return List.copyOf(values);
    }

    private static List<SurfaceAnchor> composeArrival(FacilityFacing facing, SurfaceAnchor approach, SurfaceAnchor exterior,
                                                       SurfaceAnchor throat, SurfaceAnchor interior) {
        List<SurfaceAnchor> values = new ArrayList<>(); values.add(exterior); values.addAll(composeOwnedAccess(facing, approach));
        values.add(throat); values.add(interior);
        return List.copyOf(values);
    }
}
