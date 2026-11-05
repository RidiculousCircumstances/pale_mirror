package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet;

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
 * its two lateral cells are the physical envelope needed by public pedestrian movement.</p>
 */
public final class FrontierRouteNetwork {
    public static final SubjectId OWNER = new SubjectId("route:frontier-network");
    public static final SubjectId MAINTENANCE_CONTAINER = new SubjectId("container:frontier-route-maintenance");

    // One disposable immutable declaration view. Availability/physical damage is evaluated
    // by its owning layers, not compiled into the authored road footprint.
    private static FrontierBootstrap cachedBootstrap;
    private static Map<SubjectId, List<BlockPosition>> cachedRoutes;
    private static Set<BlockPosition> cachedSurfaces;
    private static RouteFootprint cachedFootprint;

    private FrontierRouteNetwork() { }

    private static final ImmutableResultView<SubjectId,List<BlockPosition>> ROUTES=new ImmutableResultView<>(SimulationCapacity.MAX_SETTLEMENTS*2);
    public static List<BlockPosition> settlementWaypoints(FrontierBootstrap bootstrap,SubjectId settlementId) {
        return ROUTES.get(List.of(bootstrap),settlementId,()->compileSettlementWaypoints(bootstrap,settlementId));
    }
    private static List<BlockPosition> compileSettlementWaypoints(FrontierBootstrap bootstrap, SubjectId settlementId) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(settlementId, "settlement id");
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown route settlement: " + settlementId.value()));
        SettlementAccessPort access = SettlementAccessPort.forHall(settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("settlement lacks its Hall access port")));
        BlockPosition origin = access.routeSurface().support();
        var front=settlement.layout().extensionFronts().stream().min(java.util.Comparator.comparingInt(p->
                Math.abs(p.x()-origin.x())+Math.abs(p.z()-origin.z()))).orElseThrow();
        var local=SettlementLocalCirculation.path(settlement,new SurfaceAnchor(origin),front);
        var waypoints=new ArrayList<BlockPosition>();waypoints.add(origin);
        for(int i=1;i<local.size()-1;i++) {
            var a=local.get(i-1);var b=local.get(i);var c=local.get(i+1);
            if(b.x()-a.x()!=c.x()-b.x() || b.z()-a.z()!=c.z()-b.z() || b.y()-a.y()!=c.y()-b.y())waypoints.add(b.support());
        }
        if(!waypoints.getLast().equals(front.support()))waypoints.add(front.support());
        if(waypoints.size()==2)waypoints.add(1,local.get(local.size()/2).support());
        var lane=front.support();
        if(bootstrap.ruleset().worldLayout().roads()!=FrontierWorldLayout.Roads.LOCAL_ACCESS) {
            var publicJunction=surveyedGridSurface(bootstrap,junctionX(bootstrap),bootstrap.bounds().minZ()+512);
            var reserved=bootstrap.settlements().stream().flatMap(home->home.layout().plots().values().stream()).map(SettlementLayout.Plot::bounds).toList();
            var connection=PlanCorridorSearch.connect(lane,publicJunction,bootstrap.bounds(),bootstrap.terrain(),reserved,1);
            waypoints.addAll(connection.subList(1,connection.size()));
        }
        return List.copyOf(waypoints);
    }

    public static void validateSettlementWaypoints(FrontierBootstrap bootstrap, SubjectId settlementId, List<BlockPosition> route) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(settlementId, "settlement id"); Objects.requireNonNull(route, "route");
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown route settlement: " + settlementId.value()));
        SettlementAccessPort access = SettlementAccessPort.forHall(settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("settlement lacks its Hall access port")));
        if (route.size() < RouteTopology.MIN_WAYPOINTS || route.size() > RouteTopology.MAX_WAYPOINTS || !route.getFirst().equals(access.routeSurface().support())
                || !route.getLast().equals(settlementWaypoints(bootstrap, settlementId).getLast())) throw new IllegalArgumentException("replacement route has invalid endpoints or size");
        for (int index = 1; index < route.size(); index++) {
            BlockPosition from = route.get(index - 1), to = route.get(index);
            if (!bootstrap.bounds().contains(from) || !bootstrap.bounds().contains(to) || !isDeclaredGrade(from, to)) {
                throw new IllegalArgumentException("replacement route segment is outside bounds, diagonal or too steep");
            }
        }
    }


    /**
     * The route's exact maintenance stock has a real, separately claimed surface. A player or a
     * settlement can fund it through normal item custody, but gray concrete elsewhere never
     * becomes route authority.
     */
    public static BlockPosition maintenanceContainerPosition(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        int x = junctionX(bootstrap), z = Math.addExact(bootstrap.bounds().minZ(), 762);
        return new BlockPosition(x, Math.addExact(bootstrap.terrain().supportYAt(x, z), 2), z);
    }

    /**
     * The content policy declares the shared junction inset from the western bound.
     * Keep that topology-relative datum here instead of smuggling an absolute overworld
     * coordinate into every reconstructed bootstrap: recovery and isolated physical fixtures
     * may legitimately preserve the same immutable profile at a translated world origin.
     */
    private static int junctionX(FrontierBootstrap bootstrap) {
        return Math.addExact(bootstrap.bounds().minX(), SettlementLayoutRules.standard().globalRoadInset());
    }

    public static Set<BlockPosition> surfaceCells(FrontierBootstrap bootstrap) {
        return surfaceCells(bootstrap, RouteTopology.initial());
    }

    public static synchronized Set<BlockPosition> surfaceCells(FrontierBootstrap bootstrap, RouteTopology topology) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(topology, "route topology");
        var routes = topology.replacementRoutes();
        if (cachedBootstrap != bootstrap || !routes.equals(cachedRoutes)) {
            var compiled = compileSurfaceCells(bootstrap, topology);
            cachedBootstrap = bootstrap; cachedRoutes = routes; cachedSurfaces = compiled; cachedFootprint = null;
        }
        return cachedSurfaces;
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
    static synchronized RouteFootprint footprint(FrontierBootstrap bootstrap, RouteTopology topology) {
        Set<BlockPosition> surfaces = surfaceCells(bootstrap, topology);
        if (cachedFootprint != null) return cachedFootprint;
        Set<BlockPosition> foundations = new LinkedHashSet<>();
        for (BlockPosition surface : surfaces) {
            int terrain = bootstrap.terrain().supportYAt(surface.x(), surface.z());
            if (terrain > surface.y()) {
                throw new IllegalArgumentException("route surface is below its immutable terrain support at " + surface);
            }
            for (int y = terrain + 1; y < surface.y(); y++) {
                BlockPosition footing = new BlockPosition(surface.x(), y, surface.z());
                // A lower declared route deck can be the physical support at a compact graded
                // junction. It keeps its own surface provenance; do not fabricate a second
                // semantic owner for the same block.
                if (!surfaces.contains(footing)) foundations.add(footing);
            }
        }
        var footprint = new RouteFootprint(surfaces, foundations);
        cachedFootprint = footprint;
        return footprint;
    }

    private record RoadSegment(BlockPosition from, BlockPosition to) { }
    private record RoadSegments(List<RoadSegment> segments,
            Map<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region, List<RoadSegment>> regions) {
        List<RoadSegment> near(int x, int z) {
            return regions.getOrDefault(new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region(
                    Math.floorDiv(x, 16), Math.floorDiv(z, 16)), List.of());
        }
    }
    private static final ImmutableResultView<String, RoadSegments> SEGMENTS = new ImmutableResultView<>(4);
    private static RoadSegments roadSegments(FrontierBootstrap bootstrap, RouteTopology topology) {
        return SEGMENTS.get(List.of(bootstrap, topology.replacementRoutes()), "declared-road-segments", () -> {
            var segments = new ArrayList<RoadSegment>();
            for (var settlement : bootstrap.settlements()) {
                var route = topology.settlementWaypoints(bootstrap, settlement.id());
                for (int i = 1; i < route.size(); i++) segments.add(new RoadSegment(route.get(i - 1), route.get(i)));
            }
            // Global corridors already join their declared common junction; local-access
            // profiles deliberately have no extra centre-offset grid through occupied plots.
            var regions = new java.util.HashMap<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region, List<RoadSegment>>();
            for (var segment : segments) {
                for (int x = Math.floorDiv(Math.min(segment.from().x(), segment.to().x()) - 1, 16);
                        x <= Math.floorDiv(Math.max(segment.from().x(), segment.to().x()) + 1, 16); x++)
                    for (int z = Math.floorDiv(Math.min(segment.from().z(), segment.to().z()) - 1, 16);
                            z <= Math.floorDiv(Math.max(segment.from().z(), segment.to().z()) + 1, 16); z++)
                        regions.computeIfAbsent(new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region(x, z),
                                ignored -> new ArrayList<>()).add(segment);
            }
            var frozen = new java.util.HashMap<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region, List<RoadSegment>>();
            regions.forEach((region, local) -> frozen.put(region, List.copyOf(local)));
            return new RoadSegments(List.copyOf(segments), Map.copyOf(frozen));
        });
    }

    private static Set<BlockPosition> compileSurfaceCells(FrontierBootstrap bootstrap, RouteTopology topology) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(topology, "route topology");
        Set<BlockPosition> cells = new LinkedHashSet<>();
        for (var segment : roadSegments(bootstrap, topology).segments())
            addCarriagewaySegment(cells, segment.from(), segment.to());
        // Long parallel roads cluster coordinate hashes in a flat Set.copyOf table.
        // Keep the immutable footprint in the shared spatial representation instead.
        cells.removeIf(p->SettlementLandIndex.ownsFinish(bootstrap,p));
        return RegionalBlockSet.copyOf(cells);
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
            surfaceCells = RegionalBlockSet.copyOf(surfaceCells); foundationCells = RegionalBlockSet.copyOf(foundationCells);
        }
    }

    /** True when a canonical floor cell is occupied by the one-block visible route surface. */
    public static boolean isSurfaceCell(FrontierBootstrap bootstrap, RouteTopology topology, BlockPosition position) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(topology, "route topology"); Objects.requireNonNull(position, "route position");
        if(SettlementLandIndex.ownsFinish(bootstrap,position))return false;
        for (var segment : roadSegments(bootstrap, topology).near(position.x(), position.z()))
            if (onCarriagewaySegment(position, segment.from(), segment.to())) return true;
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
        for (var segment : roadSegments(bootstrap, topology).near(x, z))
            highest = Math.max(highest, surfaceYAtCarriagewayColumn(x, z, segment.from(), segment.to()));
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
        int terrain=bootstrap.terrain().supportYAt(position.x(),position.z());
        if(position.y()<=terrain)return false;
        for(var segment:roadSegments(bootstrap,topology).near(position.x(),position.z()))
            if(supportsFoundationAt(position,terrain,segment.from(),segment.to()))return true;
        return false;
    }

    /** Cells that a replacement must physically create before the topology may become active. */
    public static List<BlockPosition> constructionCells(FrontierBootstrap bootstrap, RouteTopology active, SubjectId settlementId,
                                                 List<BlockPosition> replacement) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(active, "active topology");
        validateSettlementWaypoints(bootstrap, settlementId, replacement);
        // Only this settlement route changes. Building both full-world surface sets made a local
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
        return new BlockPosition(x, bootstrap.terrain().supportYAt(x, z), z);
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
