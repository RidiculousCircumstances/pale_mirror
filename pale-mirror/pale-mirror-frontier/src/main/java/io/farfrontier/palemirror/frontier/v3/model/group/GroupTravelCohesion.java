package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPermission;
import java.util.*;

/** Read-only elastic formation envelope on the shared route. No leader, pose store or tick journal. */
public final class GroupTravelCohesion {
    private GroupTravelCohesion() { }
    public static boolean permits(FrontierWorldState state, UnitGroup group, SubjectId actorId,
                                  SurfaceAnchor position, long tick) {
        return permits(state, group, actorId, position, ActorPositionView.canonical(state, tick));
    }
    public static boolean permits(FrontierWorldState state, UnitGroup group, SubjectId actorId,
                                  SurfaceAnchor position, ActorPositionView positions) {
        return assess(state, group, actorId, position, positions).allowed();
    }
    public static MovementPermission assess(FrontierWorldState state, UnitGroup group, SubjectId actorId,
                                             SurfaceAnchor position, ActorPositionView positions) {
        var policy = UnitGroupMissionPorts.require(group).travelPolicy(state, group);
        var living = group.members().stream().filter(m -> state.actorLocations().get(m.actorId()).condition().status() == ActorLifeStatus.ALIVE).toList();
        var member = group.member(actorId);
        int slot = living.indexOf(member);
        if (slot < 0) return MovementPermission.hold(MovementPermission.Reason.INACTIVE_MEMBER);
        if (living.size() == 1) return MovementPermission.allow();
        var route = group.journey().orElseThrow().route();
        int progress = projection(route, position) + slot * policy.spacing();
        int slowest = Integer.MAX_VALUE;
        SubjectId slowestPeer = null;
        long spatialLimit = policy.maximumStretch() + (long) (living.size() - 1) * policy.spacing();
        long remaining = distance(position, route.getLast()) - (long) slot * policy.spacing();
        for (int index = 0; index < living.size(); index++) {
            if (index == slot) continue;
            var actual = positions.bodyAt(living.get(index).actorId()).supportingSurface();
            // Nearest-route projection alone loses perpendicular detour distance. The
            // nearer member waits; the lagging member is still allowed to close the gap.
            if (distance(position, actual) > spatialLimit
                    && remaining <= distance(actual, route.getLast()) - (long) index * policy.spacing())
                return MovementPermission.hold(MovementPermission.Reason.GROUP_SPATIAL_STRETCH, living.get(index).actorId());
            int peerProgress = projection(route, actual) + index * policy.spacing();
            if (peerProgress < slowest) { slowest = peerProgress; slowestPeer = living.get(index).actorId(); }
        }
        return progress <= slowest + policy.maximumStretch() ? MovementPermission.allow()
                : MovementPermission.hold(MovementPermission.Reason.GROUP_PROGRESS_STRETCH, Objects.requireNonNull(slowestPeer));
    }
    private static long distance(SurfaceAnchor left, SurfaceAnchor right) {
        return Math.abs((long) left.x() - right.x()) + Math.abs((long) left.y() - right.y()) + Math.abs((long) left.z() - right.z());
    }
    public static List<SurfaceAnchor> segment(FrontierWorldState state, UnitGroup group, SubjectId actor,
                                             List<SurfaceAnchor> path, long tick) {
        int end = 1;
        while (end < path.size() && permits(state, group, actor, path.get(end), tick)) end++;
        return List.copyOf(path.subList(0, end));
    }
    private static int projection(List<SurfaceAnchor> route, SurfaceAnchor position) {
        int best = 0; long distance = Long.MAX_VALUE;
        for (int index = 0; index < route.size(); index++) {
            var p = route.get(index);
            long dx = (long) p.x() - position.x(), dy = (long) p.y() - position.y(), dz = (long) p.z() - position.z();
            long candidate = dx * dx + dy * dy + dz * dz;
            if (candidate < distance) { best = index; distance = candidate; }
        }
        return best;
    }
}
