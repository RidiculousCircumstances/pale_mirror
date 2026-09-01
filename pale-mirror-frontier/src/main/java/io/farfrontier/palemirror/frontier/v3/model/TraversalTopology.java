package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded immutable three-dimensional capability graph compiled by a terrain provider.
 *
 * <p>It deliberately owns only surveyed semantic surfaces and their declared connections.
 * Minecraft collision, an unloaded chunk and player changes may report evidence against an
 * edge, but may not make a navigator invent another edge, entrance or grade.</p>
 */
public record TraversalTopology(TraversalTopologyId id, long revision, SubjectId provenance,
                                Map<TraversalNodeId, SurfaceAnchor> nodes, List<Edge> edges) {
    public static final int MAX_NODES = 4_096;
    public static final int MAX_EDGES = 8_192;

    public TraversalTopology {
        id = Objects.requireNonNull(id, "traversal topology id");
        if (revision < 0L) throw new IllegalArgumentException("traversal topology revision must not be negative");
        provenance = Objects.requireNonNull(provenance, "traversal topology provenance");
        Map<TraversalNodeId, SurfaceAnchor> nodeCopy = new LinkedHashMap<>();
        Objects.requireNonNull(nodes, "traversal nodes").forEach((nodeId, surface) -> {
            if (nodeCopy.put(Objects.requireNonNull(nodeId, "traversal node id"), Objects.requireNonNull(surface, "traversal node surface")) != null) {
                throw new IllegalArgumentException("duplicate traversal node");
            }
        });
        if (nodeCopy.isEmpty() || nodeCopy.size() > MAX_NODES || new LinkedHashSet<>(nodeCopy.values()).size() != nodeCopy.size()) {
            throw new IllegalArgumentException("traversal topology nodes are invalid");
        }
        // Edge cursors and bounded physical scans consume this order.  Preserve the surveyed
        // compiler order rather than letting an immutable-map implementation reorder nodes.
        nodes = Collections.unmodifiableMap(nodeCopy);
        edges = List.copyOf(Objects.requireNonNull(edges, "traversal edges"));
        if ((edges.isEmpty() && nodes.size() != 1) || edges.size() > MAX_EDGES) throw new IllegalArgumentException("traversal topology edge count is invalid");
        Set<TraversalEdgeId> edgeIds = new LinkedHashSet<>();
        for (Edge edge : edges) {
            edge = Objects.requireNonNull(edge, "traversal edge");
            if (!edgeIds.add(edge.id()) || !nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to()) || edge.from().equals(edge.to())) {
                throw new IllegalArgumentException("traversal topology edge endpoint is invalid");
            }
            SurfaceAnchor from = nodes.get(edge.from()), to = nodes.get(edge.to());
            int horizontal = Math.abs(from.x() - to.x()) + Math.abs(from.z() - to.z());
            if (horizontal != 1 || edge.grade() != Math.abs(from.y() - to.y())) {
                throw new IllegalArgumentException("traversal topology edge does not match its surveyed grade");
            }
            TraversalCapability required = switch (edge.kind()) {
                case PEDESTRIAN -> TraversalCapability.PEDESTRIAN;
                case GROUND_BIOFORM -> TraversalCapability.GROUND_BIOFORM;
                case RAIL -> TraversalCapability.RAIL_VEHICLE;
            };
            if (!edge.capabilities().contains(required) || (edge.kind() == TraversalKind.RAIL
                    ? edge.capabilities().size() != 1 : edge.capabilities().contains(TraversalCapability.RAIL_VEHICLE))) {
                throw new IllegalArgumentException("traversal edge mixes rail and ground capability classes");
            }
        }
    }

    /** Compiles one ordered surveyed corridor without consulting a heightmap or loaded world. */
    public static TraversalTopology corridor(TraversalTopologyId id, long revision, SubjectId provenance,
                                             TraversalKind kind, Set<TraversalCapability> capabilities,
                                             List<SurfaceAnchor> surfaces) {
        Objects.requireNonNull(kind, "traversal kind");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "traversal capabilities"));
        surfaces = List.copyOf(Objects.requireNonNull(surfaces, "traversal surfaces"));
        if (surfaces.isEmpty() || surfaces.size() > MAX_NODES) throw new IllegalArgumentException("traversal corridor size is invalid");
        Map<TraversalNodeId, SurfaceAnchor> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        for (int index = 0; index < surfaces.size(); index++) {
            TraversalNodeId node = new TraversalNodeId("node:" + index); nodes.put(node, surfaces.get(index));
            if (index == 0) continue;
            SurfaceAnchor from = surfaces.get(index - 1), to = surfaces.get(index);
            edges.add(new Edge(new TraversalEdgeId("edge:" + (index - 1)), new TraversalNodeId("node:" + (index - 1)), node,
                    kind, capabilities, Math.abs(from.y() - to.y()), 2, revision, TraversalAvailability.OPEN));
        }
        return new TraversalTopology(id, revision, provenance, nodes, edges);
    }

    /** Compiles a surveyed public corridor that may be consumed in either declared direction. */
    public static TraversalTopology bidirectionalCorridor(TraversalTopologyId id, long revision, SubjectId provenance,
                                                          TraversalKind kind, Set<TraversalCapability> capabilities,
                                                          List<SurfaceAnchor> surfaces) {
        TraversalTopology forward = corridor(id, revision, provenance, kind, capabilities, surfaces);
        if (forward.edges().isEmpty()) return forward;
        List<Edge> edges = new ArrayList<>(forward.edges());
        for (int index = 0; index < forward.edges().size(); index++) {
            Edge edge = forward.edges().get(index);
            edges.add(new Edge(new TraversalEdgeId("reverse:" + index), edge.to(), edge.from(), edge.kind(),
                    edge.capabilities(), edge.grade(), edge.clearance(), edge.revision(), edge.availability()));
        }
        return new TraversalTopology(id, revision, provenance, forward.nodes(), edges);
    }

    /**
     * Returns the one ordered path only when this topology is a linear corridor.
     * Branching terrain graphs require a separately retained route and may not be flattened here.
     */
    public List<SurfaceAnchor> linearCorridorSurfaces() {
        if (edges.size() != nodes.size() - 1) throw new IllegalStateException("traversal topology is not a linear corridor");
        if (edges.isEmpty()) return List.of(nodes.values().iterator().next());
        List<SurfaceAnchor> result = new ArrayList<>();
        TraversalNodeId expected = edges.getFirst().from(); result.add(nodes.get(expected));
        for (Edge edge : edges) {
            if (!expected.equals(edge.from())) throw new IllegalStateException("traversal topology edge order is not one corridor");
            result.add(nodes.get(edge.to())); expected = edge.to();
        }
        if (new LinkedHashSet<>(result).size() != nodes.size()) throw new IllegalStateException("traversal topology corridor omits or repeats a node");
        return List.copyOf(result);
    }

    /** The retained edge immediately following one linear-corridor cursor. */
    public Edge edgeAfterCursor(int cursor) {
        List<SurfaceAnchor> corridor = linearCorridorSurfaces();
        if (cursor < 0 || cursor >= corridor.size() - 1) {
            throw new IllegalArgumentException("traversal cursor has no next edge");
        }
        return edges.get(cursor);
    }

    /** Returns the same surveyed graph with only explicitly named edge availability changed. */
    public TraversalTopology withAvailability(Set<TraversalEdgeId> affected, TraversalAvailability availability) {
        Set<TraversalEdgeId> exactAffected = Set.copyOf(Objects.requireNonNull(affected, "affected traversal edges"));
        TraversalAvailability exactAvailability = Objects.requireNonNull(availability, "traversal availability");
        if (exactAffected.isEmpty()) return this;
        if (!edges.stream().map(Edge::id).collect(java.util.stream.Collectors.toSet()).containsAll(exactAffected)) {
            throw new IllegalArgumentException("availability observation names a foreign traversal edge");
        }
        List<Edge> changed = edges.stream().map(edge -> exactAffected.contains(edge.id())
                ? new Edge(edge.id(), edge.from(), edge.to(), edge.kind(), edge.capabilities(), edge.grade(), edge.clearance(), edge.revision(), exactAvailability)
                : edge).toList();
        return new TraversalTopology(id, revision, provenance, nodes, changed);
    }

    /** Applies an exact persisted edge-status overlay to this same surveyed graph. */
    public TraversalTopology withExactAvailability(Map<TraversalEdgeId, TraversalAvailability> availability) {
        Map<TraversalEdgeId, TraversalAvailability> exactAvailability = Map.copyOf(Objects.requireNonNull(availability, "traversal availability overlay"));
        if (exactAvailability.isEmpty()) return this;
        Set<TraversalEdgeId> known = edges.stream().map(Edge::id).collect(java.util.stream.Collectors.toSet());
        if (!known.containsAll(exactAvailability.keySet())) throw new IllegalArgumentException("availability observation names a foreign traversal edge");
        List<Edge> changed = edges.stream().map(edge -> {
            TraversalAvailability next = exactAvailability.get(edge.id());
            return next == null ? edge : new Edge(edge.id(), edge.from(), edge.to(), edge.kind(), edge.capabilities(), edge.grade(), edge.clearance(), edge.revision(), next);
        }).toList();
        return new TraversalTopology(id, revision, provenance, nodes, changed);
    }

    /**
     * Extracts one retained directed corridor segment without creating geometry or a new edge
     * identity. A reverse journey mirrors the same physical edge IDs, so damage follows it in
     * either direction.
     */
    public TraversalTopology linearSegment(TraversalTopologyId segmentId, int fromCursor, int toCursor) {
        segmentId = Objects.requireNonNull(segmentId, "segment topology id");
        List<SurfaceAnchor> corridor = linearCorridorSurfaces();
        if (fromCursor < 0 || fromCursor >= corridor.size() || toCursor < 0 || toCursor >= corridor.size() || fromCursor == toCursor) {
            throw new IllegalArgumentException("traversal segment cursor is invalid");
        }
        int step = Integer.compare(toCursor, fromCursor);
        List<TraversalNodeId> corridorNodes = linearCorridorNodeIds();
        Map<TraversalNodeId, SurfaceAnchor> selectedNodes = new LinkedHashMap<>();
        List<Edge> selectedEdges = new ArrayList<>();
        for (int cursor = fromCursor;; cursor += step) {
            TraversalNodeId nodeId = corridorNodes.get(cursor);
            SurfaceAnchor surface = nodes.get(nodeId);
            if (surface == null) throw new IllegalStateException("linear corridor omits its canonical node");
            selectedNodes.put(nodeId, surface);
            if (cursor == toCursor) break;
            int edgeIndex = step > 0 ? cursor : cursor - 1;
            Edge original = edges.get(edgeIndex);
            if (step > 0) selectedEdges.add(original);
            else selectedEdges.add(new Edge(original.id(), original.to(), original.from(), original.kind(), original.capabilities(), original.grade(),
                    original.clearance(), original.revision(), original.availability()));
        }
        return new TraversalTopology(segmentId, revision, provenance, selectedNodes, selectedEdges);
    }

    private List<TraversalNodeId> linearCorridorNodeIds() {
        if (edges.isEmpty()) return List.of(nodes.keySet().iterator().next());
        List<TraversalNodeId> result = new ArrayList<>();
        TraversalNodeId expected = edges.getFirst().from(); result.add(expected);
        for (Edge edge : edges) {
            if (!expected.equals(edge.from())) throw new IllegalStateException("traversal topology edge order is not one corridor");
            result.add(edge.to()); expected = edge.to();
        }
        return List.copyOf(result);
    }

    public record Edge(TraversalEdgeId id, TraversalNodeId from, TraversalNodeId to, TraversalKind kind,
                       Set<TraversalCapability> capabilities, int grade, int clearance, long revision,
                       TraversalAvailability availability) {
        public Edge {
            id = Objects.requireNonNull(id, "traversal edge id"); from = Objects.requireNonNull(from, "traversal edge from"); to = Objects.requireNonNull(to, "traversal edge to");
            kind = Objects.requireNonNull(kind, "traversal edge kind"); capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "traversal edge capabilities"));
            availability = Objects.requireNonNull(availability, "traversal edge availability");
            if (capabilities.isEmpty() || grade < 0 || grade > 1 || clearance < 2 || revision < 0L) {
                throw new IllegalArgumentException("traversal edge metadata is invalid");
            }
        }

        public boolean traversableBy(TraversalCapability capability) {
            return availability == TraversalAvailability.OPEN && capabilities.contains(Objects.requireNonNull(capability, "traversal capability"));
        }
    }
}
