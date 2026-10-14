package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Cheap positive path proof before regional expansion. Exhaustion never means NO_PATH. */
final class PedestrianLocalSearch {
    private static final int MAX_NODES = 1_024;
    private record Candidate(SurfaceAnchor surface, int distance, long estimate) { }
    private PedestrianRouteGeometry geometry;
    private final SurfaceAnchor target;
    private final Consumer<SurfaceAnchor> observed;
    private final Map<SurfaceAnchor, Integer> distances = new HashMap<>();
    private final Map<SurfaceAnchor, SurfaceAnchor> previous = new HashMap<>();
    private final PriorityQueue<Candidate> queue = new PriorityQueue<>(Comparator.comparingLong(Candidate::estimate)
            .thenComparingInt(candidate -> -candidate.distance()).thenComparingInt(candidate -> candidate.surface().x())
            .thenComparingInt(candidate -> candidate.surface().z()).thenComparingInt(candidate -> candidate.surface().y()));
    private SurfaceAnchor expanding;
    private int direction, level;
    private List<SurfaceAnchor> neighbours;
    private List<SurfaceAnchor> result;
    private boolean finished;
    PedestrianLocalSearch(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target, Consumer<SurfaceAnchor> observed) {
        this.geometry = geometry; this.target = target; this.observed = observed;
        distances.put(start, 0); queue.add(new Candidate(start, 0, distance(start, target)));
    }
    static boolean nearby(SurfaceAnchor start, SurfaceAnchor target) { return distance(start, target) <= 64; }
    void rebase(PedestrianRouteGeometry geometry) { this.geometry = geometry; }
    int advance(int budget, BooleanSupplier mayContinue) {
        int work = 0;
        while (work < budget && !finished && mayContinue.getAsBoolean()) {
            work++;
            if (expanding == null) {
                if (queue.isEmpty() || distances.size() >= MAX_NODES) { finished = true; break; }
                var candidate = queue.remove();
                if (candidate.distance() != distances.get(candidate.surface())) continue;
                observed.accept(candidate.surface());
                var levels = geometry.surfacesAt(candidate.surface().x(), candidate.surface().z());
                if (levels.size() > PedestrianRegionBuild.MAX_LEVELS) { finished = true; break; }
                if (levels.stream().distinct().count() != levels.size())
                    throw new IllegalArgumentException("local navigation column contains duplicate levels");
                if (!levels.contains(candidate.surface()) || geometry.blocked(candidate.surface())) continue;
                if (candidate.surface().equals(target)) {
                    var reverse = new ArrayList<SurfaceAnchor>(); var cursor = target;
                    while (cursor != null) { reverse.add(cursor); cursor = previous.get(cursor); }
                    Collections.reverse(reverse); result = List.copyOf(reverse); finished = true; break;
                }
                expanding = candidate.surface(); direction = level = 0; neighbours = null;
                continue;
            }
            if (direction == 4) { expanding = null; continue; }
            int x = expanding.x() + (direction == 0 ? 1 : direction == 2 ? -1 : 0);
            int z = expanding.z() + (direction == 1 ? 1 : direction == 3 ? -1 : 0);
            var bounds = geometry.bounds();
            if (x < bounds.minX() || x >= bounds.maxXExclusive() || z < bounds.minZ() || z >= bounds.maxZExclusive()) {
                direction++; neighbours = null; continue;
            }
            if (neighbours == null) {
                observed.accept(SurfaceAnchor.at(x, expanding.y(), z));
                neighbours = geometry.surfacesAt(x, z);
                if (neighbours.size() > PedestrianRegionBuild.MAX_LEVELS) { finished = true; break; }
                if (neighbours.stream().distinct().count() != neighbours.size())
                    throw new IllegalArgumentException("local navigation column contains duplicate levels");
                neighbours = neighbours.stream().sorted(Comparator.comparingInt(SurfaceAnchor::y)).toList(); level = 0;
                continue;
            }
            if (level == neighbours.size()) { direction++; neighbours = null; continue; }
            var next = neighbours.get(level++);
            if (next.x() != x || next.z() != z) throw new IllegalArgumentException("local navigation returned a foreign column");
            if (!bounds.contains(next.support()) || !geometry.canTraverse(expanding, next)) continue;
            int cost = distances.get(expanding) + 1;
            if (cost >= distances.getOrDefault(next, Integer.MAX_VALUE)) continue;
            if (!distances.containsKey(next) && distances.size() == MAX_NODES) { finished = true; break; }
            distances.put(next, cost); previous.put(next, expanding);
            queue.add(new Candidate(next, cost, cost + distance(next, target)));
        }
        return work;
    }
    private static long distance(SurfaceAnchor from, SurfaceAnchor to) {
        return Math.abs((long) from.x() - to.x()) + Math.abs((long) from.z() - to.z());
    }
    boolean finished() { return finished; }
    List<SurfaceAnchor> result() { return result; }
}
