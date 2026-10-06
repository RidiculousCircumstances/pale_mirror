package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.*;

/** Selects a known, reachable assembly point outside an exclusive service boundary. */
public final class GroupRendezvous {
    public static final int MAX_VISITED = 4096;
    private GroupRendezvous() { }
    public static SurfaceAnchor select(KnownPedestrianRouteKnowledge knowledge, SurfaceAnchor entrance,
                                       Set<SurfaceAnchor> serviceBoundary, int members) {
        if (members < 1 || members > UnitGroup.MAX_MEMBERS) throw new IllegalArgumentException("invalid assembly roster size");
        var geometry = knowledge.geometry();
        var queue = new ArrayDeque<SurfaceAnchor>(); var seen = new HashSet<SurfaceAnchor>();
        if (!entrance.equals(geometry.supportAt(entrance.x(), entrance.z())) || geometry.blocked(entrance))
            throw new KnownPedestrianNavigation.RouteUnavailable("assembly entrance is not currently known and traversable");
        queue.add(entrance); seen.add(entrance);
        while (!queue.isEmpty() && seen.size() <= MAX_VISITED) {
            var current = queue.removeFirst();
            if (serviceBoundary.stream().allMatch(surface -> Math.abs((long) surface.x() - current.x())
                    + Math.abs((long) surface.z() - current.z()) >= members * 2L)) return current;
            for (int[] offset : List.of(new int[]{0, -1}, new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1})) {
                var next = geometry.supportAt(current.x() + offset[0], current.z() + offset[1]);
                if (next != null && geometry.bounds().contains(next.support()) && !geometry.blocked(next)
                        && Math.abs((long) next.y() - current.y()) <= 1 && seen.add(next)) queue.addLast(next);
            }
        }
        throw new KnownPedestrianNavigation.RouteUnavailable("no bounded known assembly space outside service boundary");
    }
}
