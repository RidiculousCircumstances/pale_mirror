package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Immutable layered chunk graph. Components contain mutually traversable edges only. */
final class PedestrianRegion {
    static final int SIDE = 16, CELLS = SIDE * SIDE;
    static final int BUILD_WORK = CELLS * 9; // Existing flat-world slice size, not a layered build estimate.
    private static final List<List<Integer>> FLAT_EDGES = flatEdges();
    private static List<List<Integer>> flatEdges() {
        var result = new ArrayList<List<Integer>>(CELLS);
        for (int z = 0; z < SIDE; z++) for (int x = 0; x < SIDE; x++) {
            var adjacent = new ArrayList<Integer>(); int at = z * SIDE + x;
            if (z > 0) adjacent.add(at - SIDE); if (x > 0) adjacent.add(at - 1);
            if (x + 1 < SIDE) adjacent.add(at + 1); if (z + 1 < SIDE) adjacent.add(at + SIDE);
            result.add(List.copyOf(adjacent));
        }
        return List.copyOf(result);
    }
    static List<Integer> flatOutgoing(int index) { return FLAT_EDGES.get(index); }
    record Tile(int x, int z) implements Comparable<Tile> {
        static Tile at(SurfaceAnchor surface) { return new Tile(Math.floorDiv(surface.x(), SIDE), Math.floorDiv(surface.z(), SIDE)); }
        @Override public int compareTo(Tile other) { int order = Integer.compare(x, other.x); return order != 0 ? order : Integer.compare(z, other.z); }
    }
    final Tile tile;
    private sealed interface Topology permits Plane, Layered {
        int size();
        SurfaceAnchor surface(int index);
        int index(SurfaceAnchor surface);
        int component(int index);
        int componentCount();
        List<Integer> outgoing(int index);
        List<SurfaceAnchor> surfaces(int x, int z);
    }
    /** Constructed only from the provider's positive clear-plane certificate. */
    private record Plane(Tile tile, int y) implements Topology {
        public int size() { return CELLS; }
        public SurfaceAnchor surface(int index) {
            Objects.checkIndex(index, CELLS);
            return SurfaceAnchor.at(tile.x() * SIDE + index % SIDE, y, tile.z() * SIDE + index / SIDE);
        }
        public int index(SurfaceAnchor surface) {
            int x = surface.x() - tile.x() * SIDE, z = surface.z() - tile.z() * SIDE;
            return surface.y() == y && x >= 0 && x < SIDE && z >= 0 && z < SIDE ? z * SIDE + x : -1;
        }
        public int component(int index) { return 0; }
        public int componentCount() { return 1; }
        public List<Integer> outgoing(int index) { return flatOutgoing(index); }
        public List<SurfaceAnchor> surfaces(int x, int z) { return List.of(surface(z * SIDE + x)); }
    }
    private static final class Layered implements Topology {
        private final List<SurfaceAnchor> surfaces;
        private final Map<SurfaceAnchor, Integer> indices;
        private final List<Integer>[] columns, edges;
        private final int[] components;
        private final int componentCount;
        Layered(List<SurfaceAnchor> surfaces, List<Integer>[] columns, List<Integer>[] edges, int[] components) {
            this.surfaces = List.copyOf(surfaces); this.columns = columns; this.edges = edges; this.components = components;
            var lookup = new HashMap<SurfaceAnchor, Integer>(); int maximum = -1;
            for (int i = 0; i < surfaces.size(); i++) { lookup.put(surfaces.get(i), i); maximum = Math.max(maximum, components[i]); }
            indices = Collections.unmodifiableMap(lookup); componentCount = maximum + 1;
        }
        public int size() { return surfaces.size(); }
        public SurfaceAnchor surface(int index) { return surfaces.get(index); }
        public int index(SurfaceAnchor surface) { return indices.getOrDefault(surface, -1); }
        public int component(int index) { return components[index]; }
        public int componentCount() { return componentCount; }
        public List<Integer> outgoing(int index) { return edges[index]; }
        public List<SurfaceAnchor> surfaces(int x, int z) { return columns[z * SIDE + x].stream().map(surfaces::get).toList(); }
    }
    private final Topology topology;
    private final boolean unknown;

    PedestrianRegion(Tile tile, List<SurfaceAnchor> surfaces, List<Integer>[] columns,
                     List<Integer>[] edges, int[] components, boolean unknown) {
        this(tile, new Layered(surfaces, columns, edges, components), unknown);
    }
    private PedestrianRegion(Tile tile, Topology topology, boolean unknown) {
        this.tile = tile; this.topology = topology; this.unknown = unknown;
    }
    static PedestrianRegion certifiedPlane(Tile tile, int y) {
        return new PedestrianRegion(tile, new Plane(tile, y), false);
    }
    boolean unknown() { return unknown; }
    int size() { return topology.size(); }
    int componentCount() { return topology.componentCount(); }
    boolean certifiedPlane() { return topology instanceof Plane; }
    SurfaceAnchor surface(int index) { return topology.surface(index); }
    List<Integer> outgoing(int index) { return topology.outgoing(index); }
    int component(SurfaceAnchor surface) { int index = topology.index(surface); return index < 0 ? -1 : topology.component(index); }
    List<SurfaceAnchor> surfaces(int x, int z) { return topology.surfaces(x, z); }
    Refinement refine(SurfaceAnchor from, SurfaceAnchor to) { return new Refinement(from, to); }
    final class Refinement {
        private final int origin, target, component;
        private final int[] previous, queue;
        private final List<SurfaceAnchor> reverse = new ArrayList<>();
        private int read, write = 1, edge, rewind = -1, planeCursor;
        private List<SurfaceAnchor> result;
        Refinement(SurfaceAnchor from, SurfaceAnchor to) {
            component = component(from);
            if (component < 0 || component(to) != component) throw new IllegalArgumentException("regional refinement lacks connected endpoints");
            origin = topology.index(from); target = topology.index(to); planeCursor = origin;
            if (topology instanceof Plane) { previous = new int[0]; queue = previous; }
            else {
                previous = new int[size()]; Arrays.fill(previous, -1); previous[origin] = origin;
                queue = new int[size()]; queue[0] = origin;
            }
        }
        int advance(int budget) {
            int work = 0;
            while (work < budget && result == null) {
                work++;
                if (topology instanceof Plane) {
                    reverse.add(surface(planeCursor));
                    if (planeCursor == target) { result = List.copyOf(reverse); break; }
                    int dx = target % SIDE - planeCursor % SIDE;
                    planeCursor += dx != 0 ? Integer.signum(dx) : Integer.signum(target / SIDE - planeCursor / SIDE) * SIDE;
                    continue;
                }
                if (rewind >= 0) {
                    reverse.add(surface(rewind));
                    if (rewind == origin) { result = List.copyOf(reverse.reversed()); break; }
                    rewind = previous[rewind]; continue;
                }
                if (previous[target] >= 0) { rewind = target; continue; }
                if (read == write) throw new IllegalStateException("regional component lost its witnessed connection");
                int current = queue[read];
                if (edge == outgoing(current).size()) { read++; edge = 0; continue; }
                int next = outgoing(current).get(edge++);
                if (previous[next] < 0 && topology.component(next) == component) { previous[next] = current; queue[write++] = next; }
            }
            return work;
        }
        List<SurfaceAnchor> result() { return result; }
    }
}
