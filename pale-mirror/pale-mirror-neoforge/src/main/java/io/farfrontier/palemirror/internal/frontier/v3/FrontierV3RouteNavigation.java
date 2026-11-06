package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** One ephemeral journey under the caller's exact order/body permission.
 * Known planning selects bounded detail; Minecraft executes local legs only.
 * Neither a prefix endpoint nor local refusal is semantic arrival/disconnection. */
final class FrontierV3RouteNavigation {
    static final int MAX_LEG_SUPPORTS = 12;
    private static final Map<Mob, Leg> LEGS = new WeakHashMap<>();
    private static final Map<Mob, Retry> RETRIES = new WeakHashMap<>();
    record Leg(FrontierV3GoalNavigation.Goal goal, List<SurfaceAnchor> route,
               int startIndex, List<SurfaceAnchor> stations) { }
    private record Retry(FrontierV3GoalNavigation.Goal goal, long at,
                         FrontierV3MinecraftGoalNavigation.Result result, FrontierV3GoalNavigation.ProviderPermission permission) { }
    private FrontierV3RouteNavigation() { }

    static FrontierV3MinecraftGoalNavigation.Result pursue(ServerLevel level, Mob actor,
            FrontierV3GoalNavigation.Goal goal, FrontierV3GoalNavigation.ProviderPermission permission) {
        var result = pursueSelected(level, actor, goal, permission);
        if (result.status() == FrontierV3MinecraftGoalNavigation.Status.ARRIVED
                && !goal.legalStations().contains(result.arrivedStation().orElseThrow()))
            return new FrontierV3MinecraftGoalNavigation.Result(FrontierV3MinecraftGoalNavigation.Status.IN_PROGRESS,
                    "local-leg-reached;semantic-goal-retained");
        if (result.transitHint().isEmpty() || !permission.current(actor)) return result;
        var route = result.transitHint();
        var leg = new Leg(goal, route, 0, List.of(route.getLast()));
        LEGS.put(actor, leg);
        var advanced = pursueLeg(level, actor, leg, permission);
        return advanced.status() == FrontierV3MinecraftGoalNavigation.Status.ARRIVED
                && !goal.legalStations().contains(advanced.arrivedStation().orElseThrow())
                ? new FrontierV3MinecraftGoalNavigation.Result(FrontierV3MinecraftGoalNavigation.Status.IN_PROGRESS,
                    "native-frontier-reached;semantic-goal-retained") : advanced;
    }

