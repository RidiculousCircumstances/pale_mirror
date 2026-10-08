package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPermission;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace;
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
        return assess(state, group, actorId, ActorPositionView.TravelPoint.at(position.standingBody()),
                positions, MovementPermission.allow());
    }
    public static MovementPermission assessCurrent(FrontierWorldState state, UnitGroup group, SubjectId actorId,
                                                    ActorPositionView positions, MovementPermission previous) {
        var current = positions.currentPointAt(actorId);
        return current.isEmpty() ? MovementPermission.hold(MovementPermission.Reason.POSITION_UNAVAILABLE, actorId)
                : assess(state, group, actorId, current.orElseThrow(), positions, previous);
    }
    private static MovementPermission assess(FrontierWorldState state, UnitGroup group, SubjectId actorId,
                                              ActorPositionView.TravelPoint position, ActorPositionView positions,
                                              MovementPermission previous) {
        var policy = UnitGroupMissionPorts.require(group).travelPolicy(state, group);
        var living = group.members().stream().filter(m -> state.actorLocations().get(m.actorId()).condition().status() == ActorLifeStatus.ALIVE).toList();
        var member = group.member(actorId);
        int slot = living.indexOf(member);
        if (slot < 0) return MovementPermission.hold(MovementPermission.Reason.INACTIVE_MEMBER);
        double basePace = 1.0 / policy.ticksPerEdge();
        if (living.size() == 1) return MovementPermission.allow(new TravelPace(basePace));
        var route = group.journey().orElseThrow().route();
        double progress = projection(route, position) + slot * policy.spacing();
        double slowest = Double.POSITIVE_INFINITY;
        SubjectId slowestPeer = null;
        double rosterSpan = (double) (living.size() - 1) * policy.spacing();
        double spatialGap = 0;
        SubjectId spatialPeer = null;
        for (int index = 0; index < living.size(); index++) {
            if (index == slot) continue;
            var observation = positions.currentPointAt(living.get(index).actorId());
            if (observation.isEmpty())
                return MovementPermission.hold(MovementPermission.Reason.POSITION_UNAVAILABLE, living.get(index).actorId());
            var actual = observation.orElseThrow();
            double peerProgress = projection(route, actual) + index * policy.spacing();
            // Along-route progress alone orders the members, including U bends. Lateral
            // distance may stretch the envelope but may never reverse who yields.
            // Deterministic slot order breaks exact ties; the lagging member can always close.
            double separation = distance(position, actual) - rosterSpan;
            if (separation > spatialGap
                    && (progress > peerProgress || progress == peerProgress && slot < index)) {
                spatialGap = separation; spatialPeer = living.get(index).actorId();
            }
            if (peerProgress < slowest) { slowest = peerProgress; slowestPeer = living.get(index).actorId(); }
        }
        // The hard envelope is unchanged. A held member resumes only after closing
        // to normal spacing, not one block below the same stop threshold.
        boolean recovering = previous.reason() == MovementPermission.Reason.GROUP_PROGRESS_STRETCH
                || previous.reason() == MovementPermission.Reason.GROUP_SPATIAL_STRETCH;
        double holdLimit = recovering ? policy.spacing() / 2.0 : policy.maximumStretch();
        double progressGap = progress - slowest;
        var evidence = new MovementPermission.Spacing(progressGap, spatialGap, holdLimit, policy.spacing());
        if (spatialGap > holdLimit)
            return MovementPermission.hold(MovementPermission.Reason.GROUP_SPATIAL_STRETCH, Objects.requireNonNull(spatialPeer)).withSpacing(evidence);
        if (progressGap > holdLimit)
            return MovementPermission.hold(MovementPermission.Reason.GROUP_PROGRESS_STRETCH, Objects.requireNonNull(slowestPeer)).withSpacing(evidence);
        // A continuous positive pace closes ordinary elastic lag without STOP,
        // path replacement or a new arrival. The slowest member keeps base pace.
        double excess = Math.max(0, Math.max(progressGap, spatialGap) - policy.spacing());
        return MovementPermission.allow(new TravelPace(basePace * policy.spacing() / (policy.spacing() + excess))).withSpacing(evidence);
    }
    private static double distance(ActorPositionView.TravelPoint left, ActorPositionView.TravelPoint right) {
        double x = left.x() - right.x(), y = left.y() - right.y(), z = left.z() - right.z();
        return Math.sqrt(x * x + y * y + z * z);
    }
    public static List<SurfaceAnchor> segment(FrontierWorldState state, UnitGroup group, SubjectId actor,
                                             List<SurfaceAnchor> path, long tick) {
        return segment(state, group, actor, path, ActorPositionView.canonical(state, tick));
    }
    public static List<SurfaceAnchor> segment(FrontierWorldState state, UnitGroup group, SubjectId actor,
                                             List<SurfaceAnchor> path, ActorPositionView positions) {
        int end = 1;
        while (end < path.size() && permits(state, group, actor, path.get(end), positions)) end++;
        return List.copyOf(path.subList(0, end));
    }
    private static double projection(List<SurfaceAnchor> route, ActorPositionView.TravelPoint position) {
        double best = 0, distance = Double.POSITIVE_INFINITY, travelled = 0;
        for (int index = 0; index < route.size() - 1; index++) {
            var a = route.get(index); var b = route.get(index + 1);
            double ax = a.x() + 0.5, ay = a.y() + 1.0, az = a.z() + 0.5;
            double dx = (double) b.x() - a.x(), dy = (double) b.y() - a.y(), dz = (double) b.z() - a.z();
            double lengthSquared = dx * dx + dy * dy + dz * dz;
            double fraction = lengthSquared == 0 ? 0 : Math.clamp(
                    ((position.x() - ax) * dx + (position.y() - ay) * dy + (position.z() - az) * dz) / lengthSquared, 0, 1);
            double x = ax + fraction * dx - position.x(), y = ay + fraction * dy - position.y(), z = az + fraction * dz - position.z();
            double candidate = x * x + y * y + z * z;
            double length = Math.sqrt(lengthSquared);
            if (candidate < distance) { best = travelled + fraction * length; distance = candidate; }
            travelled += length;
        }
        return best;
    }
}
