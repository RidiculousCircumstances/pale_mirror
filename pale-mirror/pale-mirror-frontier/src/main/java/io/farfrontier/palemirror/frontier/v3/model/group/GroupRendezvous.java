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