    private static FrontierV3MinecraftGoalNavigation.Result pursueSelected(ServerLevel level, Mob actor,
            FrontierV3GoalNavigation.Goal goal, FrontierV3GoalNavigation.ProviderPermission permission) {
        Objects.requireNonNull(permission, "route provider permission");
        if (!permission.current(actor)) return new FrontierV3MinecraftGoalNavigation.Result(
                FrontierV3MinecraftGoalNavigation.Status.AMBIGUOUS, "stale-provider-authority");
        Leg leg = LEGS.get(actor);
        if (leg != null && continuedGoal(goal, leg.goal()) != leg.goal()) {
            stop(actor); FrontierV3MinecraftGoalNavigation.stop(actor); leg = null;
        }
        if (arrived(level, actor, goal.legalStations())) {
            stop(actor);
            return FrontierV3MinecraftGoalNavigation.pursue(level, actor, goal.legalStations(),
                    goal.scope(), goal.order(), permission, true);
        }
        var retry = RETRIES.get(actor);
        if (retry != null && continuedGoal(goal, retry.goal()) == retry.goal() && level.getGameTime() < retry.at())
            return retry.result();
        RETRIES.remove(actor);
        if (leg == null) {
            var route = goal.routeHint();
            // A nearby terminal goal needs no strategic calculation. Never submit a
            // remote endpoint to the bounded local native provider as a fallback.
            if (route.isEmpty() && (local(actor, goal.legalStations()) || permission.nativePlanningOnly()))
                return FrontierV3MinecraftGoalNavigation.pursue(level, actor, goal.legalStations(),
                        goal.scope(), goal.order(), permission, true);
            if (route.isEmpty()) {
                var planned = plan(level, actor, goal, permission);
                if (planned.result().isPresent()) return planned.result().orElseThrow();
                route = planned.route();
            }
            leg = leg(goal, route, nearest(actor, route));
            if (leg.stations().isEmpty() || !local(actor, leg.stations())) {
                var planned = plan(level, actor, goal, permission);
                if (planned.result().isPresent()) return planned.result().orElseThrow();
                leg = leg(goal, planned.route(), 0);
            }
            LEGS.put(actor, leg);
        }
        var passing = FrontierV3MinecraftGoalNavigation.transitHandoff(actor,
                new FrontierV3MinecraftGoalNavigation.Demand(leg.stations(), goal.scope(), goal.order()), goal.legalStations());
        if (passing.isPresent()) {
            int index = leg.route().subList(leg.startIndex(), leg.route().size()).indexOf(passing.orElseThrow());
            if (index >= 0 && leg.startIndex() + index < leg.route().size() - 1) {
                leg = leg(goal, leg.route(), leg.startIndex() + index); LEGS.put(actor, leg);
            }
        }
        var result = pursueLeg(level, actor, leg, permission);
        if (result.status() == FrontierV3MinecraftGoalNavigation.Status.BLOCKED
                && !leg.stations().stream().allMatch(goal.legalStations()::contains)
                && result.blockReason().filter(reason -> reason == FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE
                        || reason == FrontierV3GoalNavigation.BlockReason.PATH_STALLED).isPresent()) {
            if (local(actor, goal.legalStations())) {
                leg = new Leg(goal, goal.legalStations(), 0, goal.legalStations());
                LEGS.put(actor, leg);
                return pursueLeg(level, actor, leg, permission);
            }
            LEGS.remove(actor);
            FrontierV3MinecraftGoalNavigation.stop(actor);
            var replanned = plan(level, actor, goal, permission);
            if (replanned.result().isPresent()) return replanned.result().orElseThrow();
            var advanced = pursueLeg(level, actor, LEGS.get(actor), permission);
            return advanced.status() == FrontierV3MinecraftGoalNavigation.Status.BLOCKED
                    ? waiting(level, actor, goal, permission, "local-leg-blocked;semantic-goal-retained") : advanced;
        }
        if (result.status() != FrontierV3MinecraftGoalNavigation.Status.ARRIVED) return result;
        SurfaceAnchor reached = result.arrivedStation().orElseThrow();
        if (goal.legalStations().contains(reached)) { stop(actor); return result; }
        int index = leg.route().subList(leg.startIndex(), leg.route().size()).indexOf(reached);
        if (index < 0) throw new IllegalStateException("local arrival escaped its retained route segment");
        int next = leg.startIndex() + index;
        if (next == leg.route().size() - 1) {
            // Refine the next prefix from the actual body, retaining the same order.
            LEGS.remove(actor);
            return pursueSelected(level, actor, goal, permission);
        }
        leg = leg(goal, leg.route(), next); LEGS.put(actor, leg);
        return pursueLeg(level, actor, leg, permission);
    }
    private record Planned(List<SurfaceAnchor> route, Optional<FrontierV3MinecraftGoalNavigation.Result> result) { }
    private static Planned plan(ServerLevel level, Mob actor, FrontierV3GoalNavigation.Goal goal,
                                FrontierV3GoalNavigation.ProviderPermission permission) {
        var knowledge = permission.routeKnowledge(goal);
        if (goal.order().isEmpty() || knowledge.isEmpty())
            return new Planned(List.of(), Optional.of(waiting(level, actor, goal, permission, "route-knowledge-unavailable")));
        var body = FrontierV3SupportedBodyCapture.observe(level, actor);
        if (body.isEmpty()) return new Planned(List.of(), Optional.of(waiting(level, actor, goal, permission, "route-departure-unobserved")));
        try {
            var route = knowledge.orElseThrow().plannedSegment(body.orElseThrow().supportingSurface(), goal.order().orElseThrow());
            if (route.isEmpty() || route.stream().anyMatch(s -> !goal.scope().permits(s.support())))
                throw new IllegalArgumentException("planned route segment escaped its hard task scope");
            LEGS.put(actor, leg(goal, route, 0));
            return new Planned(route, Optional.empty());
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            if (unavailable.status() == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH
                    || unavailable.status() == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.SAFETY_LIMIT) {
                var blocked = new FrontierV3MinecraftGoalNavigation.Result(FrontierV3MinecraftGoalNavigation.Status.BLOCKED,
                        "known-route-unavailable:" + unavailable.getMessage(), Optional.of(
                            unavailable.status() == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.SAFETY_LIMIT
                                ? FrontierV3GoalNavigation.BlockReason.SEARCH_BUDGET_EXHAUSTED : FrontierV3GoalNavigation.BlockReason.KNOWN_NO_PATH));
                RETRIES.put(actor, new Retry(goal, level.getGameTime() + FrontierV3MinecraftGoalNavigation.retryIntervalTicks(), blocked, permission));
                return new Planned(List.of(), Optional.of(blocked));
            }
            return new Planned(List.of(), Optional.of(waiting(level, actor, goal, permission, "route-" + unavailable.status() + ":" + unavailable.getMessage())));
        }
    }
    private static FrontierV3MinecraftGoalNavigation.Result waiting(ServerLevel level, Mob actor,
            FrontierV3GoalNavigation.Goal goal, FrontierV3GoalNavigation.ProviderPermission permission, String reason) {
        var result = new FrontierV3MinecraftGoalNavigation.Result(FrontierV3MinecraftGoalNavigation.Status.WAITING, reason);
        RETRIES.put(actor, new Retry(goal, level.getGameTime() + FrontierV3MinecraftGoalNavigation.retryIntervalTicks(), result, permission));
        return result;
    }
    static FrontierV3GoalNavigation.Goal continuedGoal(FrontierV3GoalNavigation.Goal incoming, FrontierV3GoalNavigation.Goal retained) {
        return incoming.legalStations().equals(retained.legalStations()) && incoming.capability() == retained.capability()
                && incoming.scope().equals(retained.scope()) && incoming.order().equals(retained.order()) ? retained : incoming;
    }
    private static boolean arrived(ServerLevel level, Mob actor, List<SurfaceAnchor> stations) {
        return stations.stream().anyMatch(station -> FrontierV3SemanticMovement.arrived(level, actor, station));
    }
    private static boolean local(Mob actor, List<SurfaceAnchor> stations) {
        var origin = actor.blockPosition();
        return stations.stream().allMatch(s -> Math.abs((long)s.x()-origin.getX()) <= FrontierV3PhysicalPathPolicy.MAX_PATH_NODES
                && Math.abs((long)s.y()+1-origin.getY()) <= FrontierV3PhysicalPathPolicy.MAX_PATH_NODES
                && Math.abs((long)s.z()-origin.getZ()) <= FrontierV3PhysicalPathPolicy.MAX_PATH_NODES);
    }
    private static FrontierV3MinecraftGoalNavigation.Result pursueLeg(ServerLevel level, Mob actor, Leg leg,
            FrontierV3GoalNavigation.ProviderPermission permission) {
        return FrontierV3MinecraftGoalNavigation.pursue(level, actor, leg.stations(), leg.goal().scope(), leg.goal().order(), permission,
                leg.stations().stream().allMatch(leg.goal().legalStations()::contains));
    }
    static Leg leg(FrontierV3GoalNavigation.Goal goal, List<SurfaceAnchor> route, int start) {
        var stations = new ArrayList<SurfaceAnchor>();
        for (int offset = MAX_LEG_SUPPORTS; offset >= 4; offset -= 4) {
            var station = route.get(Math.min(route.size()-1, start+offset));
            if (!stations.contains(station)) stations.add(station);
        }
        return new Leg(goal, List.copyOf(route), start, List.copyOf(stations));
    }
    private static int nearest(Mob actor, List<SurfaceAnchor> route) {
        var observed = actor.getOnPos(); int nearest = 0; long best = Long.MAX_VALUE;
        for (int i=0;i<route.size();i++) {
            var s=route.get(i); long distance=Math.abs((long)s.x()-observed.getX())+Math.abs((long)s.y()-observed.getY())+Math.abs((long)s.z()-observed.getZ());
            if (distance<best) {nearest=i;best=distance;}
        }
        return nearest;
    }
    static Optional<FrontierV3MinecraftGoalNavigation.Observation> waitingObservation(Mob actor) {
        var retry = RETRIES.get(actor);
        return retry != null && retry.permission().current(actor)
                ? Optional.of(new FrontierV3MinecraftGoalNavigation.Observation(retry.result().status().name(),
                    retry.result().reason(), retry.goal().legalStations(), List.of())) : Optional.empty();
    }
    static void stop(Mob actor) { LEGS.remove(actor); RETRIES.remove(actor); }
}
