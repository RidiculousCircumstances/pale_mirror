package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Connected known supports inside one chunk-sized tile. No route or entity authority. */
final class PedestrianRegion {
    static final int SIDE = 16;
    static final int CELLS = SIDE * SIDE;
    // Upper bound includes surveying and four-neighbour component construction.
    static final int BUILD_WORK = CELLS * 5;
    record Tile(int x, int z) implements Comparable<Tile> {
        static Tile at(SurfaceAnchor surface) { return new Tile(Math.floorDiv(surface.x(), SIDE), Math.floorDiv(surface.z(), SIDE)); }
        @Override public int compareTo(Tile other) { int xOrder = Integer.compare(x, other.x); return xOrder != 0 ? xOrder : Integer.compare(z, other.z); }
    }
    final Tile tile;
    private final SurfaceAnchor[] surfaces = new SurfaceAnchor[CELLS];
    private final int[] components = new int[CELLS];
    private boolean unknown;

    PedestrianRegion(PedestrianRouteGeometry geometry, Tile tile) {
        this.tile = tile;
        Arrays.fill(components, -1);
        int minX = Math.multiplyExact(tile.x(), SIDE), minZ = Math.multiplyExact(tile.z(), SIDE);
        var bounds = geometry.bounds();
        for (int z = 0; z < SIDE; z++) for (int x = 0; x < SIDE; x++) {
            int worldX = minX + x, worldZ = minZ + z;
            if (worldX < bounds.minX() || worldX >= bounds.maxXExclusive()
                    || worldZ < bounds.minZ() || worldZ >= bounds.maxZExclusive()) continue;
            SurfaceAnchor support = geometry.supportAt(worldX, worldZ);
            if (support == null) { unknown = true; continue; }
            if (support.x() != worldX || support.z() != worldZ)
                throw new IllegalArgumentException("navigation survey returned a foreign column");
            if (!geometry.blocked(support)) surfaces[index(x, z)] = support;
        }
        int component = 0;
        int[] queue = new int[CELLS];
        for (int cell = 0; cell < CELLS; cell++) if (surfaces[cell] != null && components[cell] < 0) {
            int read = 0, write = 0; queue[write++] = cell; components[cell] = component;
            while (read < write) {
                int current = queue[read++];
                for (int direction = 0; direction < 4; direction++) {
                    int next = neighbour(current, direction);
                    if (next < 0 || surfaces[next] == null || components[next] >= 0 || !connected(current, next)) continue;
                    components[next] = component; queue[write++] = next;
                }
            }
            component++;
        }
    }

    boolean unknown() { return unknown; }
    int component(SurfaceAnchor surface) {
        if (!tile.equals(Tile.at(surface))) return -1;
        int index = index(Math.floorMod(surface.x(), SIDE), Math.floorMod(surface.z(), SIDE));
        return surface.equals(surfaces[index]) ? components[index] : -1;
    }
    SurfaceAnchor surface(int x, int z) { return surfaces[index(x, z)]; }
    int componentAt(int x, int z) { return components[index(x, z)]; }

    List<SurfaceAnchor> path(SurfaceAnchor from, SurfaceAnchor to) {
        int component = component(from);
        if (component < 0 || component(to) != component) throw new IllegalArgumentException("regional refinement lacks connected endpoints");
        int origin = index(Math.floorMod(from.x(), SIDE), Math.floorMod(from.z(), SIDE));
        int target = index(Math.floorMod(to.x(), SIDE), Math.floorMod(to.z(), SIDE));
        int[] previous = new int[CELLS]; Arrays.fill(previous, -1); previous[origin] = origin;
        int[] queue = new int[CELLS]; int read = 0, write = 0; queue[write++] = origin;
        while (read < write && previous[target] < 0) {
            int current = queue[read++];
            for (int direction = 0; direction < 4; direction++) {
                int next = neighbour(current, direction);
                if (next < 0 || previous[next] >= 0 || components[next] != component || !connected(current, next)) continue;
                previous[next] = current; queue[write++] = next;
            }
        }
        if (previous[target] < 0) throw new IllegalStateException("regional component lost its witnessed connection");
        var reverse = new ArrayList<SurfaceAnchor>();
        for (int cell = target; cell != origin; cell = previous[cell]) reverse.add(surfaces[cell]);
        reverse.add(from);
        return reverse.reversed();
    }

    private boolean connected(int first, int second) { return Math.abs(surfaces[first].y() - surfaces[second].y()) <= 1; }
    private static int index(int x, int z) { return z * SIDE + x; }
    private static int neighbour(int cell, int direction) {
        int x = cell % SIDE, z = cell / SIDE;
        return switch (direction) {
            case 0 -> x + 1 < SIDE ? cell + 1 : -1;
            case 1 -> z > 0 ? cell - SIDE : -1;
            case 2 -> x > 0 ? cell - 1 : -1;
            case 3 -> z + 1 < SIDE ? cell + SIDE : -1;
            default -> throw new IllegalArgumentException("foreign region direction");
        };
    }
}
