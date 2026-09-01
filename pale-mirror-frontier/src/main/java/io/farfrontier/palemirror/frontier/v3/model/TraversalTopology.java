package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
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
        nodes = Map.copyOf(nodeCopy);
        edges = List.copyOf(Objects.requireNonNull(edges, "traversal edges"));
        if (edges.isEmpty() || edges.size() > MAX_EDGES) throw new IllegalArgumentException("traversal topology edge count is invalid");
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
        if (surfaces.size() < 2 || surfaces.size() > MAX_NODES) throw new IllegalArgumentException("traversal corridor size is invalid");
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
