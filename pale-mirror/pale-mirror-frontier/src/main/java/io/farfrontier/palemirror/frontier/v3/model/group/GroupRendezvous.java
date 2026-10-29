package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.*;

/** Selects a known, reachable assembly point outside an exclusive service boundary. */
public final class GroupRendezvous {
    public static final int MAX_VISITED = 4096;
    private static final int MAX_SELECTIONS = 128;
    private static final ImmutableInputView<Map<Request, SurfaceAnchor>> VIEWS = new ImmutableInputView<>();
    private record Request(SurfaceAnchor entrance, Set<SurfaceAnchor> boundary, int members) {
        Request { boundary = Set.copyOf(boundary); }
    }
    private GroupRendezvous() { }
    /** Every successful bounded cardinal search stays within MAX_VISITED - 1
     * horizontal steps of its entrance. Bounding boxes also cover unchosen
     * entrance alternatives; height, obstacles and unknown geometry can only
     * make this lower bound looser, never justify a false negative. */
    public static long minimumHorizontalSeparation(List<SurfaceAnchor> left, List<SurfaceAnchor> right) {
        if (left.isEmpty() || right.isEmpty()) throw new IllegalArgumentException("assembly bound needs declared entrances");
        long minLX = left.stream().mapToInt(SurfaceAnchor::x).min().orElseThrow();
        long maxLX = left.stream().mapToInt(SurfaceAnchor::x).max().orElseThrow();
        long minLZ = left.stream().mapToInt(SurfaceAnchor::z).min().orElseThrow();
        long maxLZ = left.stream().mapToInt(SurfaceAnchor::z).max().orElseThrow();
        long minRX = right.stream().mapToInt(SurfaceAnchor::x).min().orElseThrow();
        long maxRX = right.stream().mapToInt(SurfaceAnchor::x).max().orElseThrow();
        long minRZ = right.stream().mapToInt(SurfaceAnchor::z).min().orElseThrow();
        long maxRZ = right.stream().mapToInt(SurfaceAnchor::z).max().orElseThrow();
        long gapX = Math.max(0, Math.max(minLX - maxRX, minRX - maxLX));
        long gapZ = Math.max(0, Math.max(minLZ - maxRZ, minRZ - maxLZ));
        return Math.max(0, gapX + gapZ - 2L * (MAX_VISITED - 1));
    }
    public static synchronized SurfaceAnchor select(KnownPedestrianRouteKnowledge knowledge, SurfaceAnchor entrance,
                                       Set<SurfaceAnchor> serviceBoundary, int members) {
        if (members < 1 || members > UnitGroup.MAX_MEMBERS) throw new IllegalArgumentException("invalid assembly roster size");
        // Geometry identity includes the captured world revision and unit profile.
        // Funds/claims may branch without invalidating this static spatial fact.
        var views = VIEWS.get(List.of(knowledge.geometry()), HashMap::new);
        var request = new Request(entrance, serviceBoundary, members);
        var previous = views.get(request);
        if (previous != null) return previous;
        var selected = search(knowledge, entrance, serviceBoundary, members);
        if (views.size() >= MAX_SELECTIONS) views.clear();
        views.put(request, selected);
        return selected;
    }
    private static SurfaceAnchor search(KnownPedestrianRouteKnowledge knowledge, SurfaceAnchor entrance,
                                       Set<SurfaceAnchor> serviceBoundary, int members) {
        var geometry = knowledge.geometry();
        var queue = new ArrayDeque<SurfaceAnchor>(); var seen = new HashSet<SurfaceAnchor>();
        if (!geometry.knowsSupport(entrance) || geometry.blocked(entrance))
            throw new KnownPedestrianNavigation.RouteUnavailable("assembly entrance is not currently known and traversable");
        queue.add(entrance); seen.add(entrance);
        while (!queue.isEmpty() && seen.size() <= MAX_VISITED) {
            var current = queue.removeFirst();
            if (knowledge.clearStation(current) && serviceBoundary.stream().allMatch(surface -> Math.abs((long) surface.x() - current.x())
                    + Math.abs((long) surface.z() - current.z()) >= members * 2L)) return current;
            for (int[] offset : List.of(new int[]{0, -1}, new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1})) {
                for (var next : geometry.surfacesAt(current.x() + offset[0], current.z() + offset[1])) {
                    if (geometry.bounds().contains(next.support()) && !geometry.blocked(next)
                            && geometry.canTraverse(current, next) && seen.add(next)) queue.addLast(next);
                }
            }
        }
        throw new KnownPedestrianNavigation.RouteUnavailable("no bounded known assembly space outside service boundary");
    }
}
