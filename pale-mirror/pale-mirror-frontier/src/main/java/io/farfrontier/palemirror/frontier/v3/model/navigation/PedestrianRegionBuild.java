package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Resumable construction: a dense layered chunk cannot monopolize a planning turn. */
final class PedestrianRegionBuild {
    static final int MAX_LEVELS = 64, MAX_SURFACES = 4_096, MAX_EDGES = 32_768;
    private final PedestrianRouteGeometry geometry;
    private final PedestrianRegion.Tile tile;
    private final OptionalInt uniform;
    private final List<SurfaceAnchor> surfaces = new ArrayList<>();
    private final List<Integer>[] columns;
    private List<Integer>[] edges;
    private int[] parents, components, penalties;
    private List<SurfaceAnchor> pending;
    private int column, level, node, direction, neighbour, edgeCount, componentNode;
    private boolean unknown, limited;
    private PedestrianRegion result;
    private final Map<Integer, Integer> componentIds = new LinkedHashMap<>();
    @SuppressWarnings("unchecked")
    PedestrianRegionBuild(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile) {
        this(geometry, tile, uniformSupport(geometry, tile));
    }
    /** A cached layered builder already received a negative immutable certificate. */
    @SuppressWarnings("unchecked")
    PedestrianRegionBuild(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile, OptionalInt uniform) {
        this.geometry = geometry; this.tile = tile; this.uniform = uniform;
        int initial = uniform.isPresent() ? 0 : PedestrianRegion.CELLS;
        parents = new int[initial]; components = new int[initial]; penalties = new int[initial];
        columns = (List<Integer>[]) new List<?>[initial];
        edges = (List<Integer>[]) new List<?>[initial];
        for (int i = 0; i < columns.length; i++) columns[i] = new ArrayList<>();
    }
    static OptionalInt uniformSupport(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile) {
        var area = new io.farfrontier.palemirror.frontier.v3.model.WorldBounds(tile.x() * PedestrianRegion.SIDE,
                tile.z() * PedestrianRegion.SIDE, PedestrianRegion.SIDE, PedestrianRegion.SIDE);
        var bounds = geometry.bounds();
        return area.minX() >= bounds.minX() && area.minZ() >= bounds.minZ()
                && area.maxXExclusive() <= bounds.maxXExclusive() && area.maxZExclusive() <= bounds.maxZExclusive()
                ? geometry.uniformSupport(area) : OptionalInt.empty();
    }
    int advance(int budget) {
        return advance(budget, () -> true);
    }
    int advance(int budget, java.util.function.BooleanSupplier mayContinue) {
        int work = 0;
        while (work < budget && result == null && !limited && mayContinue.getAsBoolean()) {
            work++;
            if (uniform.isPresent()) { result = PedestrianRegion.certifiedPlane(tile, uniform.orElseThrow()); continue; }
            if (column < PedestrianRegion.CELLS) { survey(); continue; }
            if (node < surfaces.size()) { connect(); continue; }
            if (componentNode < surfaces.size()) {
                int root = root(componentNode);
                components[componentNode] = componentIds.computeIfAbsent(root, ignored -> componentIds.size());
                componentNode++; continue;
            }
            for (int i = 0; i < columns.length; i++) columns[i] = List.copyOf(columns[i]);
            for (int i = 0; i < surfaces.size(); i++) edges[i] = List.copyOf(edges[i]);
            result = new PedestrianRegion(tile, surfaces, columns, Arrays.copyOf(edges, surfaces.size()),
                    Arrays.copyOf(components, surfaces.size()), Arrays.copyOf(penalties, surfaces.size()), unknown);
        }
        return work;
    }
    private void survey() {
        int x = tile.x() * PedestrianRegion.SIDE + column % PedestrianRegion.SIDE;
        int z = tile.z() * PedestrianRegion.SIDE + column / PedestrianRegion.SIDE;
        var bounds = geometry.bounds();
        if (x < bounds.minX() || x >= bounds.maxXExclusive() || z < bounds.minZ() || z >= bounds.maxZExclusive()) {
            column++; return;
        }
        if (pending == null) {
            pending = geometry.surfacesAt(x, z);
            if (pending.size() > MAX_LEVELS) { limited = true; return; }
            if (pending.isEmpty()) unknown = true;
            if (pending.stream().distinct().count() != pending.size())
                throw new IllegalArgumentException("navigation column contains duplicate levels");
            pending = pending.stream().sorted(Comparator.comparingInt(SurfaceAnchor::y)).toList();
            level = 0; return;
        }
        if (level == pending.size()) { column++; pending = null; return; }
        var surface = pending.get(level++);
        if (!geometry.clearanceKnown(surface)) unknown = true;
        if (surface.x() != x || surface.z() != z) throw new IllegalArgumentException("navigation survey returned a foreign column");
        if (!bounds.contains(surface.support()) || geometry.blocked(surface)) return;
        if (surfaces.size() >= MAX_SURFACES) { limited = true; return; }
        if (surfaces.size() == parents.length) {
            int capacity = Math.min(MAX_SURFACES, parents.length * 2);
            parents = Arrays.copyOf(parents, capacity); components = Arrays.copyOf(components, capacity); edges = Arrays.copyOf(edges, capacity);
            penalties = Arrays.copyOf(penalties, capacity);
        }
        int index = surfaces.size(); surfaces.add(surface); columns[column].add(index); parents[index] = index; edges[index] = new ArrayList<>();
        penalties[index] = geometry.traversalPenalty(surface);
    }
    private void connect() {
        if (direction == 2) { node++; direction = 0; neighbour = 0; return; }
        var from = surfaces.get(node);
        int x = Math.floorMod(from.x(), PedestrianRegion.SIDE), z = Math.floorMod(from.z(), PedestrianRegion.SIDE);
        int nextX = x + (direction == 0 ? 1 : 0);
        int nextZ = z + (direction == 1 ? 1 : 0);
        if (nextX < 0 || nextX >= PedestrianRegion.SIDE || nextZ < 0 || nextZ >= PedestrianRegion.SIDE
                || neighbour >= columns[nextZ * PedestrianRegion.SIDE + nextX].size()) {
            direction++; neighbour = 0; return;
        }
        int next = columns[nextZ * PedestrianRegion.SIDE + nextX].get(neighbour++);
        boolean forward = geometry.canTraverse(from, surfaces.get(next));
        boolean backward = geometry.canTraverse(surfaces.get(next), from);
        edgeCount += (forward ? 1 : 0) + (backward ? 1 : 0);
        if (edgeCount > MAX_EDGES) { limited = true; return; }
        if (forward) edges[node].add(next);
        if (backward) edges[next].add(node);
        if (forward && backward) {
            int left = root(node), right = root(next);
            if (left != right) parents[Math.max(left, right)] = Math.min(left, right);
        }
    }
    private int root(int index) {
        while (parents[index] != index) { parents[index] = parents[parents[index]]; index = parents[index]; }
        return index;
    }
    PedestrianRegion result() { return result; }
    boolean limited() { return limited; }
}
