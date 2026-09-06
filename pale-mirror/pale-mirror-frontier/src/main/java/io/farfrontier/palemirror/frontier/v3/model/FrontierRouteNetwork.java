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
 * same declared orthogonal segments into its visible three-cell carriageway. A segment may
 * declare a bounded grade; the compiler expands it into surveyed adjacent steps. There is no second
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
            if (!bootstrap.bounds().contains(from) || !bootstrap.bounds().contains(to) || !isDeclaredGrade(from, to)) {
                throw new IllegalArgumentException("replacement route segment is outside bounds, diagonal or too steep");
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
        return compileSurfaceCells(bootstrap, topology);
    }

    /**
     * Compiles the two materialized layers of the exact route network in one pass.
     *
     * <p>The surface and its foundations are one indivisible structural projection.  Keeping
     * them together matters at the NeoForge boundary: rebuilding a 1024-block-world route
     * twice merely to ask for each layer separately can stall the server thread while a player
     * enters a loaded scene.  This is still a pure, immutable compiler result; callers may not
     * cache it as world authority.</p>
     */
    static RouteFootprint footprint(FrontierBootstrap bootstrap, RouteTopology topology) {
        Set<BlockPosition> surfaces = compileSurfaceCells(bootstrap, topology);
        Set<BlockPosition> foundations = new LinkedHashSet<>();
        for (BlockPosition surface : surfaces) {
            int terrain = bootstrap.terrain().supportYAt(surface.x(), surface.z());
            if (terrain >= surface.y()) {
                throw new IllegalArgumentException("route surface is not above its immutable terrain support at " + surface);
            }
            for (int y = terrain + 1; y < surface.y(); y++) {
                BlockPosition footing = new BlockPosition(surface.x(), y, surface.z());
                // A lower declared route deck can be the physical support at a compact graded
                // junction. It keeps its own surface provenance; do not fabricate a second
                // semantic owner for the same block.
                if (!surfaces.contains(footing)) foundations.add(footing);
            }
        }
        return new RouteFootprint(surfaces, Set.copyOf(foundations));
    }

    private static Set<BlockPosition> compileSurfaceCells(FrontierBootstrap bootstrap, RouteTopology topology) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(topology, "route topology");
        Set<BlockPosition> cells = new LinkedHashSet<>();
        List<Settlement> settlements = bootstrap.settlements();
        for (int index = 0; index < settlements.size(); index++) {
            Settlement settlement = settlements.get(index); BlockPosition anchor = settlement.anchor();
            int laneZ = anchor.z() + 36; int laneX = anchor.x() + 36;
            addCarriagewaySegment(cells, surveyedGridSurface(bootstrap, anchor.x(), laneZ), surveyedGridSurface(bootstrap, laneX, laneZ));
            addCarriagewaySegment(cells, surveyedGridSurface(bootstrap, laneX, anchor.z()), surveyedGridSurface(bootstrap, laneX, laneZ));
            if (index % 4 != 3) addCarriagewaySegment(cells, surveyedGridSurface(bootstrap, laneX, laneZ),
                    surveyedGridSurface(bootstrap, settlements.get(index + 1).anchor().x() + 36, laneZ));
            if (index < 8) addCarriagewaySegment(cells, surveyedGridSurface(bootstrap, laneX, laneZ),
                    surveyedGridSurface(bootstrap, laneX, settlements.get(index + 4).anchor().z() + 36));
        }
        for (Settlement settlement : settlements) {
            List<BlockPosition> supply = topology.supplyWaypoints(bootstrap, settlement.id());
            for (int index = 1; index < supply.size(); index++) addCarriagewaySegment(cells, supply.get(index - 1), supply.get(index));
        }
        return Set.copyOf(cells);
    }

    /**
     * Provider-owned graybox fill below elevated route cells. The terrain survey is immutable
     * bootstrap input: this compiler never probes a loaded height-map or asks Minecraft to
     * excavate a slope. A surface at/below its surveyed datum is impossible without an explicit
     * future earthworks provider and therefore fails closed here.
     */
    public static Set<BlockPosition> foundationCells(FrontierBootstrap bootstrap, RouteTopology topology) {
        return footprint(bootstrap, topology).foundationCells();
    }

    /** Exact immutable route layers from one deterministic topology compilation. */
    record RouteFootprint(Set<BlockPosition> surfaceCells, Set<BlockPosition> foundationCells) {
        RouteFootprint {
            surfaceCells = Set.copyOf(surfaceCells); foundationCells = Set.copyOf(foundationCells);
        }
    }

    /** True when a canonical floor cell is occupied by the one-block visible route surface. */
    public static boolean isSurfaceCell(FrontierBootstrap bootstrap, RouteTopology topology, BlockPosition position) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(topology, "route topology"); Objects.requireNonNull(position, "route position");
        List<Settlement> settlements = bootstrap.settlements();
        for (int index = 0; index < settlements.size(); index++) {
            Settlement settlement = settlements.get(index); BlockPosition anchor = settlement.anchor();
            int laneZ = anchor.z() + 36, laneX = anchor.x() + 36;
            if (onCarriagewaySegment(position, surveyedGridSurface(bootstrap, anchor.x(), laneZ), surveyedGridSurface(bootstrap, laneX, laneZ))
                    || onCarriagewaySegment(position, surveyedGridSurface(bootstrap, laneX, anchor.z()), surveyedGridSurface(bootstrap, laneX, laneZ))
                    || index % 4 != 3 && onCarriagewaySegment(position, surveyedGridSurface(bootstrap, laneX, laneZ),
                    surveyedGridSurface(bootstrap, settlements.get(index + 1).anchor().x() + 36, laneZ))
                    || index < 8 && onCarriagewaySegment(position, surveyedGridSurface(bootstrap, laneX, laneZ),
                    surveyedGridSurface(bootstrap, laneX, settlements.get(index + 4).anchor().z() + 36))) return true;
        }
        for (Settlement settlement : settlements) {
            List<BlockPosition> supply = topology.supplyWaypoints(bootstrap, settlement.id());
            for (int index = 1; index < supply.size(); index++) if (onCarriagewaySegment(position, supply.get(index - 1), supply.get(index))) return true;
        }
        return false;
    }

    /**
     * Highest declared carriageway support at one horizontal column.
     *
     * <p>This is a bounded topology query, not a path search or a Minecraft height lookup.
     * Pedestrian compilers use it to stand <em>on</em> an owned route deck instead of compiling
     * a terrain-level body through that deck after a work site or depot journey crosses a
     * three-wide route.</p>
     */
    public static java.util.Optional<BlockPosition> surfaceAt(FrontierBootstrap bootstrap, RouteTopology topology, int x, int z) {
        Objects.requireNonNull(bootstrap, "route bootstrap"); Objects.requireNonNull(topology, "route topology");
        int highest = Integer.MIN_VALUE;
        List<Settlement> settlements = bootstrap.settlements();
        for (int index = 0; index < settlements.size(); index++) {
            Settlement settlement = settlements.get(index); BlockPosition anchor = settlement.anchor();
            int laneZ = anchor.z() + 36, laneX = anchor.x() + 36;
            highest = Math.max(highest, surfaceYAtCarriagewayColumn(x, z, surveyedGridSurface(bootstrap, anchor.x(), laneZ), surveyedGridSurface(bootstrap, laneX, laneZ)));
            highest = Math.max(highest, surfaceYAtCarriagewayColumn(x, z, surveyedGridSurface(bootstrap, laneX, anchor.z()), surveyedGridSurface(bootstrap, laneX, laneZ)));
            if (index % 4 != 3) highest = Math.max(highest, surfaceYAtCarriagewayColumn(x, z,
                    surveyedGridSurface(bootstrap, laneX, laneZ), surveyedGridSurface(bootstrap, settlements.get(index + 1).anchor().x() + 36, laneZ)));
            if (index < 8) highest = Math.max(highest, surfaceYAtCarriagewayColumn(x, z,
                    surveyedGridSurface(bootstrap, laneX, laneZ), surveyedGridSurface(bootstrap, laneX, settlements.get(index + 4).anchor().z() + 36)));
        }
        for (Settlement settlement : settlements) {
            List<BlockPosition> supply = topology.supplyWaypoints(bootstrap, settlement.id());
            for (int index = 1; index < supply.size(); index++) {
                highest = Math.max(highest, surfaceYAtCarriagewayColumn(x, z, supply.get(index - 1), supply.get(index)));
            }
        }
        return highest == Integer.MIN_VALUE ? java.util.Optional.empty() : java.util.Optional.of(new BlockPosition(x, highest, z));
    }

    /**
     * True when {@code position} is one of the immutable surveyed support cells below an
     * elevated route deck.  This is deliberately a bounded geometry predicate rather than a
     * call to {@link #footprint(FrontierBootstrap, RouteTopology)}: state-transition validation
     * asks about one retained loss on every canonical commit and must never compile the whole
     * world route projection merely to validate that one cell.
     */
    public static boolean isFoundationCell(FrontierBootstrap bootstrap, RouteTopology topology, BlockPosition position) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(topology, "route topology"); Objects.requireNonNull(position, "route position");
        int terrain = bootstrap.terrain().supportYAt(position.x(), position.z());
        if (position.y() <= terrain) return false;
        List<Settlement> settlements = bootstrap.settlements();
        for (int index = 0; index < settlements.size(); index++) {
            Settlement settlement = settlements.get(index); BlockPosition anchor = settlement.anchor();
            int laneZ = anchor.z() + 36, laneX = anchor.x() + 36;
            if (supportsFoundationAt(position, terrain, surveyedGridSurface(bootstrap, anchor.x(), laneZ), surveyedGridSurface(bootstrap, laneX, laneZ))
                    || supportsFoundationAt(position, terrain, surveyedGridSurface(bootstrap, laneX, anchor.z()), surveyedGridSurface(bootstrap, laneX, laneZ))
                    || index % 4 != 3 && supportsFoundationAt(position, terrain, surveyedGridSurface(bootstrap, laneX, laneZ),
                    surveyedGridSurface(bootstrap, settlements.get(index + 1).anchor().x() + 36, laneZ))
                    || index < 8 && supportsFoundationAt(position, terrain, surveyedGridSurface(bootstrap, laneX, laneZ),
                    surveyedGridSurface(bootstrap, laneX, settlements.get(index + 4).anchor().z() + 36))) return true;
        }
        for (Settlement settlement : settlements) {
            List<BlockPosition> supply = topology.supplyWaypoints(bootstrap, settlement.id());
            for (int index = 1; index < supply.size(); index++) {
                if (supportsFoundationAt(position, terrain, supply.get(index - 1), supply.get(index))) return true;
            }
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
     * existing edge IDs and never creates a detour. The same declared-grade compiler owns both
     * the centreline and its three-wide physical envelope, so a lateral support on a step maps
     * only to the persisted adjacent edges which that step supports.
     */
    public static Set<TraversalEdgeId> affectedTraversalEdges(TraversalTopology topology, BlockPosition physicalSurface) {
        Objects.requireNonNull(topology, "traversal topology"); Objects.requireNonNull(physicalSurface, "physical route surface");
        Set<TraversalEdgeId> affected = new LinkedHashSet<>();
        for (TraversalTopology.Edge edge : topology.edges()) {
            BlockPosition from = topology.nodes().get(edge.from()).support();
            BlockPosition to = topology.nodes().get(edge.to()).support();
            if (onCarriagewaySegment(physicalSurface, from, to) || onCarriagewayColumn(physicalSurface, from, to)) affected.add(edge.id());
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

    private static boolean supportsFoundationAt(BlockPosition position, int terrain, BlockPosition from, BlockPosition to) {
        int surfaceY = surfaceYAtCarriagewayColumn(position.x(), position.z(), from, to);
        return surfaceY != Integer.MIN_VALUE && position.y() < surfaceY && position.y() > terrain;
    }

    /** One bootstrap-owned surface datum for a fixed-grid junction; never a Minecraft height query. */
    private static BlockPosition surveyedGridSurface(FrontierBootstrap bootstrap, int x, int z) {
        return new BlockPosition(x, Math.addExact(bootstrap.terrain().supportYAt(x, z), 1), z);
    }

    /** Returns the deck elevation at one carriageway column, or a sentinel when it is outside. */
    private static int surfaceYAtCarriagewayColumn(int x, int z, BlockPosition from, BlockPosition to) {
        if (from.x() == to.x()) {
            if (Math.abs(x - from.x()) > 1) return Integer.MIN_VALUE;
            int horizontal = horizontalLength(from, to);
            int step = (z - from.z()) * Integer.compare(to.z(), from.z());
            return step < 0 || step > horizontal ? Integer.MIN_VALUE : heightAt(from, to, step, horizontal);
        }
        if (from.z() == to.z()) {
            if (Math.abs(z - from.z()) > 1) return Integer.MIN_VALUE;
            int horizontal = horizontalLength(from, to);
            int step = (x - from.x()) * Integer.compare(to.x(), from.x());
            return step < 0 || step > horizontal ? Integer.MIN_VALUE : heightAt(from, to, step, horizontal);
        }
        throw new IllegalArgumentException("route segment must be axis aligned");
    }

    /** A confirmed foundation loss is support evidence for the same elevated carriageway, not a second path. */
    private static boolean onCarriagewayColumn(BlockPosition position, BlockPosition from, BlockPosition to) {
        return onCarriagewaySegment(new BlockPosition(position.x(), from.y(), position.z()), from, to)
                || onCarriagewaySegment(new BlockPosition(position.x(), to.y(), position.z()), from, to);
    }

    private static void addSegment(Set<BlockPosition> cells, BlockPosition from, BlockPosition to) {
        forEachSegmentCell(from, to, cells::add);
    }

    private static boolean onSegment(BlockPosition position, BlockPosition from, BlockPosition to) {
        int horizontal = horizontalLength(from, to);
        if (from.x() == to.x() && position.x() == from.x()) {
            int step = (position.z() - from.z()) * Integer.compare(to.z(), from.z());
            return step >= 0 && step <= horizontal && position.y() == heightAt(from, to, step, horizontal);
        }
        if (from.z() == to.z() && position.z() == from.z()) {
            int step = (position.x() - from.x()) * Integer.compare(to.x(), from.x());
            return step >= 0 && step <= horizontal && position.y() == heightAt(from, to, step, horizontal);
        }
        return false;
    }
    private static void addSegment(java.util.List<BlockPosition> cells, BlockPosition from, BlockPosition to) {
        forEachSegmentCell(from, to, cells::add);
    }

    /**
     * Declared terrain geometry only: one horizontal cell per node and at most one vertical
     * datum change on that edge.  It deliberately has no Minecraft/height-map input.
     */
    private static boolean isDeclaredGrade(BlockPosition from, BlockPosition to) {
        int horizontal = horizontalLength(from, to);
        return horizontal > 0 && Math.abs(to.y() - from.y()) <= horizontal;
    }

    private static int horizontalLength(BlockPosition from, BlockPosition to) {
        int deltaX = Math.abs(to.x() - from.x()), deltaZ = Math.abs(to.z() - from.z());
        if (deltaX != 0 && deltaZ != 0) throw new IllegalArgumentException("route segment must be axis aligned");
        return deltaX + deltaZ;
    }

    private static int heightAt(BlockPosition from, BlockPosition to, int step, int horizontal) {
        int deltaY = to.y() - from.y();
        return from.y() + Integer.signum(deltaY) * (step * Math.abs(deltaY) / horizontal);
    }

    private static void forEachSegmentCell(BlockPosition from, BlockPosition to, java.util.function.Consumer<BlockPosition> consumer) {
        Objects.requireNonNull(from, "route segment from"); Objects.requireNonNull(to, "route segment to");
        int horizontal = horizontalLength(from, to);
        if (!isDeclaredGrade(from, to)) throw new IllegalArgumentException("route segment must be orthogonal with a declared grade of at most one per cell");
        int stepX = Integer.compare(to.x(), from.x()), stepZ = Integer.compare(to.z(), from.z());
        for (int step = 0; step <= horizontal; step++) {
            consumer.accept(new BlockPosition(from.x() + step * stepX, heightAt(from, to, step, horizontal), from.z() + step * stepZ));
        }
    }
}
