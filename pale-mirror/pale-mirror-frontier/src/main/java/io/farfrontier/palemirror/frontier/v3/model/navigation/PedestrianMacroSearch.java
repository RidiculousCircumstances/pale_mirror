package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import static io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianItineraryCache.*;

/** Bounded upper-level A* over proven region chains. Missing edges fall back to ordinary regional exploration. */
final class PedestrianMacroSearch {
    private record Candidate(Node node, int cost, int estimate) { }
    private record Previous(Node from, Itinerary itinerary, Arc arc) { }
    private final List<Itinerary> graphs;
    private final PedestrianRegionCache cache;
    private final SurfaceAnchor start, target;
    private final BiConsumer<PedestrianRegion.Tile, Object> observed;
    private PedestrianRouteGeometry geometry;
    private PedestrianRegion first, last;
    private Node goal, rewind;
    private final Map<Node, Integer> distances = new HashMap<>();
    private final Map<Node, Previous> previous = new HashMap<>();
    private final PriorityQueue<Candidate> queue = new PriorityQueue<>(Comparator.comparingInt(Candidate::estimate)
            .thenComparingInt(value -> -value.cost()).thenComparing(Candidate::node));
    private final Map<Itinerary, Boolean> validity = new IdentityHashMap<>();
    private Candidate expanding;
    private Iterator<Map.Entry<PedestrianRegion.Tile, Object>> validation;
    private int graphIndex, arcIndex, part, portalIndex;
    private final List<Previous> reverse = new ArrayList<>();
    private final List<Portal> route = new ArrayList<>();
    private List<Portal> result;
    private boolean finished, constructing;

    PedestrianMacroSearch(List<Itinerary> graphs, PedestrianRouteGeometry geometry, PedestrianRegionCache cache,
                           SurfaceAnchor start, SurfaceAnchor target, BiConsumer<PedestrianRegion.Tile, Object> observed) {
        this.graphs = List.copyOf(graphs); this.geometry = geometry; this.cache = cache;
        this.start = start; this.target = target; this.observed = observed;
    }
    void rebase(PedestrianRouteGeometry geometry) { this.geometry = geometry; }
    int advance(int budget, BooleanSupplier mayContinue) {
        int work = 0;
        while (!finished && work < budget && mayContinue.getAsBoolean()) {
            if (first == null || last == null) {
                var tile = PedestrianRegion.Tile.at(first == null ? start : target);
                observed.accept(tile, revision(tile));
                var lookup = cache.region(geometry, tile, budget - work, mayContinue); work += lookup.work();
                if (lookup.limited()) { finished = true; continue; }
                if (lookup.region() == null) break;
                if (first == null) first = lookup.region(); else last = lookup.region();
                continue;
            }
            work++;
            if (goal == null) {
                if (first.component(start) < 0 || last.component(target) < 0) { finished = true; continue; }
                var source = new Node(first.tile, first.component(start)); goal = new Node(last.tile, last.component(target));
                distances.put(source, 0); queue.add(candidate(source, 0)); continue;
            }
            if (rewind != null) {
                var edge = previous.get(rewind);
                if (edge == null) { rewind = null; constructing = true; }
                else { reverse.add(edge); rewind = edge.from(); }
                continue;
            }
            if (constructing) {
                if (part == reverse.size()) { result = List.copyOf(route); finished = true; continue; }
                var edge = reverse.get(reverse.size() - 1 - part);
                if (portalIndex == edge.arc().cost()) { part++; portalIndex = 0; continue; }
                if (route.size() == HierarchicalPedestrianSearch.MAX_NODES) { finished = true; continue; }
                route.add(edge.itinerary().portals().get(edge.arc().fromIndex() + portalIndex++)); continue;
            }
            if (expanding == null) {
                if (queue.isEmpty() || distances.size() > HierarchicalPedestrianSearch.MAX_NODES) { finished = true; continue; }
                var candidate = queue.remove();
                if (distances.get(candidate.node()) != candidate.cost()) continue;
                if (candidate.node().equals(goal)) { rewind = candidate.node(); continue; }
                expanding = candidate; graphIndex = arcIndex = 0; continue;
            }
            if (graphIndex == graphs.size()) { expanding = null; continue; }
            var graph = graphs.get(graphIndex); var arcs = graph.graph().get(expanding.node());
            if (arcs == null || Boolean.FALSE.equals(validity.get(graph))) { graphIndex++; arcIndex = 0; continue; }
            if (!validity.containsKey(graph)) {
                if (validation == null) validation = graph.dependencies().entrySet().iterator();
                if (validation.hasNext()) {
                    var entry = validation.next(); Object current = revision(entry.getKey()); observed.accept(entry.getKey(), current);
                    if (!Objects.equals(entry.getValue(), current)) { validity.put(graph, false); validation = null; }
                } else { validity.put(graph, true); validation = null; }
                continue;
            }
            if (arcIndex == arcs.size()) { graphIndex++; arcIndex = 0; continue; }
            var arc = arcs.get(arcIndex++); int cost = expanding.cost() + arc.cost();
            if (cost < distances.getOrDefault(arc.to(), Integer.MAX_VALUE)) {
                if (queue.size() >= HierarchicalPedestrianSearch.MAX_NODES
                        || !distances.containsKey(arc.to()) && distances.size() >= HierarchicalPedestrianSearch.MAX_NODES) {
                    finished = true; continue; // Exhausted hint budget is not evidence of no path.
                }
                distances.put(arc.to(), cost); previous.put(arc.to(), new Previous(expanding.node(), graph, arc));
                queue.add(candidate(arc.to(), cost));
            }
        }
        return work;
    }
    private Candidate candidate(Node node, int cost) {
        return new Candidate(node, cost, cost + Math.abs(node.tile().x() - goal.tile().x()) + Math.abs(node.tile().z() - goal.tile().z()));
    }
    private Object revision(PedestrianRegion.Tile tile) { return geometry.regionalRevision().orElseThrow().at(tile.x(), tile.z()); }
    boolean finished() { return finished; }
    List<Portal> result() { return result; }
}
