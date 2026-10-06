package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** Resumable region/portal search. Each advance has a deterministic work bound, not a timer. */
public final class HierarchicalPedestrianSearch {
    public static final int MAX_REGIONS = 4_096;
    public static final int MAX_NODES = 16_384;
    public static final int MAX_ROUTE_SURFACES = 65_535;
    public static final int MIN_SLICE_WORK = PedestrianRegion.BUILD_WORK * 5 + 256;
    private record Node(PedestrianRegion.Tile tile, int component) { }
    private record Candidate(Node node, int distance, int estimate) { }
    private record Edge(Node previous, SurfaceAnchor exit, SurfaceAnchor entrance) { }
    private final PedestrianRouteGeometry geometry;
    private final PedestrianRegionCache regionCache;
    private final SurfaceAnchor start, target;
    private final Map<PedestrianRegion.Tile, PedestrianRegion> regions = new LinkedHashMap<>();
    private final Map<Node, Integer> distance = new HashMap<>();
    private final Map<Node, Edge> previous = new HashMap<>();
    private final PriorityQueue<Candidate> queue = new PriorityQueue<>(Comparator
            .comparingInt(Candidate::estimate).thenComparingInt(value -> -value.distance())
            .thenComparing(value -> value.node().tile()).thenComparingInt(value -> value.node().component()));
    private Node destination;
    private List<Edge> itinerary;
    private Node rewind;
    private final List<Edge> reverseItinerary = new ArrayList<>();
    private int refinement;
    private SurfaceAnchor refinementStart;
    private final List<SurfaceAnchor> route = new ArrayList<>();
    private long work;
    private boolean initialized, unknown;
    private PedestrianRouteResult terminal;

    public HierarchicalPedestrianSearch(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        this(geometry, start, target, new PedestrianRegionCache());
    }

    HierarchicalPedestrianSearch(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                 PedestrianRegionCache regionCache) {
        this.geometry = Objects.requireNonNull(geometry); this.start = Objects.requireNonNull(start); this.target = Objects.requireNonNull(target);
        this.regionCache = Objects.requireNonNull(regionCache);
    }

    public synchronized long workUnits() { return work; }

    public synchronized PedestrianRouteResult advance(int budget) {
        if (budget < MIN_SLICE_WORK) throw new IllegalArgumentException("navigation slice cannot fit one bounded regional expansion");
        if (terminal != null) return terminal;
        long limit = work + budget;
        if (!initialized) {
            if (!geometry.bounds().contains(start.support()) || !geometry.bounds().contains(target.support()))
                return finish(PedestrianRouteResult.Status.NO_PATH, "ENDPOINT_OUTSIDE_KNOWN_WORLD");
            var first = region(PedestrianRegion.Tile.at(start)); var last = region(PedestrianRegion.Tile.at(target));
            int sourceComponent = first.component(start), targetComponent = last.component(target);
            if (sourceComponent < 0 || targetComponent < 0) return unavailable("ENDPOINT_NOT_ON_KNOWN_CLEAR_SUPPORT");
            Node source = new Node(first.tile, sourceComponent); destination = new Node(last.tile, targetComponent);
            distance.put(source, 0); queue.add(candidate(source, 0)); initialized = true;
        }
        while (work + MIN_SLICE_WORK <= limit && itinerary == null && rewind == null && !queue.isEmpty()) {
            Candidate candidate = queue.remove(); work++;
            if (candidate.distance() != distance.get(candidate.node())) continue;
            if (candidate.node().equals(destination)) { beginRefinement(candidate.node()); break; }
            if (regions.size() + 4 > MAX_REGIONS || distance.size() > MAX_NODES)
                return finish(PedestrianRouteResult.Status.SAFETY_LIMIT, "REGIONAL_SEARCH_SAFETY_LIMIT");
            expand(candidate);
            if (terminal != null) return terminal;
        }
        if (itinerary == null && rewind == null && queue.isEmpty()) return unavailable("NO_CONNECTED_KNOWN_REGION_PATH");
        while (rewind != null && work < limit) {
            work++;
            Edge edge = previous.get(rewind);
            if (edge == null) { itinerary = reverseItinerary; rewind = null; refinementStart = start; }
            else { reverseItinerary.add(edge); rewind = edge.previous(); }
        }
        while (itinerary != null && work + PedestrianRegion.BUILD_WORK <= limit) {
            SurfaceAnchor end = refinement < itinerary.size() ? forwardEdge(refinement).exit() : target;
            var segment = region(PedestrianRegion.Tile.at(refinementStart)).path(refinementStart, end);
            work += PedestrianRegion.BUILD_WORK;
            if (route.size() + segment.size() > MAX_ROUTE_SURFACES)
                return finish(PedestrianRouteResult.Status.SAFETY_LIMIT, "ROUTE_OUTPUT_SAFETY_LIMIT");
            if (route.isEmpty()) route.addAll(segment); else route.addAll(segment.subList(1, segment.size()));
            if (refinement == itinerary.size()) return finish(PedestrianRouteResult.Status.FOUND, "CONNECTED_KNOWN_ROUTE");
            if (route.size() >= MAX_ROUTE_SURFACES) return finish(PedestrianRouteResult.Status.SAFETY_LIMIT, "ROUTE_OUTPUT_SAFETY_LIMIT");
            Edge edge = forwardEdge(refinement++); route.add(edge.entrance()); refinementStart = edge.entrance();
        }
        return result(PedestrianRouteResult.Status.PLANNING, "REGIONAL_SEARCH_IN_PROGRESS");
    }

