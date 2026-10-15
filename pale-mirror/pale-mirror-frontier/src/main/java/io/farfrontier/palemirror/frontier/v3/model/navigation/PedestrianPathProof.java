package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Exact run-length geometry for a whole itinerary, not a movement clock or arrival receipt. */
public record PedestrianPathProof(SurfaceAnchor origin, List<Run> runs) {
    public static final int MAX_EDGES = 65_534;
    public record Run(int dx, int dy, int dz, int edges) {
        public Run {
            if (!PedestrianEdge.boundedDelta(dx, dy, dz)
                    || edges < 1 || edges > MAX_EDGES)
                throw new IllegalArgumentException("itinerary run needs bounded pedestrian edges");
        }
        boolean sameDirection(Run other) { return dx == other.dx && dy == other.dy && dz == other.dz; }
        SurfaceAnchor end(SurfaceAnchor from, int count) {
            return SurfaceAnchor.at(Math.addExact(from.x(), Math.multiplyExact(dx, count)),
                    Math.addExact(from.y(), Math.multiplyExact(dy, count)), Math.addExact(from.z(), Math.multiplyExact(dz, count)));
        }
    }
    public PedestrianPathProof {
        Objects.requireNonNull(origin); runs = List.copyOf(runs);
        int total = 0; Run previous = null; var position = origin;
        for (var run : runs) {
            total = Math.addExact(total, run.edges());
            if (total > MAX_EDGES || previous != null && previous.sameDirection(run))
                throw new IllegalArgumentException("itinerary exceeds its bound or has unmerged adjacent runs");
            position = run.end(position, run.edges()); previous = run;
        }
    }
    public static PedestrianPathProof from(List<SurfaceAnchor> route) {
        if (route.isEmpty() || route.size() > MAX_EDGES + 1)
            throw new IllegalArgumentException("itinerary needs bounded nonempty geometry");
        var runs = new ArrayList<Run>();
        for (int i = 1; i < route.size(); i++) {
            var from = route.get(i - 1); var to = route.get(i);
            var run = new Run(Math.subtractExact(to.x(), from.x()), Math.subtractExact(to.y(), from.y()),
                    Math.subtractExact(to.z(), from.z()), 1);
            if (!runs.isEmpty() && runs.getLast().sameDirection(run)) {
                var last = runs.removeLast(); run = new Run(run.dx(), run.dy(), run.dz(), last.edges() + 1);
            }
            runs.add(run);
        }
        return new PedestrianPathProof(route.getFirst(), runs);
    }
    public long edges() { return runs.stream().mapToLong(Run::edges).sum(); }
    public SurfaceAnchor destination() {
        var position = origin;
        for (var run : runs) position = run.end(position, run.edges());
        return position;
    }
    /** Lazy exact view: validation can inspect every edge without retaining every coordinate. */
    public List<SurfaceAnchor> surfaces() {
        var starts = new SurfaceAnchor[runs.size()]; var offsets = new int[runs.size() + 1];
        var position = origin;
        for (int i = 0; i < runs.size(); i++) {
            starts[i] = position; var run = runs.get(i);
            offsets[i + 1] = offsets[i] + run.edges(); position = run.end(position, run.edges());
        }
        var destination = position;
        return new AbstractList<>() {
            @Override public int size() { return offsets[offsets.length - 1] + 1; }
            @Override public SurfaceAnchor get(int index) {
                Objects.checkIndex(index, size());
                if (index == size() - 1) return destination;
                int found = Arrays.binarySearch(offsets, index);
                int part = found >= 0 ? found : -found - 2;
                return runs.get(part).end(starts[part], index - offsets[part]);
            }
        };
    }
}
