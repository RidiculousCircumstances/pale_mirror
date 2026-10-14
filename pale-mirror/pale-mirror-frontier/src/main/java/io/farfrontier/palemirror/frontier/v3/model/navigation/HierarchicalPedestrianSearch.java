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
    private PedestrianRouteGeometry geometry;
    private final PedestrianRegionCache regionCache;
    private final SurfaceAnchor start, target;
    private final Map<PedestrianRegion.Tile, PedestrianRegion> regions = new LinkedHashMap<>();
    private final Map<PedestrianRegion.Tile, Object> dependencies = new LinkedHashMap<>();
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
    private Candidate expanding;
    private final Map<Node, Edge> expansionExits = new HashMap<>();
    private int expansionNode, expansionEdge, expansionDirection = -1, expansionOffset, expansionExit, expansionEntrance;
    private PedestrianRegion.Refinement segmentRefinement;
    private long turnLimit;
    private int retainedSurfaces;
    private List<Map.Entry<Node, Edge>> publishingExits;
    private int publication;
    private java.util.function.BooleanSupplier mayContinue = () -> true;
    private PedestrianLocalSearch localSearch;
    private boolean destinationChecked;
    private int destinationNode, destinationEdge, destinationDirection = -1, destinationOffset, destinationExit, destinationEntrance;

    public HierarchicalPedestrianSearch(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        this(geometry, start, target, new PedestrianRegionCache());
    }

    HierarchicalPedestrianSearch(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                 PedestrianRegionCache regionCache) {
        this.geometry = Objects.requireNonNull(geometry); this.start = Objects.requireNonNull(start); this.target = Objects.requireNonNull(target);
        this.regionCache = Objects.requireNonNull(regionCache);
        if (geometry.bounds().contains(start.support()) && geometry.bounds().contains(target.support())
                && PedestrianLocalSearch.nearby(start, target)) localSearch = new PedestrianLocalSearch(geometry, start, target,
                surface -> {
                    var tile = PedestrianRegion.Tile.at(surface);
                    dependencies.put(tile, this.geometry.regionalRevision().map(revision -> revision.at(tile.x(), tile.z()))
                            .orElse(this.geometry.version()));
                });
    }

    public synchronized long workUnits() { return work; }
    synchronized PedestrianRegionDependencies dependencies() {
        return new PedestrianRegionDependencies(geometry.bounds(), geometry.regionalRevision()
                .map(PedestrianGeometryRevision::scope).orElse(geometry), dependencies);
    }

    /** Rebase only untouched surveyed dependencies. Future expansion consumes the new immutable view. */
    synchronized boolean rebase(PedestrianRouteGeometry current) {
        if (!compatible(current)) return false;
        geometry = current;
        if (localSearch != null) localSearch.rebase(current);
        return true;
    }
    synchronized boolean compatible(PedestrianRouteGeometry current) {
        var oldRevision = geometry.regionalRevision(); var nextRevision = current.regionalRevision();
        return geometry.bounds().equals(current.bounds()) && oldRevision.isPresent() && nextRevision.isPresent()
                && oldRevision.orElseThrow().scope().equals(nextRevision.orElseThrow().scope())
                && dependencies.entrySet().stream().allMatch(entry -> Objects.equals(entry.getValue(),
                        nextRevision.orElseThrow().at(entry.getKey().x(), entry.getKey().z())));
    }

    public synchronized PedestrianRouteResult advance(int budget) {
        return advance(budget, () -> true);
    }
    /** The host may yield calculation, never change route semantics or canonical time. */
    public synchronized PedestrianRouteResult advance(int budget, java.util.function.BooleanSupplier mayContinue) {
        this.mayContinue = Objects.requireNonNull(mayContinue);
        if (budget < MIN_SLICE_WORK) throw new IllegalArgumentException("navigation slice cannot fit one bounded regional expansion");
        if (terminal != null) return terminal;
        long limit = work + budget;
        turnLimit = limit;
        if (localSearch != null) {
            work += localSearch.advance(budget, mayContinue);
            if (!localSearch.finished()) return result(PedestrianRouteResult.Status.PLANNING, "LOCAL_SEARCH_IN_PROGRESS");
            if (localSearch.result() != null) {
                route.addAll(localSearch.result()); localSearch = null;
                return finish(PedestrianRouteResult.Status.FOUND, "EXACT_LOCAL_ROUTE");
            }
            localSearch = null; // No positive proof: retain full regional completeness.
            if (work >= limit || !mayContinue.getAsBoolean()) return result(PedestrianRouteResult.Status.PLANNING, "REGIONAL_SEARCH_IN_PROGRESS");
        }
        if (!initialized) {
            if (!geometry.bounds().contains(start.support()) || !geometry.bounds().contains(target.support()))
                return finish(PedestrianRouteResult.Status.NO_PATH, "ENDPOINT_OUTSIDE_KNOWN_WORLD");
            var first = region(PedestrianRegion.Tile.at(start));
            if (terminal != null) return terminal;
            if (first == null) return result(PedestrianRouteResult.Status.PLANNING, "REGION_BUILD_IN_PROGRESS");
            var last = region(PedestrianRegion.Tile.at(target));
            if (terminal != null) return terminal;
            if (last == null) return result(PedestrianRouteResult.Status.PLANNING, "REGION_BUILD_IN_PROGRESS");
            int sourceComponent = first.component(start), targetComponent = last.component(target);
            if (sourceComponent < 0 || targetComponent < 0) {
                if (geometry.supportExcluded(start) || geometry.supportExcluded(target))
                    return finish(PedestrianRouteResult.Status.NO_PATH, "ENDPOINT_KNOWN_REMOVED_SUPPORT");
                if (!geometry.knowsSupport(start) || !geometry.knowsSupport(target))
                    return finish(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, "ENDPOINT_LEVEL_NOT_KNOWN");
                if (geometry.blocked(start) || geometry.blocked(target))
                    return finish(PedestrianRouteResult.Status.NO_PATH, "ENDPOINT_KNOWN_BLOCKED_SUPPORT");
                return unavailable("ENDPOINT_NOT_ON_KNOWN_CLEAR_SUPPORT");
            }
            Node source = new Node(first.tile, sourceComponent); destination = new Node(last.tile, targetComponent);
            distance.put(source, 0); queue.add(candidate(source, 0)); initialized = true;
        }
        // Prove a fully known isolated goal from its incoming edges, instead of
        // flooding the entire reachable world from the source to rediscover it.
        while (!destinationChecked && work < limit && mayContinue.getAsBoolean()) {
            int reachable = probeDestination();
            if (reachable > 0) destinationChecked = true;
            if (reachable < 0) return finish(PedestrianRouteResult.Status.NO_PATH, "KNOWN_ISOLATED_TARGET_COMPONENT");
        }
        if (!destinationChecked) return result(PedestrianRouteResult.Status.PLANNING, "TARGET_CONNECTIVITY_IN_PROGRESS");
        while (work < limit && mayContinue.getAsBoolean() && itinerary == null && rewind == null && (expanding != null || !queue.isEmpty())) {
            if (expanding == null) {
                Candidate candidate = queue.remove(); work++;
                if (candidate.distance() != distance.get(candidate.node())) continue;
                if (candidate.node().equals(destination)) { beginRefinement(candidate.node()); break; }
                if (regions.size() + 4 > MAX_REGIONS || distance.size() > MAX_NODES)
                    return finish(PedestrianRouteResult.Status.SAFETY_LIMIT, "REGIONAL_SEARCH_SAFETY_LIMIT");
                expanding = candidate; expansionNode = expansionEdge = expansionOffset = expansionExit = expansionEntrance = 0;
                expansionDirection = -1; expansionExits.clear(); publishingExits = null; publication = 0;
            }
            // Selecting a candidate is itself charged work. Retain that candidate
            // when it consumes the last unit; expansion belongs to the next slice.
            if (work >= limit) break;
            expandStep();
            if (terminal != null) return terminal;
        }
        if (itinerary == null && rewind == null && expanding == null && queue.isEmpty()) return unavailable("NO_CONNECTED_KNOWN_REGION_PATH");
        while (rewind != null && work < limit && mayContinue.getAsBoolean()) {
            work++;
            Edge edge = previous.get(rewind);
            if (edge == null) { itinerary = reverseItinerary; rewind = null; refinementStart = start; }
            else { reverseItinerary.add(edge); rewind = edge.previous(); }
        }
        while (itinerary != null && work < limit && mayContinue.getAsBoolean()) {
            SurfaceAnchor end = refinement < itinerary.size() ? forwardEdge(refinement).exit() : target;
            if (segmentRefinement == null) segmentRefinement = regions.get(PedestrianRegion.Tile.at(refinementStart)).refine(refinementStart, end);
            // A local refinement is itself resumable; keep each admitted piece small.
            work += segmentRefinement.advance(Math.min(256, Math.toIntExact(limit - work)));
            var segment = segmentRefinement.result();
            if (segment == null) break;
            segmentRefinement = null;
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
        if (work >= turnLimit) return null;
        var lookup = regionCache.region(geometry, tile, Math.toIntExact(turnLimit - work), mayContinue);
        PedestrianRegion built = lookup.region();
        dependencies.put(tile, geometry.regionalRevision().map(revision -> revision.at(tile.x(), tile.z())).orElse(geometry.version()));
        work += lookup.work();
        if (lookup.limited()) { finish(PedestrianRouteResult.Status.SAFETY_LIMIT, "LAYERED_REGION_SAFETY_LIMIT"); return null; }
        if (built != null) {
            if (retainedSurfaces + built.size() > MAX_REGIONS * PedestrianRegion.CELLS) {
                finish(PedestrianRouteResult.Status.SAFETY_LIMIT, "LAYERED_SEARCH_RETENTION_LIMIT"); return null;
            }
            retainedSurfaces += built.size(); unknown |= built.unknown(); regions.put(tile, built);
        }
        return built;
    }
    /** 1 means possible incoming access, -1 proved isolation, 0 cooperative continuation. */
    private int probeDestination() {
        var local = regions.get(destination.tile());
        if (local.unknown() || queue.peek().node().equals(destination)) { work++; return 1; }
        if (destinationDirection < 0) {
            work++;
            if (destinationNode == local.size()) { destinationDirection = 0; return 0; }
            if (local.component(local.surface(destinationNode)) == destination.component()
                    || destinationEdge == local.outgoing(destinationNode).size()) {
                destinationNode++; destinationEdge = 0; return 0;
            }
            int next = local.outgoing(destinationNode).get(destinationEdge++);
            return local.component(local.surface(next)) == destination.component() ? 1 : 0;
        }
        if (destinationDirection == 4) { work++; return -1; }
        int tileX = local.tile.x() + (destinationDirection == 0 ? 1 : destinationDirection == 2 ? -1 : 0);
        int tileZ = local.tile.z() + (destinationDirection == 3 ? 1 : destinationDirection == 1 ? -1 : 0);
        int minX = Math.multiplyExact(tileX, PedestrianRegion.SIDE), minZ = Math.multiplyExact(tileZ, PedestrianRegion.SIDE);
        var bounds = geometry.bounds();
        if (minX >= bounds.maxXExclusive() || minX + PedestrianRegion.SIDE <= bounds.minX()
                || minZ >= bounds.maxZExclusive() || minZ + PedestrianRegion.SIDE <= bounds.minZ()) {
            work++; nextDestinationDirection(); return 0;
        }
        var neighbour = region(new PedestrianRegion.Tile(tileX, tileZ));
        if (neighbour == null || work >= turnLimit) return 0;
        work++;
        if (neighbour.unknown()) return 1; // Missing knowledge cannot prove isolation.
        if (destinationOffset == PedestrianRegion.SIDE) { nextDestinationDirection(); return 0; }
        int direction = destinationDirection, offset = destinationOffset;
        int x = direction == 0 ? PedestrianRegion.SIDE - 1 : direction == 2 ? 0 : offset;
        int z = direction == 3 ? PedestrianRegion.SIDE - 1 : direction == 1 ? 0 : offset;
        int otherX = direction == 0 ? 0 : direction == 2 ? PedestrianRegion.SIDE - 1 : x;
        int otherZ = direction == 3 ? 0 : direction == 1 ? PedestrianRegion.SIDE - 1 : z;
        var exits = local.surfaces(x, z); var entrances = neighbour.surfaces(otherX, otherZ);
        if (destinationExit == exits.size()) { destinationOffset++; destinationExit = destinationEntrance = 0; return 0; }
        var exit = exits.get(destinationExit);
        if (local.component(exit) != destination.component() || destinationEntrance == entrances.size()) {
            destinationExit++; destinationEntrance = 0; return 0;
        }
        return geometry.canTraverse(entrances.get(destinationEntrance++), exit) ? 1 : 0;
    }
    private void nextDestinationDirection() {
        destinationDirection++; destinationOffset = destinationExit = destinationEntrance = 0;
    }
    private void expandStep() {
        var candidate = expanding;
        var local = regions.get(candidate.node().tile());
        if (expansionDirection < 0) {
            work++;
            if (expansionNode == local.size()) { expansionDirection = 0; return; }
            var outgoing = local.outgoing(expansionNode);
            if (local.component(local.surface(expansionNode)) != candidate.node().component() || expansionEdge == outgoing.size()) {
                expansionNode++; expansionEdge = 0; return;
            }
            var exit = local.surface(expansionNode); var entrance = local.surface(outgoing.get(expansionEdge++));
            if (local.component(entrance) != candidate.node().component()) recordPortal(candidate, local, exit, entrance);
            return;
        }
        var bounds = geometry.bounds();
        if (expansionDirection < 4) {
            int direction = expansionDirection;
            int tileX = local.tile.x() + (direction == 0 ? 1 : direction == 2 ? -1 : 0);
            int tileZ = local.tile.z() + (direction == 3 ? 1 : direction == 1 ? -1 : 0);
            int minX = Math.multiplyExact(tileX, PedestrianRegion.SIDE), minZ = Math.multiplyExact(tileZ, PedestrianRegion.SIDE);
            if (minX >= bounds.maxXExclusive() || minX + PedestrianRegion.SIDE <= bounds.minX()
                    || minZ >= bounds.maxZExclusive() || minZ + PedestrianRegion.SIDE <= bounds.minZ()) {
                work++; nextDirection(); return;
            }
            var neighbour = region(new PedestrianRegion.Tile(tileX, tileZ));
            if (neighbour == null || work >= turnLimit) return;
            work++;
            if (expansionOffset == PedestrianRegion.SIDE) { nextDirection(); return; }
            int offset = expansionOffset;
            int x = direction == 0 ? PedestrianRegion.SIDE - 1 : direction == 2 ? 0 : offset;
            int z = direction == 3 ? PedestrianRegion.SIDE - 1 : direction == 1 ? 0 : offset;
            int otherX = direction == 0 ? 0 : direction == 2 ? PedestrianRegion.SIDE - 1 : x;
            int otherZ = direction == 3 ? 0 : direction == 1 ? PedestrianRegion.SIDE - 1 : z;
            var exits = local.surfaces(x, z); var entrances = neighbour.surfaces(otherX, otherZ);
            if (expansionExit == exits.size()) { expansionOffset++; expansionExit = expansionEntrance = 0; return; }
            var exit = exits.get(expansionExit);
            if (local.component(exit) != candidate.node().component() || expansionEntrance == entrances.size()) {
                expansionExit++; expansionEntrance = 0; return;
            }
            var entrance = entrances.get(expansionEntrance++);
            if (geometry.canTraverse(exit, entrance)) recordPortal(candidate, neighbour, exit, entrance);
            return;
        }
        // At most one exit per bounded component. Stable ordering keeps map iteration irrelevant.
        if (publishingExits == null) publishingExits = expansionExits.entrySet().stream().sorted(Comparator
                    .comparing((Map.Entry<Node, Edge> value) -> value.getKey().tile())
                    .thenComparingInt(value -> value.getKey().component())).toList();
        work++;
        if (publication < publishingExits.size()) {
            var entry = publishingExits.get(publication++);
            Node node = entry.getKey(); int nextDistance = candidate.distance() + 1;
            if (distance.getOrDefault(node, Integer.MAX_VALUE) <= nextDistance) return;
            if (!distance.containsKey(node) && distance.size() >= MAX_NODES) {
                finish(PedestrianRouteResult.Status.SAFETY_LIMIT, "REGIONAL_NODE_SAFETY_LIMIT"); return;
            }
            distance.put(node, nextDistance); previous.put(node, entry.getValue()); queue.add(candidate(node, nextDistance));
            return;
        }
        expanding = null; expansionExits.clear(); publishingExits = null;
    }
    private void recordPortal(Candidate candidate, PedestrianRegion neighbour, SurfaceAnchor exit, SurfaceAnchor entrance) {
        Node node = new Node(neighbour.tile, neighbour.component(entrance));
        var edge = new Edge(candidate.node(), exit, entrance); Edge prior = expansionExits.get(node);
        if (prior == null || portalScore(edge) < portalScore(prior)) expansionExits.put(node, edge);
    }
    private void nextDirection() {
        expansionDirection++; expansionOffset = expansionExit = expansionEntrance = 0;
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
