package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Exact directed cell graph for reusable regional refinement; levels and components stay distinct. */
final class PedestrianRegionGraph implements IncrementalGoalField.Graph<SurfaceAnchor> {
    private final List<SurfaceAnchor> nodes;
    private final Map<SurfaceAnchor, List<SurfaceAnchor>> outgoing, incoming;
    PedestrianRegionGraph(PedestrianRegion region) {
        var points = new ArrayList<SurfaceAnchor>(region.size());
        for (int index = 0; index < region.size(); index++) points.add(region.surface(index));
        nodes = List.copyOf(points);
        var next = new HashMap<SurfaceAnchor, List<SurfaceAnchor>>();
        var previous = new HashMap<SurfaceAnchor, List<SurfaceAnchor>>();
        for (var node : nodes) previous.put(node, new ArrayList<>());
        for (int index = 0; index < region.size(); index++) {
            var adjacent = new ArrayList<SurfaceAnchor>(); var from = nodes.get(index);
            for (int edge : region.outgoing(index)) if (region.component(from) == region.component(nodes.get(edge))) {
                var to = nodes.get(edge); adjacent.add(to); previous.get(to).add(from);
            }
            next.put(from, List.copyOf(adjacent));
        }
        previous.replaceAll((node, values) -> List.copyOf(values));
        outgoing = Collections.unmodifiableMap(next); incoming = Collections.unmodifiableMap(previous);
    }
    public List<SurfaceAnchor> nodes() { return nodes; }
    public boolean contains(SurfaceAnchor node) { return outgoing.containsKey(node); }
    public List<SurfaceAnchor> successors(SurfaceAnchor node) { return outgoing.getOrDefault(node, List.of()); }
    public List<SurfaceAnchor> predecessors(SurfaceAnchor node) { return incoming.getOrDefault(node, List.of()); }
}
