package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.KnownPedestrianRouteKnowledge;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Formation projects goals onto a legal route, never inventing off-route flat offsets. */
public final class GroupFormation {
    private GroupFormation() { }
    public static Map<SubjectId, SurfaceAnchor> stations(UnitGroup group, List<SurfaceAnchor> route, int cursor,
                                                       KnownPedestrianRouteKnowledge knowledge, int spacing) {
        return stations(group.members(), route, cursor, knowledge, spacing);
    }
    public static Map<SubjectId, SurfaceAnchor> stations(List<UnitGroup.Member> members, List<SurfaceAnchor> route, int cursor,
                                                       KnownPedestrianRouteKnowledge knowledge, int spacing) {
        if (members.isEmpty()) throw new IllegalArgumentException("formation needs current active participants");
        if (spacing < 2 || spacing > 16) throw new IllegalArgumentException("undeclared formation spacing");
        if (cursor < 0 || cursor >= route.size()) throw new IllegalArgumentException("formation cursor outside route");
        var result = new LinkedHashMap<SubjectId, SurfaceAnchor>();
        int index = cursor;
        for (var member : members) {
            while (index >= 0 && result.containsValue(route.get(index))) index--;
            SurfaceAnchor station;
            if (index >= 0) station = route.get(index);
            else {
                // A short final leg still needs distinct assembly places. Select from known 3-D geometry,
                // never from invented flat offsets or a fabricated walk by the leader.
                var queue = new java.util.ArrayDeque<SurfaceAnchor>(); var seen = new java.util.HashSet<SurfaceAnchor>();
                queue.add(route.get(cursor)); seen.add(route.get(cursor)); station = null;
                while (!queue.isEmpty() && seen.size() <= GroupRendezvous.MAX_VISITED) {
                    var candidate = queue.removeFirst();
                    if (result.values().stream().allMatch(value -> Math.abs((long) value.x() - candidate.x())
                            + Math.abs((long) value.z() - candidate.z()) >= spacing)) { station = candidate; break; }
                    for (int[] offset : List.of(new int[]{0,-1}, new int[]{-1,0}, new int[]{1,0}, new int[]{0,1})) {
                        var next = knowledge.geometry().supportAt(candidate.x() + offset[0], candidate.z() + offset[1]);
                        if (next != null && knowledge.geometry().bounds().contains(next.support()) && !knowledge.geometry().blocked(next)
                                && Math.abs((long) next.y() - candidate.y()) <= 1 && seen.add(next)) queue.addLast(next);
                    }
                }
                if (station == null) throw new KnownPedestrianNavigation.RouteUnavailable("group lacks distinct known assembly space");
            }
            result.put(member.actorId(), station);
            // Two support columns avoid overlapping one-block pedestrian footprints.
            index -= spacing;
        }
        return Map.copyOf(result);
    }
    public static UnitGroup.Journey first(UnitGroup group, SurfaceAnchor destination, List<SurfaceAnchor> route, KnownPedestrianRouteKnowledge knowledge, int spacing) {
        return first(group.members(), destination, route, knowledge, spacing);
    }
    public static UnitGroup.Journey first(List<UnitGroup.Member> members, SurfaceAnchor destination, List<SurfaceAnchor> route, KnownPedestrianRouteKnowledge knowledge, int spacing) {
        // One accepted bounded regional leg, not a procession of stop-and-wait micro-waypoints.
        int cursor = route.size() - 1;
        var stations = stations(members, route, cursor, knowledge, spacing);
        return new UnitGroup.Journey(destination, route, cursor, stations);
    }
}
