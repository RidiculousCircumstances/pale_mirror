package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry;
import java.util.*;
import java.util.function.Predicate;

/** Bounded multi-level area discovery through the same edges as walking. Unknown space is never admitted. */
public final class ReachableSurfaceArea {
    public record Result(List<SurfaceAnchor> selected, int examined, boolean exhausted) {
        public Result { selected = List.copyOf(selected); }
    }
    public static Result select(PedestrianRouteGeometry geometry, Collection<SurfaceAnchor> starts,
            SurfaceAnchor origin, int radius, int limit, int budget, Predicate<SurfaceAnchor> suitable) {
        if (radius < 1 || radius > 128 || limit < 1 || budget < 1 || budget > 65536)
            throw new IllegalArgumentException("unbounded surface discovery");
        var order = Comparator.comparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y);
        var queue = new ArrayDeque<SurfaceAnchor>(); var seen = new HashSet<SurfaceAnchor>();
        starts.stream().sorted(order).filter(s -> geometry.knowsSupport(s) && geometry.clearanceKnown(s) && !geometry.blocked(s))
                .forEach(s -> { if (seen.add(s)) queue.addLast(s); });
        var selected = new ArrayList<SurfaceAnchor>(); int examined = 0;
        while (!queue.isEmpty() && selected.size() < limit && examined < budget) {
            var current = queue.removeFirst(); examined++;
            if (suitable.test(current)) selected.add(current);
            for (int[] offset : new int[][]{{-1, 0}, {0, -1}, {0, 1}, {1, 0}}) {
                int x = Math.addExact(current.x(), offset[0]), z = Math.addExact(current.z(), offset[1]);
                if (Math.abs((long) x - origin.x()) > radius || Math.abs((long) z - origin.z()) > radius) continue;
                for (var next : geometry.surfacesAt(x, z).stream().sorted(order).toList()) {
                    if (seen.contains(next) || !geometry.bounds().contains(next.support()) || !geometry.clearanceKnown(next)
                            || !geometry.canTraverse(current, next)) continue;
                    seen.add(next); queue.addLast(next);
                }
            }
        }
        return new Result(selected, examined, examined == budget && !queue.isEmpty());
    }
    private ReachableSurfaceArea() { }
}