    private PedestrianRegion region(PedestrianRegion.Tile tile) {
        PedestrianRegion prior = regions.get(tile);
        if (prior != null) return prior;
        PedestrianRegion built = regionCache.region(geometry, tile);
        work += PedestrianRegion.BUILD_WORK; unknown |= built.unknown(); regions.put(tile, built); return built;
    }
    private void expand(Candidate candidate) {
        var local = region(candidate.node().tile());
        var bounds = geometry.bounds();
        for (int direction = 0; direction < 4; direction++) {
            int tileX = local.tile.x() + (direction == 0 ? 1 : direction == 2 ? -1 : 0);
            int tileZ = local.tile.z() + (direction == 3 ? 1 : direction == 1 ? -1 : 0);
            int minX = Math.multiplyExact(tileX, PedestrianRegion.SIDE), minZ = Math.multiplyExact(tileZ, PedestrianRegion.SIDE);
            if (minX >= bounds.maxXExclusive() || minX + PedestrianRegion.SIDE <= bounds.minX()
                    || minZ >= bounds.maxZExclusive() || minZ + PedestrianRegion.SIDE <= bounds.minZ()) continue;
            var neighbour = region(new PedestrianRegion.Tile(tileX, tileZ));
            var exits = new HashMap<Node, Edge>();
            for (int offset = 0; offset < PedestrianRegion.SIDE; offset++) {
                work++;
                int x = direction == 0 ? PedestrianRegion.SIDE - 1 : direction == 2 ? 0 : offset;
                int z = direction == 3 ? PedestrianRegion.SIDE - 1 : direction == 1 ? 0 : offset;
                int otherX = direction == 0 ? 0 : direction == 2 ? PedestrianRegion.SIDE - 1 : x;
                int otherZ = direction == 3 ? 0 : direction == 1 ? PedestrianRegion.SIDE - 1 : z;
                SurfaceAnchor exit = local.surface(x, z), entrance = neighbour.surface(otherX, otherZ);
                if (local.componentAt(x, z) != candidate.node().component() || entrance == null || exit == null
                        || Math.abs(exit.y() - entrance.y()) > 1) continue;
                Node node = new Node(neighbour.tile, neighbour.componentAt(otherX, otherZ));
                var edge = new Edge(candidate.node(), exit, entrance);
                Edge prior = exits.get(node);
                if (prior == null || portalScore(edge) < portalScore(prior)) exits.put(node, edge);
            }
            for (var entry : exits.entrySet().stream().sorted(Comparator.comparingInt(value -> value.getKey().component())).toList()) {
                Node node = entry.getKey(); int nextDistance = candidate.distance() + 1;
                if (distance.getOrDefault(node, Integer.MAX_VALUE) <= nextDistance) continue;
                if (!distance.containsKey(node) && distance.size() >= MAX_NODES) {
                    finish(PedestrianRouteResult.Status.SAFETY_LIMIT, "REGIONAL_NODE_SAFETY_LIMIT"); return;
                }
                distance.put(node, nextDistance); previous.put(node, entry.getValue()); queue.add(candidate(node, nextDistance));
            }
        }
    }
    private long portalScore(Edge edge) {
        return Math.abs((long) edge.entrance().x() - target.x()) + Math.abs((long) edge.entrance().z() - target.z())
                + Math.abs((long) edge.exit().x() - start.x()) + Math.abs((long) edge.exit().z() - start.z());
    }
    private Candidate candidate(Node node, int distance) {
        var goal = PedestrianRegion.Tile.at(target);
        return new Candidate(node, distance, distance + Math.abs(node.tile().x() - goal.x()) + Math.abs(node.tile().z() - goal.z()));
    }
    private void beginRefinement(Node node) {
        rewind = node;
    }
    private Edge forwardEdge(int index) { return itinerary.get(itinerary.size() - 1 - index); }
    private PedestrianRouteResult unavailable(String reason) {
        return finish(unknown ? PedestrianRouteResult.Status.UNKNOWN_GEOMETRY : PedestrianRouteResult.Status.NO_PATH, reason);
    }
    private PedestrianRouteResult finish(PedestrianRouteResult.Status status, String reason) { terminal = result(status, reason); return terminal; }
    private PedestrianRouteResult result(PedestrianRouteResult.Status status, String reason) {
        return new PedestrianRouteResult(status, status == PedestrianRouteResult.Status.FOUND ? route : List.of(), work, regions.size(), reason);
    }
}
