package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable semantic facility entrance compiled by the facility plan.
 *
 * <p>A port names its exterior approach, threshold and interior connector explicitly. It is not
 * a structure-centre offset for a physical adapter to reinterpret. The same value is used by
 * COLD topology compilation, HOT standing checks and Foundry clearance audits.</p>
 */
public record FacilityTraversalPort(SubjectId facilityId, FacilityFacing facing,
                                    List<SurfaceAnchor> exteriorApproach,
                                    SurfaceAnchor thresholdSurface, SurfaceAnchor interiorConnector,
                                    List<SurfaceAnchor> stations, Set<TraversalCapability> capabilities) {
    public static final int MAX_APPROACH_SURFACES = 32;
    public static final int MAX_STATIONS = 16;

    public FacilityTraversalPort {
        facilityId = Objects.requireNonNull(facilityId, "facility port id");
        facing = Objects.requireNonNull(facing, "facility port facing");
        exteriorApproach = List.copyOf(Objects.requireNonNull(exteriorApproach, "facility exterior approach"));
        thresholdSurface = Objects.requireNonNull(thresholdSurface, "facility threshold surface");
        interiorConnector = Objects.requireNonNull(interiorConnector, "facility interior connector");
        stations = List.copyOf(Objects.requireNonNull(stations, "facility stations"));
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "facility capabilities"));
        if (exteriorApproach.isEmpty() || exteriorApproach.size() > MAX_APPROACH_SURFACES
                || stations.size() > MAX_STATIONS || capabilities.isEmpty()
                || !capabilities.contains(TraversalCapability.PEDESTRIAN)) {
            throw new IllegalArgumentException("facility traversal port bounds/capabilities are invalid");
        }
        List<SurfaceAnchor> ingress = new ArrayList<>(exteriorApproach);
        ingress.add(thresholdSurface); ingress.add(interiorConnector);
        if (new LinkedHashSet<>(ingress).size() != ingress.size() || !adjacentChain(ingress)
                || new LinkedHashSet<>(stations).size() != stations.size()
                || stations.contains(thresholdSurface)) {
            throw new IllegalArgumentException("facility traversal port geometry is invalid");
        }
        for (int stationIndex = 0; stationIndex < stations.size(); stationIndex++) {
            SurfaceAnchor station = stations.get(stationIndex);
            boolean connectedToIngress = ingress.contains(station) || ingress.stream().anyMatch(surface -> adjacent(surface, station));
            boolean connectedToPriorStation = stationIndex > 0 && adjacent(stations.get(stationIndex - 1), station);
            if (!connectedToIngress && !connectedToPriorStation) {
                throw new IllegalArgumentException("facility station is not connected to the declared ingress");
            }
        }
    }

    /** Exact ordered movement corridor, from public exterior to the facility interior. */
    public List<SurfaceAnchor> ingressSurfaces() {
        List<SurfaceAnchor> result = new ArrayList<>(exteriorApproach);
        result.add(thresholdSurface); result.add(interiorConnector);
        return List.copyOf(result);
    }

    /** The two feet-height cells that must remain clear above the semantic throat support. */
    public List<BlockPosition> thresholdHeadroomCells() {
        BlockPosition threshold = thresholdSurface.support();
        return List.of(threshold.offset(0, 1, 0), threshold.offset(0, 2, 0));
    }

    public TraversalTopology ingressTopology(TraversalTopologyId id, long revision, SubjectId provenance) {
        return TraversalTopology.corridor(id, revision, provenance, TraversalKind.PEDESTRIAN,
                capabilities, ingressSurfaces());
    }

    private static boolean adjacentChain(List<SurfaceAnchor> surfaces) {
        for (int index = 1; index < surfaces.size(); index++) {
            if (!adjacent(surfaces.get(index - 1), surfaces.get(index))) return false;
        }
        return true;
    }

    private static boolean adjacent(SurfaceAnchor first, SurfaceAnchor second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z()) == 1
                && Math.abs(first.y() - second.y()) <= 1;
    }
}
