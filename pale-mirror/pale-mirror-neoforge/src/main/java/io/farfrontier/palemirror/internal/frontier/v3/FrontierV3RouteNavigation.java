package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared ephemeral route following. Only the retained final goal can report semantic arrival. */
final class FrontierV3RouteNavigation {
    // Safety bound on local hints, not a task-specific detour radius or durable cursor.
    private static final int MAX_LEG_SUPPORTS = 12;
    private static final Map<Mob, Leg> LEGS = new WeakHashMap<>();
    record Leg(FrontierV3GoalNavigation.Goal goal, int startIndex, List<SurfaceAnchor> stations, long retryHintsAt) {
        boolean retryHints(long tick, FrontierV3MinecraftGoalNavigation.Status status) {
            return retryHintsAt >= 0 && tick >= retryHintsAt && stations.equals(goal.legalStations())
                    && status == FrontierV3MinecraftGoalNavigation.Status.BLOCKED;
        }
    }
    private FrontierV3RouteNavigation() { }

    static FrontierV3MinecraftGoalNavigation.Result pursue(ServerLevel level, Mob actor,
                                                           FrontierV3GoalNavigation.Goal goal,
                                                           FrontierV3GoalNavigation.ProviderPermission permission) {
        java.util.Objects.requireNonNull(permission, "route provider permission");
        if (!permission.current(actor))
            return new FrontierV3MinecraftGoalNavigation.Result(FrontierV3MinecraftGoalNavigation.Status.AMBIGUOUS,
                    "stale-provider-authority");
        List<SurfaceAnchor> route = goal.routeHint();
        if (route.isEmpty() || goal.legalStations().stream().anyMatch(station ->
                FrontierV3SemanticMovement.arrived(level, actor, station))) {
            LEGS.remove(actor);
            return FrontierV3MinecraftGoalNavigation.pursue(level, actor, goal.legalStations(), goal.scope(), goal.order(), permission);
        }
        Leg leg = LEGS.get(actor);
        if (leg == null || !leg.goal().equals(goal)) {
            FrontierV3MinecraftGoalNavigation.stop(actor);
            leg = leg(goal, nearest(actor, route));
            LEGS.put(actor, leg);
        }
        var result = FrontierV3MinecraftGoalNavigation.pursue(level, actor, leg.stations(), goal.scope(), goal.order(), permission);
        if (leg.retryHints(level.getGameTime(), result.status())) {
            // A failed distant fallback is not a permanent replacement for the
            // route. Reconsider nearby hints at the provider's bounded retry
            // cadence as chunks/physical terrain become available. Never cancel
            // a fallback path that is actually progressing.
            leg = leg(goal, nearest(actor, route));
            LEGS.put(actor, leg);
            result = FrontierV3MinecraftGoalNavigation.pursue(level, actor, leg.stations(), goal.scope(), goal.order(), permission);
        }
        if (result.status() == FrontierV3MinecraftGoalNavigation.Status.BLOCKED
                && !leg.stations().equals(goal.legalStations())
                && result.blockReason().filter(reason -> reason == FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE
                    || reason == FrontierV3GoalNavigation.BlockReason.PATH_STALLED
                    || reason == FrontierV3GoalNavigation.BlockReason.TRAFFIC_BLOCKED).isPresent()) {
            // Hints are advisory. Try the real goal under the same hard scope;
            // keep its path while progressing, but periodically retry local
            // hints if the distant goal itself proves unreachable.
            leg = new Leg(goal, leg.startIndex(), goal.legalStations(),
                    level.getGameTime() + FrontierV3MinecraftGoalNavigation.retryIntervalTicks());
            LEGS.put(actor, leg);
            result = FrontierV3MinecraftGoalNavigation.pursue(level, actor, leg.stations(), goal.scope(), goal.order(), permission);
        }
        if (result.status() != FrontierV3MinecraftGoalNavigation.Status.ARRIVED) return result;
        SurfaceAnchor arrived = result.arrivedStation().orElseThrow();
        if (goal.legalStations().contains(arrived)) { LEGS.remove(actor); return result; }
        int reached = leg.startIndex();
        for (int index = reached; index < route.size(); index++) {
            if (route.get(index).equals(arrived)) { reached = index; break; }
        }
        LEGS.put(actor, leg(goal, reached));
        return new FrontierV3MinecraftGoalNavigation.Result(FrontierV3MinecraftGoalNavigation.Status.IN_PROGRESS,
                "local-leg-arrived-final-goal-retained");
    }

    /** Several later hints permit bypass of a blocked micro-waypoint without changing the task. */
    private static Leg leg(FrontierV3GoalNavigation.Goal goal, int start) {
        List<SurfaceAnchor> route = goal.routeHint();
        List<SurfaceAnchor> stations = new ArrayList<>();
        for (int offset = 4; offset <= MAX_LEG_SUPPORTS; offset += 4) {
            SurfaceAnchor station = route.get(Math.min(route.size() - 1, start + offset));
            if (!stations.contains(station)) stations.add(station);
        }
        return new Leg(goal, start, List.copyOf(stations), -1L);
    }

    private static int nearest(Mob actor, List<SurfaceAnchor> route) {
        var observed = actor.getOnPos();
        int nearest = 0;
        long best = Long.MAX_VALUE;
        for (int index = 0; index < route.size(); index++) {
            SurfaceAnchor station = route.get(index);
            long distance = Math.abs((long) station.x() - observed.getX())
                    + Math.abs((long) station.y() - observed.getY()) + Math.abs((long) station.z() - observed.getZ());
            if (distance < best) { nearest = index; best = distance; }
        }
        return nearest;
    }

    static void stop(Mob actor) { LEGS.remove(actor); }
}
