package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, walkable service boundary for a settlement depot.
 *
 * <p>The depot inventory is not an abstract remote endpoint. Its chest has one exact interior
 * socket, and workers meet it at one of four exterior service stations through an authored doorway.
 * This keeps tool issue/return and future physical loading tied to visible geometry instead of
 * allowing a hand-off merely because the actor and inventory share a settlement ID.</p>
 */
public record SettlementDepotServicePort(SubjectId settlementId, SubjectId depotId, FacilityFacing facing,
                                         SurfaceAnchor exteriorApproach, SurfaceAnchor serviceSurface,
                                         SurfaceAnchor thresholdSurface, SurfaceAnchor socketSurface,
                                         List<SurfaceAnchor> stations) {
    public SettlementDepotServicePort {
        settlementId = Objects.requireNonNull(settlementId, "depot service settlement");
        depotId = Objects.requireNonNull(depotId, "depot service depot");
        facing = Objects.requireNonNull(facing, "depot service facing");
        exteriorApproach = Objects.requireNonNull(exteriorApproach, "depot service exterior approach");
        serviceSurface = Objects.requireNonNull(serviceSurface, "depot service surface");
        thresholdSurface = Objects.requireNonNull(thresholdSurface, "depot service threshold");
        socketSurface = Objects.requireNonNull(socketSurface, "depot service socket");
        stations = List.copyOf(Objects.requireNonNull(stations, "depot service stations"));
        SurfaceAnchor checkedServiceSurface = serviceSurface;
        SurfaceAnchor checkedExteriorApproach = exteriorApproach;
        if (stations.size() < EngineeringRecoveryTeam.MIN_MEMBERS || stations.size() > EngineeringRecoveryTeam.MAX_MEMBERS
                || stations.stream().anyMatch(Objects::isNull) || stations.stream().distinct().count() != stations.size()
                || !adjacent(exteriorApproach, serviceSurface) || !adjacent(serviceSurface, thresholdSurface)
                || !adjacent(thresholdSurface, socketSurface)
                || stations.stream().anyMatch(station -> !adjacent(station, checkedServiceSurface) && !adjacent(station, checkedExteriorApproach))) {
            throw new IllegalArgumentException("depot service port geometry is invalid");
        }
    }

    public static SettlementDepotServicePort forDepot(SettlementStructure depot) {
        Objects.requireNonNull(depot, "depot structure");
        if (depot.kind() != StructureKind.DEPOT) throw new IllegalArgumentException("only a depot owns a depot service port");
        SurfaceAnchor center = new SurfaceAnchor(depot.anchor());
        int exteriorDistance = exteriorDistance(depot.facing());
        SurfaceAnchor threshold = depot.facing().step(center, exteriorDistance - 1);
        SurfaceAnchor service = depot.facing().step(center, exteriorDistance);
        SurfaceAnchor exterior = depot.facing().step(center, exteriorDistance + 1);
        SurfaceAnchor socket = depot.facing().step(center, exteriorDistance - 2);
        return new SettlementDepotServicePort(depot.settlementId(), depot.id(), depot.facing(), exterior, service, threshold, socket,
                List.of(depot.facing().stepLeft(service, 1), depot.facing().stepRight(service, 1),
                        depot.facing().stepLeft(exterior, 1), depot.facing().stepRight(exterior, 1)));
    }

    /** The chest body is one block above this exact supported socket surface. */
    public BlockPosition containerPosition() { return socketSurface.support().offset(0, 1, 0); }
    public List<SurfaceAnchor> ownedAccessSurfaces() {
        java.util.ArrayList<SurfaceAnchor> owned = new java.util.ArrayList<>(); owned.add(serviceSurface); owned.addAll(stations);
        return List.copyOf(owned);
    }
    public List<BlockPosition> throatAirCells() {
        BlockPosition threshold = thresholdSurface.support();
        return List.of(threshold.offset(0, 1, 0), threshold.offset(0, 2, 0));
    }
    public FacilityTraversalPort topologyPort() {
        return new FacilityTraversalPort(depotId, facing, List.of(exteriorApproach, serviceSurface), thresholdSurface,
                socketSurface, stations, Set.of(TraversalCapability.PEDESTRIAN));
    }

    private static int exteriorDistance(FacilityFacing facing) {
        // DEPOT footprint is x=[-4,3], z=[-3,3] about its anchor. The threshold is the
        // respective perimeter floor and service lies exactly one declared surface outside it.
        return switch (facing) {
            case NORTH, SOUTH, EAST -> 4;
            case WEST -> 5;
        };
    }

    private static boolean adjacent(SurfaceAnchor first, SurfaceAnchor second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z()) == 1
                && Math.abs(first.y() - second.y()) <= 1;
    }
}
