package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic physical route graph for the finite bootstrap profile.
 *
 * <p>Operations take their COLD/HOT waypoints from this graph; the graybox compiler expands the
 * same axis-aligned segments into its visible three-cell carriageway. There is no second
 * hand-authored route for materialization. The centre cell remains the canonical topology;
 * its two lateral cells are the physical envelope needed by a real convoy body formation and
 * cargo carrier.</p>
 */
public final class FrontierRouteNetwork {
    public static final SubjectId OWNER = new SubjectId("route:frontier-network");
    public static final SubjectId MAINTENANCE_CONTAINER = new SubjectId("container:frontier-route-maintenance");

    private FrontierRouteNetwork() { }

    public static List<BlockPosition> supplyWaypoints(FrontierBootstrap bootstrap, SubjectId settlementId) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(settlementId, "settlement id");
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown route settlement: " + settlementId.value()));
        SettlementAccessPort access = SettlementAccessPort.forHall(settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("settlement lacks its Hall access port")));
        BlockPosition origin = access.routeSurface().support();
        BlockPosition destination = supplyNest(bootstrap).anchor().offset(4, 0, -4);
        BlockPosition lane = origin.offset(0, 0, 36);
        // The previous source route visited the central junction and then retraced the same
        // physical corridor for southern settlements.  A traversal topology is a surveyed
        // graph, not an ordered list allowed to duplicate one surface under two node IDs:
        // retracing would make availability, recovery and HOT/COLD cursors ambiguous.  The
        // direct vertical leg is the same real carriageway without the artificial out-and-back.
        return List.of(origin, lane, new BlockPosition(-405, origin.y(), lane.z()),
                new BlockPosition(-405, destination.y(), destination.z()), destination);
    }

    public static void validateSupplyWaypoints(FrontierBootstrap bootstrap, SubjectId settlementId, List<BlockPosition> route) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(settlementId, "settlement id"); Objects.requireNonNull(route, "route");
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown route settlement: " + settlementId.value()));
        SettlementAccessPort access = SettlementAccessPort.forHall(settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("settlement lacks its Hall access port")));
        if (route.size() < RouteTopology.MIN_WAYPOINTS || route.size() > RouteTopology.MAX_WAYPOINTS || !route.getFirst().equals(access.routeSurface().support())
                || !route.getLast().equals(supplyNest(bootstrap).anchor().offset(4, 0, -4))) throw new IllegalArgumentException("replacement route has invalid endpoints or size");
        for (int index = 1; index < route.size(); index++) {
            BlockPosition from = route.get(index - 1), to = route.get(index);
            if (!bootstrap.bounds().contains(to) || from.y() != to.y() || (from.x() != to.x() && from.z() != to.z())) {
                throw new IllegalArgumentException("replacement route segment is outside bounds or not axis aligned");
            }
        }
    }

    public static HiveNest supplyNest(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        return bootstrap.hive().seedNests().getFirst();
    }

    /**
     * The route's exact maintenance stock has a real, separately claimed surface. A player or a
     * settlement can fund it through normal item custody, but gray concrete elsewhere never
     * becomes route authority.
     */
    public static BlockPosition maintenanceContainerPosition(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        return new BlockPosition(-405, 65, 250);
    }

    public static Set<BlockPosition> surfaceCells(FrontierBootstrap bootstrap) {
        return surfaceCells(bootstrap, RouteTopology.initial());
    }

    public static Set<BlockPosition> surfaceCells(FrontierBootstrap bootstrap, RouteTopology topology) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(topology, "route topology");
        Set<BlockPosition> cells = new LinkedHashSet<>();
        List<Settlement> settlements = bootstrap.settlements();
        for (int index = 0; index < settlements.size(); index++) {
            Settlement settlement = settlements.get(index); BlockPosition anchor = settlement.anchor();
            int laneZ = anchor.z() + 36; int laneX = anchor.x() + 36;
            addCarriagewaySegment(cells, new BlockPosition(anchor.x(), 64, laneZ), new BlockPosition(laneX, 64, laneZ));
            addCarriagewaySegment(cells, new BlockPosition(laneX, 64, anchor.z()), new BlockPosition(laneX, 64, laneZ));
            if (index % 4 != 3) addCarriagewaySegment(cells, new BlockPosition(laneX, 64, laneZ),
                    new BlockPosition(settlements.get(index + 1).anchor().x() + 36, 64, laneZ));
            if (index < 8) addCarriagewaySegment(cells, new BlockPosition(laneX, 64, laneZ),
                    new BlockPosition(laneX, 64, settlements.get(index + 4).anchor().z() + 36));
        }
        for (Settlement settlement : settlements) {
            List<BlockPosition> supply = topology.supplyWaypoints(bootstrap, settlement.id());
            for (int index = 1; index < supply.size(); index++) addCarriagewaySegment(cells, supply.get(index - 1), supply.get(index));
        }
        return Set.copyOf(cells);
    }

    /** True when a canonical floor cell is occupied by the one-block visible route surface. */
    public static boolean isSurfaceCell(FrontierBootstrap bootstrap, RouteTopology topology, BlockPosition position) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(topology, "route topology"); Objects.requireNonNull(position, "route position");
        List<Settlement> settlements = bootstrap.settlements();
        for (int index = 0; index < settlements.size(); index++) {
            Settlement settlement = settlements.get(index); BlockPosition anchor = settlement.anchor();
            int laneZ = anchor.z() + 36, laneX = anchor.x() + 36;
            if (onCarriagewaySegment(position, new BlockPosition(anchor.x(), 64, laneZ), new BlockPosition(laneX, 64, laneZ))
                    || onCarriagewaySegment(position, new BlockPosition(laneX, 64, anchor.z()), new BlockPosition(laneX, 64, laneZ))
                    || index % 4 != 3 && onCarriagewaySegment(position, new BlockPosition(laneX, 64, laneZ),
                    new BlockPosition(settlements.get(index + 1).anchor().x() + 36, 64, laneZ))
                    || index < 8 && onCarriagewaySegment(position, new BlockPosition(laneX, 64, laneZ),
                    new BlockPosition(laneX, 64, settlements.get(index + 4).anchor().z() + 36))) return true;
        }
        for (Settlement settlement : settlements) {
            List<BlockPosition> supply = topology.supplyWaypoints(bootstrap, settlement.id());
            for (int index = 1; index < supply.size(); index++) if (onCarriagewaySegment(position, supply.get(index - 1), supply.get(index))) return true;
        }
        return false;
    }

    /** Cells that a replacement must physically create before the topology may become active. */
    public static List<BlockPosition> constructionCells(FrontierBootstrap bootstrap, RouteTopology active, SubjectId settlementId,
                                                 List<BlockPosition> replacement) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(active, "active topology");
        validateSupplyWaypoints(bootstrap, settlementId, replacement);
        // Only this supply route changes. Building both full-world surface sets made a local
        // construction admission proportional to every settlement corridor and, on recovery,
        // repeated that work for each retained project. The exact set difference is simply the
        // replacement's own surface cells that are not already active anywhere in the network.
        Set<BlockPosition> replacementCells = operationSurfaceCells(replacement);
        ArrayList<BlockPosition> required = new ArrayList<>();
        // Construction is an operation along the proposed corridor, not a spatial batch
        // sorted by world coordinates.  Sorting put Northwatch's first work cell beside the
        // hive (and beyond an unbuilt detour), so its exact crew could never reach the job
        // that it was supposed to start.  The LinkedHashSet keeps the declared route's
        // deterministic egress-to-destination order, letting the crew extend a real usable
        // work front from its settlement without inventing a path or teleporting.
        for (BlockPosition cell : replacementCells) if (!isSurfaceCell(bootstrap, active, cell)) required.add(cell);
        return List.copyOf(required);
    }

    /**
     * A COLD operation can use only its own visible corridor.  A known route-surface loss makes
     * this single-lane graybox corridor unavailable; future routing/repair can choose another
     * graph path, but must not move cargo through an observed physical hole.
     */
    public static boolean isPassable(FrontierBootstrap bootstrap, List<BlockPosition> waypoints,
                              Map<BlockPosition, PhysicalDelta> deltas) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(waypoints, "waypoints"); Objects.requireNonNull(deltas, "physical deltas");
        return deltas.keySet().stream().noneMatch(position -> containsOperationSurfaceCell(waypoints, position));
    }

    public static boolean containsOperationSurfaceCell(List<BlockPosition> waypoints, BlockPosition position) {
        Objects.requireNonNull(waypoints, "waypoints"); Objects.requireNonNull(position, "position");
        return operationSurfaceCells(waypoints).contains(position);
    }

    /**
     * Maps an observed owned graybox carriageway cell to the surveyed centre-line edges it
     * physically supports. This is provider evidence, not a route search: it can only name
     * existing edge IDs and never creates a detour. A sloped provider must submit the exact
     * node/edge evidence through its own topology contract; the current graybox covers its
     * flat three-wide envelope here.
     */
    public static Set<TraversalEdgeId> affectedTraversalEdges(TraversalTopology topology, BlockPosition physicalSurface) {
        Objects.requireNonNull(topology, "traversal topology"); Objects.requireNonNull(physicalSurface, "physical route surface");
        Set<TraversalEdgeId> affected = new LinkedHashSet<>();
        for (TraversalTopology.Edge edge : topology.edges()) {
            BlockPosition from = topology.nodes().get(edge.from()).support();
            BlockPosition to = topology.nodes().get(edge.to()).support();
            boolean matches = from.y() == to.y()
                    ? onCarriagewaySegment(physicalSurface, from, to)
                    : physicalSurface.equals(from) || physicalSurface.equals(to);
            if (matches) affected.add(edge.id());
        }
        return Set.copyOf(affected);
    }

    /**
     * Expands declared axis-aligned waypoints into their one ordered semantic surface path.
     * This is immutable plan compilation, not a runtime Minecraft heightmap or navigation scan.
     */
    public static List<BlockPosition> expandWaypoints(List<BlockPosition> waypoints) {
        Objects.requireNonNull(waypoints, "route waypoints");
        if (waypoints.size() < 2) throw new IllegalArgumentException("route needs at least two waypoints");
        List<BlockPosition> expanded = new ArrayList<>();
        for (int index = 1; index < waypoints.size(); index++) {
            List<BlockPosition> segment = new ArrayList<>();
            addSegment(segment, Objects.requireNonNull(waypoints.get(index - 1), "route waypoint"),
                    Objects.requireNonNull(waypoints.get(index), "route waypoint"));
            if (!expanded.isEmpty()) segment.removeFirst();
            expanded.addAll(segment);
            if (expanded.size() > TraversalTopology.MAX_NODES) throw new IllegalArgumentException("expanded route exceeds traversal topology bound");
        }
        return List.copyOf(expanded);
    }

    /** First physical delta on the segment a COLD patrol has just traversed, in stable block order. */
    public static java.util.Optional<BlockPosition> firstObstructionOnSegment(List<BlockPosition> waypoints, int fromWaypointIndex,
                                                                        Map<BlockPosition, PhysicalDelta> deltas) {
        if (fromWaypointIndex < 0 || fromWaypointIndex >= waypoints.size() - 1) throw new IllegalArgumentException("route segment cursor is invalid");
        if (fromWaypointIndex == 0) return java.util.Optional.empty();
        java.util.ArrayList<BlockPosition> cells = new java.util.ArrayList<>();
        addSegment(cells, waypoints.get(fromWaypointIndex), waypoints.get(fromWaypointIndex + 1));
        return cells.stream().filter(deltas::containsKey).findFirst();
    }

    /**
     * Exact physical evidence for one strategic segment's full owned carriageway, including
     * its two lateral support strips.  Availability remains topology-owned; this only recovers
     * the concrete observed location a patrol must inspect and an engineering task must repair.
     */
    public static java.util.Optional<BlockPosition> firstObstructionOnCarriagewaySegment(List<BlockPosition> waypoints, int fromWaypointIndex,
                                                                                            Map<BlockPosition, PhysicalDelta> deltas) {
        if (fromWaypointIndex < 0 || fromWaypointIndex >= waypoints.size() - 1) throw new IllegalArgumentException("route segment cursor is invalid");
        Set<BlockPosition> cells = new LinkedHashSet<>();
        addCarriagewaySegment(cells, waypoints.get(fromWaypointIndex), waypoints.get(fromWaypointIndex + 1));
        return cells.stream().filter(deltas::containsKey).findFirst();
    }

    private static Set<BlockPosition> operationSurfaceCells(List<BlockPosition> waypoints) {
        Set<BlockPosition> cells = new LinkedHashSet<>();
        for (int index = 1; index < waypoints.size(); index++) addCarriagewaySegment(cells, waypoints.get(index - 1), waypoints.get(index));
        return cells;
    }

    /**
     * Compiles a centre-line route plus one supported cell on each lateral side.  The immutable
     * traversal topology still owns only the centre line; this is its required physical width,
     * not a second path or a navigator-chosen detour.  At a corner both strips intentionally
     * meet to form the one small square a translated convoy needs to turn without teleporting.
     */
    private static void addCarriagewaySegment(Set<BlockPosition> cells, BlockPosition from, BlockPosition to) {
        addSegment(cells, from, to);
        if (from.x() == to.x()) {
            addSegment(cells, from.offset(-1, 0, 0), to.offset(-1, 0, 0));
            addSegment(cells, from.offset(1, 0, 0), to.offset(1, 0, 0));
        } else {
            addSegment(cells, from.offset(0, 0, -1), to.offset(0, 0, -1));
            addSegment(cells, from.offset(0, 0, 1), to.offset(0, 0, 1));
        }
    }

    private static boolean onCarriagewaySegment(BlockPosition position, BlockPosition from, BlockPosition to) {
        return onSegment(position, from, to)
                || from.x() == to.x() && (onSegment(position, from.offset(-1, 0, 0), to.offset(-1, 0, 0))
                || onSegment(position, from.offset(1, 0, 0), to.offset(1, 0, 0)))
                || from.z() == to.z() && (onSegment(position, from.offset(0, 0, -1), to.offset(0, 0, -1))
                || onSegment(position, from.offset(0, 0, 1), to.offset(0, 0, 1)));
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

    private static boolean onSegment(BlockPosition position, BlockPosition from, BlockPosition to) {
        if (position.y() != from.y() || from.y() != to.y()) return false;
        if (from.x() == to.x()) return position.x() == from.x() && position.z() >= Math.min(from.z(), to.z()) && position.z() <= Math.max(from.z(), to.z());
        if (from.z() == to.z()) return position.z() == from.z() && position.x() >= Math.min(from.x(), to.x()) && position.x() <= Math.max(from.x(), to.x());
        throw new IllegalArgumentException("route segment must be axis aligned");
    }
    private static void addSegment(java.util.List<BlockPosition> cells, BlockPosition from, BlockPosition to) {
        if (from.y() != to.y() || (from.x() != to.x() && from.z() != to.z())) throw new IllegalArgumentException("route segment must be horizontal and axis aligned");
        int stepX = Integer.compare(to.x(), from.x()), stepZ = Integer.compare(to.z(), from.z());
        for (int x = from.x(), z = from.z();; x += stepX, z += stepZ) {
            cells.add(new BlockPosition(x, from.y(), z)); if (x == to.x() && z == to.z()) return;
        }
    }
}
