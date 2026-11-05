package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.*;

/** Shared immutable four-neighbour street graph; no movement or navigation authority. */
final class SettlementStreetTopology {
    // Values contain only projected nodes/edges, never the layout key. Retired plans
    // release their derived graph rather than retaining a fixed number of whole worlds.
    private static final Map<SettlementLayout, TraversalTopology> VIEWS = new WeakHashMap<>();

    static synchronized TraversalTopology of(SettlementLayout layout) {
        return VIEWS.computeIfAbsent(
                layout,
                key ->
                        compile(
                                key.settlementId(),
                                key.paving().stream()
                                        .map(SurfaceAnchor::new)
                                        .collect(java.util.stream.Collectors.toSet()),
                                "circulation"));
    }

    private static TraversalTopology compile(
            SubjectId owner, Set<SurfaceAnchor> supports, String purpose) {
        var nodes = new LinkedHashMap<TraversalNodeId, SurfaceAnchor>();
        var edges = new ArrayList<TraversalTopology.Edge>();
        var ordered =
                supports.stream()
                        .sorted(
                                Comparator.comparingInt(SurfaceAnchor::x)
                                        .thenComparingInt(SurfaceAnchor::z)
                                        .thenComparingInt(SurfaceAnchor::y))
                        .toList();
        long revision = Integer.toUnsignedLong(ordered.hashCode());
        var columns = new HashMap<TerrainColumn, SurfaceAnchor>();
        for (var p : ordered) {
            columns.put(new TerrainColumn(p.x(), p.z()), p);
            nodes.put(node(p), p);
        }
        for (var p : ordered)
            for (var d : FacilityFacing.values()) {
                var q = columns.get(new TerrainColumn(p.x() + d.x(), p.z() + d.z()));
                if (q == null || Math.abs(q.y() - p.y()) > 1) continue;
                var a = node(p);
                var b = node(q);
                edges.add(
                        new TraversalTopology.Edge(
                                new TraversalEdgeId("edge:" + a.value() + ">" + b.value()),
                                a,
                                b,
                                TraversalKind.PEDESTRIAN,
                                Set.of(
                                        TraversalCapability.PEDESTRIAN,
                                        TraversalCapability.GROUND_BIOFORM),
                                Math.abs(q.y() - p.y()),
                                2,
                                revision,
                                TraversalAvailability.OPEN));
            }
        return new TraversalTopology(
                new TraversalTopologyId(
                        "topology:" + purpose + "-" + owner.value().replace(':', '-')),
                revision,
                owner,
                nodes,
                edges);
    }

    private static TraversalNodeId node(SurfaceAnchor p) {
        return new TraversalNodeId("node:" + p.x() + ":" + p.y() + ":" + p.z());
    }

    private SettlementStreetTopology() {}
}
