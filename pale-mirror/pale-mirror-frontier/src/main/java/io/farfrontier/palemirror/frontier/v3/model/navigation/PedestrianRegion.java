package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Immutable layered chunk graph. Components contain mutually traversable edges only. */
final class PedestrianRegion {
    static final int SIDE = 16, CELLS = SIDE * SIDE;
    static final int BUILD_WORK = CELLS * 9; // Existing flat-world slice size, not a layered build estimate.
    record Tile(int x, int z) implements Comparable<Tile> {
        static Tile at(SurfaceAnchor surface) { return new Tile(Math.floorDiv(surface.x(), SIDE), Math.floorDiv(surface.z(), SIDE)); }
        @Override public int compareTo(Tile other) { int order = Integer.compare(x, other.x); return order != 0 ? order : Integer.compare(z, other.z); }
    }
    final Tile tile;
    private final List<SurfaceAnchor> surfaces;
    private final Map<SurfaceAnchor, Integer> indices;
    private final List<Integer>[] columns;
    private final int[] components;
    private final List<Integer>[] edges;
    private final boolean unknown;

    PedestrianRegion(Tile tile, List<SurfaceAnchor> surfaces, List<Integer>[] columns,
                     List<Integer>[] edges, int[] components, boolean unknown) {
        this.tile = tile; this.surfaces = List.copyOf(surfaces); this.columns = columns;
        this.edges = edges; this.components = components; this.unknown = unknown;
        var lookup = new HashMap<SurfaceAnchor, Integer>();
        for (int i = 0; i < surfaces.size(); i++) lookup.put(surfaces.get(i), i);
        indices = Collections.unmodifiableMap(lookup);
    }
    boolean unknown() { return unknown; }
    int size() { return surfaces.size(); }
    SurfaceAnchor surface(int index) { return surfaces.get(index); }
    List<Integer> outgoing(int index) { return edges[index]; }
    int component(SurfaceAnchor surface) { var index = indices.get(surface); return index == null ? -1 : components[index]; }
    List<SurfaceAnchor> surfaces(int x, int z) { return columns[z * SIDE + x].stream().map(surfaces::get).toList(); }
    Refinement refine(SurfaceAnchor from, SurfaceAnchor to) { return new Refinement(from, to); }
    final class Refinement {
        private final int origin, target, component;
        private final int[] previous, queue;
        private final List<SurfaceAnchor> reverse = new ArrayList<>();
        private int read, write = 1, edge, rewind = -1;
        private List<SurfaceAnchor> result;
        Refinement(SurfaceAnchor from, SurfaceAnchor to) {
            component = component(from);
            if (component < 0 || component(to) != component) throw new IllegalArgumentException("regional refinement lacks connected endpoints");
            origin = indices.get(from); target = indices.get(to);
            previous = new int[size()]; Arrays.fill(previous, -1); previous[origin] = origin;
            queue = new int[size()]; queue[0] = origin;
        }
        int advance(int budget) {
            int work = 0;
            while (work < budget && result == null) {
                work++;
                if (rewind >= 0) {
                    reverse.add(surfaces.get(rewind));
                    if (rewind == origin) { result = List.copyOf(reverse.reversed()); break; }
                    rewind = previous[rewind]; continue;
                }
                if (previous[target] >= 0) { rewind = target; continue; }
                if (read == write) throw new IllegalStateException("regional component lost its witnessed connection");
                int current = queue[read];
                if (edge == edges[current].size()) { read++; edge = 0; continue; }
                int next = edges[current].get(edge++);
                if (previous[next] < 0 && components[next] == component) { previous[next] = current; queue[write++] = next; }
            }
            return work;
        }
        List<SurfaceAnchor> result() { return result; }
    }
}
