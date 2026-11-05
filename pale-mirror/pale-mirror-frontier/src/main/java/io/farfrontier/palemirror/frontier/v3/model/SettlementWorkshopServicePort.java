package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSpatialPlan;
import java.util.*;

/** Read-only production projection of the building's explicit entrance and workplace. */
public final class SettlementWorkshopServicePort {
    private final SubjectId settlementId;
    private final FacilityTraversalPort port;
    private final SurfaceAnchor input, work;
    private SettlementWorkshopServicePort(SubjectId settlementId, FacilityTraversalPort port, SurfaceAnchor input, SurfaceAnchor work) {
        this.settlementId = Objects.requireNonNull(settlementId); this.port = Objects.requireNonNull(port);
        this.input = Objects.requireNonNull(input); this.work = Objects.requireNonNull(work);
        if (input.equals(work) || !port.stations().containsAll(List.of(input, work)))
            throw new IllegalArgumentException("workshop lacks declared interaction stations");
    }
    public static SettlementWorkshopServicePort forWorkshop(SettlementStructure workshop) {
        Objects.requireNonNull(workshop);
        if (!ProductionFacilityLayout.supports(workshop.kind())) throw new IllegalArgumentException("facility has no production service port");
        var layout = BuildingSpatialPlan.of(workshop); var workplace = layout.definition().primaryWorkplaceDefinition();
        return new SettlementWorkshopServicePort(workshop.settlementId(), layout.primaryEntrance(),
                layout.placement().support(workplace.input()), layout.placement().support(workplace.worker()));
    }
    public SubjectId settlementId() { return settlementId; }
    public SubjectId workshopId() { return port.facilityId(); }
    public FacilityFacing facing() { return port.facing(); }
    public SurfaceAnchor exteriorApproach() { return port.exteriorApproach().getFirst(); }
    public SurfaceAnchor approachSurface() { return port.exteriorApproach().getLast(); }
    public SurfaceAnchor throatSurface() { return port.thresholdSurface(); }
    public SurfaceAnchor interiorSurface() { return port.interiorConnector(); }
    public SurfaceAnchor inputStation() { return input; }
    public SurfaceAnchor workStation() { return work; }
    public FacilityTraversalPort topologyPort() { return port; }
    public List<SurfaceAnchor> ownedAccessSurfaces() { return port.exteriorApproach().subList(1, port.exteriorApproach().size()); }
    /** Authoring apertures for simple box producers only. Real assets retain native collision shapes. */
    public List<BlockPosition> throatAirCells() {
        return List.of(facing().stepLeft(throatSurface(), 1), throatSurface(), facing().stepRight(throatSurface(), 1)).stream()
                .flatMap(surface -> java.util.stream.Stream.of(surface.support().offset(0, 1, 0), surface.support().offset(0, 2, 0))).toList();
    }
}
