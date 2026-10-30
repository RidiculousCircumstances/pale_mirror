package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.*;

/** Compact connected portal itineraries; distant cell paths and region graphs are not retained here. */
final class PedestrianItineraryCache {
    private static final int MAX_ENTRIES = 128, MAX_PORTALS = 16_384, MAX_DEPENDENCIES = 16_384;
    /** One additional abstraction level jumps at most sixteen proven regional transitions. */
    private static final int MACRO_SPAN = 16;
    record Node(PedestrianRegion.Tile tile, int component) implements Comparable<Node> {
        @Override public int compareTo(Node other) { int order = tile.compareTo(other.tile); return order != 0 ? order : Integer.compare(component, other.component); }
    }
    record Portal(SurfaceAnchor exit, SurfaceAnchor entrance, int fromComponent, int toComponent) {
        Node from() { return new Node(PedestrianRegion.Tile.at(exit), fromComponent); }
        Node to() { return new Node(PedestrianRegion.Tile.at(entrance), toComponent); }
    }
    record Arc(Node to, int fromIndex, int endIndex) { int cost() { return endIndex - fromIndex; } }
    record Itinerary(SurfaceAnchor target, List<Portal> portals, Map<PedestrianRegion.Tile, Object> dependencies,
                     Map<Node, List<Arc>> graph) { }
    private record Key(Object geometryScope, WorldBounds bounds, PedestrianRegion.Tile target) { }
    private final Map<Key, Itinerary> entries = new LinkedHashMap<>(16, 0.75f, true);
    private int portals, dependencies;
    private record Scope(Object geometryScope, WorldBounds bounds) { }
    private final Map<Scope, Snapshot> snapshots = new HashMap<>();
    /** Fixed hint membership shared by admitted searches; no geometry validity or route authority. */
    static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(List.of());
        private final List<Itinerary> graphs;
        // Bounded derived node membership, including absence. A first lookup
        // examines at most MAX_ENTRIES graphs; hits never scan unrelated hints.
        private final io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo<Node, List<Itinerary>> nodes =
                io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo.readMostly(1_024);
        private Snapshot(List<Itinerary> graphs) { this.graphs = List.copyOf(graphs); }
        boolean isEmpty() { return graphs.isEmpty(); }
        List<Itinerary> at(Node node) {
            return nodes.get(node, () -> graphs.stream().filter(graph -> graph.graph().containsKey(node)).toList());
        }
    }

    synchronized Snapshot snapshot(PedestrianRouteGeometry geometry) {
        var scope = geometry.regionalRevision().map(PedestrianGeometryRevision::geometryScope).orElse(null);
        if (scope == null) return Snapshot.EMPTY;
        var key = new Scope(scope, geometry.bounds());
        var retained = snapshots.get(key);
        if (retained != null) return retained;
        var graphs = entries.entrySet().stream().filter(entry -> entry.getKey().geometryScope().equals(scope)
                && entry.getKey().bounds().equals(geometry.bounds())).map(Map.Entry::getValue).toList();
        if (graphs.isEmpty()) return Snapshot.EMPTY;
        // Every retained image has a present entry; at most MAX_ENTRIES scopes.
        var captured = new Snapshot(graphs); snapshots.put(key, captured); return captured;
    }
    record Prepared(Key key, Itinerary value) { }
    static Optional<Prepared> prepare(PedestrianRouteGeometry geometry, SurfaceAnchor target, List<Portal> route,
                                      Map<PedestrianRegion.Tile, Object> proof) {
        var key = key(geometry, target);
        if (key == null || route.isEmpty() || route.size() > MAX_PORTALS || proof.size() > MAX_DEPENDENCIES) return Optional.empty();
        var graph = new HashMap<Node, List<Arc>>();
        for (int i = 0; i < route.size(); i++) {
            int end = Math.min(route.size(), i + MACRO_SPAN);
            var arcs = graph.computeIfAbsent(route.get(i).from(), ignored -> new ArrayList<>());
            if (end > i + 1) arcs.add(new Arc(route.get(end - 1).to(), i, end));
            arcs.add(new Arc(route.get(i).to(), i, i + 1));
        }
        graph.replaceAll((node, arcs) -> List.copyOf(arcs));
        // This private graph has no writer after publication. HashMap also avoids
        // MapN's linear-probe clustering for adjacent spatial node hashes.
        var value = new Itinerary(target, List.copyOf(route), Map.copyOf(proof), Collections.unmodifiableMap(graph));
        return Optional.of(new Prepared(key, value));
    }
    synchronized void publish(Prepared prepared) {
        var key = prepared.key(); var value = prepared.value();
        var old = entries.get(key);
        // Even an ignored shorter publication can touch this scope's access order.
        snapshots.remove(new Scope(key.geometryScope(), key.bounds()));
        // A continuation must not replace the useful full trunk with its suffix.
        if (old != null && old.dependencies().equals(value.dependencies()) && old.portals().size() >= value.portals().size()) return;
        if (old != null) { entries.remove(key); subtract(old); }
        while (!entries.isEmpty() && (entries.size() >= MAX_ENTRIES || portals + value.portals().size() > MAX_PORTALS
                || dependencies + value.dependencies().size() > MAX_DEPENDENCIES)) {
            var iterator = entries.entrySet().iterator(); var removed = iterator.next(); iterator.remove();
            snapshots.remove(new Scope(removed.getKey().geometryScope(), removed.getKey().bounds())); subtract(removed.getValue());
        }
        entries.put(key, value); portals += value.portals().size(); dependencies += value.dependencies().size();
    }
    private static Key key(PedestrianRouteGeometry geometry, SurfaceAnchor target) {
        return geometry.regionalRevision().map(revision -> new Key(revision.geometryScope(), geometry.bounds(),
                PedestrianRegion.Tile.at(target))).orElse(null);
    }
    private void subtract(Itinerary value) { portals -= value.portals().size(); dependencies -= value.dependencies().size(); }
    synchronized void clear() { entries.clear(); snapshots.clear(); portals = dependencies = 0; }
}
