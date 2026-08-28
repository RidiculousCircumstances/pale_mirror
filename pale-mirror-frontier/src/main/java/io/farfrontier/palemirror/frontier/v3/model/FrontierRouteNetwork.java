package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic physical route graph for the finite bootstrap profile.
 *
 * <p>Operations take their COLD/HOT waypoints from this graph; the graybox compiler expands the
 * same axis-aligned segments into its visible route surface. There is no second hand-authored
 * route for materialization.</p>
 */
final class FrontierRouteNetwork {
    static final SubjectId OWNER = new SubjectId("route:frontier-network");
    static final SubjectId MAINTENANCE_CONTAINER = new SubjectId("container:frontier-route-maintenance");

    private FrontierRouteNetwork() { }

    static List<BlockPosition> supplyWaypoints(FrontierBootstrap bootstrap, SubjectId settlementId) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(settlementId, "settlement id");
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown route settlement: " + settlementId.value()));
        BlockPosition origin = settlement.anchor();
        BlockPosition destination = supplyNest(bootstrap).anchor().offset(4, 0, -4);
        BlockPosition egress = origin.offset(-6, 0, 0), lane = egress.offset(0, 0, 36);
        return List.of(origin, egress, lane, new BlockPosition(-405, origin.y(), lane.z()), new BlockPosition(-405, origin.y(), 250),
                new BlockPosition(-405, destination.y(), destination.z()), destination);
    }

    static void validateSupplyWaypoints(FrontierBootstrap bootstrap, SubjectId settlementId, List<BlockPosition> route) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(settlementId, "settlement id"); Objects.requireNonNull(route, "route");
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown route settlement: " + settlementId.value()));
        if (route.size() < RouteTopology.MIN_WAYPOINTS || route.size() > RouteTopology.MAX_WAYPOINTS || !route.getFirst().equals(settlement.anchor())
                || !route.getLast().equals(supplyNest(bootstrap).anchor().offset(4, 0, -4))) throw new IllegalArgumentException("replacement route has invalid endpoints or size");
        for (int index = 1; index < route.size(); index++) {
            BlockPosition from = route.get(index - 1), to = route.get(index);
            if (!bootstrap.bounds().contains(to) || from.y() != to.y() || (from.x() != to.x() && from.z() != to.z())) {
                throw new IllegalArgumentException("replacement route segment is outside bounds or not axis aligned");
            }
        }
    }

    static HiveNest supplyNest(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        return bootstrap.hive().seedNests().getFirst();
    }

    /**
     * The route's exact maintenance stock has a real, separately claimed surface. A player or a
     * settlement can fund it through normal item custody, but gray concrete elsewhere never
     * becomes route authority.
     */
    static BlockPosition maintenanceContainerPosition(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        return new BlockPosition(-405, 65, 250);
    }

    static Set<BlockPosition> surfaceCells(FrontierBootstrap bootstrap) {
        return surfaceCells(bootstrap, RouteTopology.initial());
    }

    static Set<BlockPosition> surfaceCells(FrontierBootstrap bootstrap, RouteTopology topology) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(topology, "route topology");
        Set<BlockPosition> cells = new LinkedHashSet<>();
        List<Settlement> settlements = bootstrap.settlements();
        for (int index = 0; index < settlements.size(); index++) {
            Settlement settlement = settlements.get(index); BlockPosition anchor = settlement.anchor();
            int laneZ = anchor.z() + 36; int laneX = anchor.x() + 36;
            addSegment(cells, new BlockPosition(anchor.x(), 64, laneZ), new BlockPosition(laneX, 64, laneZ));
            addSegment(cells, new BlockPosition(laneX, 64, anchor.z()), new BlockPosition(laneX, 64, laneZ));
            if (index % 4 != 3) addSegment(cells, new BlockPosition(laneX, 64, laneZ),
                    new BlockPosition(settlements.get(index + 1).anchor().x() + 36, 64, laneZ));
            if (index < 8) addSegment(cells, new BlockPosition(laneX, 64, laneZ),
                    new BlockPosition(laneX, 64, settlements.get(index + 4).anchor().z() + 36));
        }
        for (Settlement settlement : settlements) {
            List<BlockPosition> supply = topology.supplyWaypoints(bootstrap, settlement.id());
            for (int index = 2; index < supply.size(); index++) addSegment(cells, supply.get(index - 1), supply.get(index));
        }
        return Set.copyOf(cells);
    }

    /** Cells that a replacement must physically create before the topology may become active. */
    static List<BlockPosition> constructionCells(FrontierBootstrap bootstrap, RouteTopology active, SubjectId settlementId,
                                                 List<BlockPosition> replacement) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(active, "active topology");
        validateSupplyWaypoints(bootstrap, settlementId, replacement);
        Set<BlockPosition> current = surfaceCells(bootstrap, active);
        Set<BlockPosition> target = surfaceCells(bootstrap, active.replaceSupplyRoute(bootstrap, settlementId, replacement));
        ArrayList<BlockPosition> required = new ArrayList<>(target); required.removeAll(current);
        required.sort(Comparator.comparingInt(BlockPosition::x).thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z));
        return List.copyOf(required);
    }

    /**
     * A COLD operation can use only its own visible corridor.  A known route-surface loss makes
     * this single-lane graybox corridor unavailable; future routing/repair can choose another
     * graph path, but must not move cargo through an observed physical hole.
     */
    static boolean isPassable(FrontierBootstrap bootstrap, List<BlockPosition> waypoints,
                              Map<BlockPosition, PhysicalDelta> deltas) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(waypoints, "waypoints"); Objects.requireNonNull(deltas, "physical deltas");
        return deltas.keySet().stream().noneMatch(position -> containsOperationSurfaceCell(waypoints, position));
    }

    static boolean containsOperationSurfaceCell(List<BlockPosition> waypoints, BlockPosition position) {
        Objects.requireNonNull(waypoints, "waypoints"); Objects.requireNonNull(position, "position");
        return operationSurfaceCells(waypoints).contains(position);
    }

    /** First physical delta on the segment a COLD patrol has just traversed, in stable block order. */
    static java.util.Optional<BlockPosition> firstObstructionOnSegment(List<BlockPosition> waypoints, int fromWaypointIndex,
                                                                        Map<BlockPosition, PhysicalDelta> deltas) {
        if (fromWaypointIndex < 0 || fromWaypointIndex >= waypoints.size() - 1) throw new IllegalArgumentException("route segment cursor is invalid");
        if (fromWaypointIndex == 0) return java.util.Optional.empty();
        java.util.ArrayList<BlockPosition> cells = new java.util.ArrayList<>();
        addSegment(cells, waypoints.get(fromWaypointIndex), waypoints.get(fromWaypointIndex + 1));
        return cells.stream().filter(deltas::containsKey).findFirst();
    }

    private static Set<BlockPosition> operationSurfaceCells(List<BlockPosition> waypoints) {
        Set<BlockPosition> cells = new LinkedHashSet<>();
        // The origin-to-egress segment stays inside the settlement silhouette and is intentionally
        // not a route surface. All later segments are the materialized corridor.
        for (int index = 2; index < waypoints.size(); index++) addSegment(cells, waypoints.get(index - 1), waypoints.get(index));
        return cells;
    }

    private static void addSegment(Set<BlockPosition> cells, BlockPosition from, BlockPosition to) {
        if (from.y() != to.y() || (from.x() != to.x() && from.z() != to.z())) {
            throw new IllegalArgumentException("route segment must be horizontal and axis aligned");
        }
        int stepX = Integer.compare(to.x(), from.x()), stepZ = Integer.compare(to.z(), from.z());
        for (int x = from.x(), z = from.z();; x += stepX, z += stepZ) {
            cells.add(new BlockPosition(x, from.y(), z));
            if (x == to.x() && z == to.z()) return;
        }
    }
    private static void addSegment(java.util.List<BlockPosition> cells, BlockPosition from, BlockPosition to) {
        if (from.y() != to.y() || (from.x() != to.x() && from.z() != to.z())) throw new IllegalArgumentException("route segment must be horizontal and axis aligned");
        int stepX = Integer.compare(to.x(), from.x()), stepZ = Integer.compare(to.z(), from.z());
        for (int x = from.x(), z = from.z();; x += stepX, z += stepZ) {
            cells.add(new BlockPosition(x, from.y(), z)); if (x == to.x() && z == to.z()) return;
        }
    }
}
