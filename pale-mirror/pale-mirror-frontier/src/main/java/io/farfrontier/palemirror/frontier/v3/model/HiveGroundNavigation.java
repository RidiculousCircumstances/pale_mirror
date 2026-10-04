package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/** Read-only ground provider for explicitly declared hive actors, shared by assembly, march and return.
 * Semantic targets/cohort constraints belong to callers; body and execution authority never live here. */
public final class HiveGroundNavigation {
    private HiveGroundNavigation() { }
    private static final int MAX_SEARCHED_CELLS = 16_384;
    private static final List<Step> STEPS = List.of(new Step(0, -1), new Step(-1, 0), new Step(1, 0), new Step(0, 1));

    /** A bounded legal-route miss, distinct from an incomplete actor/home declaration. */
    public static final class RouteUnavailable extends IllegalArgumentException {
        public RouteUnavailable(String detail) { super(detail); }
    }

    /** Shared immutable ground knowledge for assembly, expedition and return, not actor authority. */
    record GroundView(Set<BlockPosition> organs, Set<SurfaceAnchor> blocked) { }
    private static FrontierBootstrap groundBootstrap;
    private static Object groundOrgans, groundLifecycles, groundDeltas;
    private static GroundView groundView;

    static synchronized GroundView ground(FrontierWorldState state) {
        if (groundView == null || groundBootstrap != state.bootstrap()
                || groundOrgans != state.hiveColony().addedOrgans()
                || groundLifecycles != state.hiveColony().bioformLifecycles() || groundDeltas != state.physicalDeltas()) {
            var organs = java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(),
                    state.hiveColony().addedOrgans().values().stream()).toList();
            var intact = FrontierGrayboxPlan.intactOrganOccupancy(organs);
            var physical = KnownPedestrianRouteKnowledge.staticOccupancy(state.bootstrap());
            physical.addAll(intact);
            organs.forEach(organ -> physical.addAll(HiveOrganSupportPlan.foundationCells(state.bootstrap().terrain(), organ)));
            physical.addAll(state.physicalDeltas().keySet());
            groundView = new GroundView(Set.copyOf(intact), blockedBodySurfaces(state, physical));
            groundBootstrap = state.bootstrap(); groundOrgans = state.hiveColony().addedOrgans();
            groundLifecycles = state.hiveColony().bioformLifecycles(); groundDeltas = state.physicalDeltas();
        }
        return groundView;
    }

    /** Exact home/tray declaration supplies support; exclusions retain only formation constraints. */
    public static List<SurfaceAnchor> route(FrontierWorldState state, SubjectId actor, SurfaceAnchor start,
                                                     SurfaceAnchor destination, Set<SurfaceAnchor> excluded) {
        var view = ground(state);
        return route(state, homeHibernaculum(state, actor), start, destination, Set.copyOf(excluded), view.organs(), view.blocked());
    }

    @FunctionalInterface
    public interface StepAdmission { boolean permits(SurfaceAnchor from, SurfaceAnchor to, int nextStep); }

    /** An admission-only reservation horizon; not a second live movement cursor. */
    public static List<SurfaceAnchor> route(FrontierWorldState state, SubjectId actor, SurfaceAnchor start,
            SurfaceAnchor destination, Set<SurfaceAnchor> excluded, int reservationHorizon, StepAdmission admission) {
        if (reservationHorizon < 0 || reservationHorizon > TraversalTopology.MAX_NODES)
            throw new IllegalArgumentException("ground reservation horizon exceeds retained route bounds");
        var view = ground(state);
        return route(state, homeHibernaculum(state, actor), start, destination, Set.copyOf(excluded),
                view.organs(), view.blocked(), reservationHorizon, Objects.requireNonNull(admission));
    }

    /** A solid support is not a solid feet/head cell. Never clear a witnessed physical change. */
    public static boolean stepClear(FrontierWorldState state, SubjectId actor, SurfaceAnchor from, SurfaceAnchor to) {
        var view = ground(state); var home = homeHibernaculum(state, actor);
        return traversable(state, home, from, view.organs(), view.blocked())
                && traversable(state, home, to, view.organs(), view.blocked())
                && !state.physicalDeltas().containsKey(from.support()) && !state.physicalDeltas().containsKey(to.support());
    }

    static List<SurfaceAnchor> route(FrontierWorldState state, HiveOrgan home, SurfaceAnchor start,
            SurfaceAnchor destination, Set<SurfaceAnchor> excluded, Set<BlockPosition> intact, Set<SurfaceAnchor> blocked) {
        return route(state, home, start, destination, excluded, intact, blocked, 0, (from, to, step) -> true);
    }
    private static List<SurfaceAnchor> route(FrontierWorldState state, HiveOrgan home, SurfaceAnchor start,
            SurfaceAnchor destination, Set<SurfaceAnchor> excluded, Set<BlockPosition> intact,
            Set<SurfaceAnchor> blocked, int reservationHorizon, StepAdmission admission) {
        if (!traversable(state, home, start, intact, blocked) || !traversable(state, home, destination, intact, blocked))
            throw new RouteUnavailable("hive route lacks a declared clear supported endpoint: " + start + " -> " + destination);
        var origin = new RouteNode(start, 0);
        Map<RouteNode, RouteNode> previous = new HashMap<>();
        Map<RouteNode, Integer> cost = new HashMap<>();
        PriorityQueue<Candidate> frontier = new PriorityQueue<>(Comparator.comparingInt(Candidate::estimated)
                .thenComparing(Comparator.comparingInt(Candidate::cost).reversed()).thenComparingInt(value -> value.node().surface().x())
                .thenComparingInt(value -> value.node().surface().y()).thenComparingInt(value -> value.node().surface().z()));
        cost.put(origin, 0); frontier.add(new Candidate(origin, 0, distance(start, destination)));
        int searched = 0;
        while (!frontier.isEmpty()) {
            Candidate current = frontier.remove(); var surface = current.node().surface();
            if (current.cost() != cost.getOrDefault(current.node(), Integer.MAX_VALUE)) continue;
            if (++searched > MAX_SEARCHED_CELLS) throw new RouteUnavailable("hive ground search exceeds bounded profile: "
                    + start + " -> " + destination + " last=" + surface);
            if (surface.equals(destination)) return materialize(origin, current.node(), previous);
            for (Step direction : STEPS) {
                SurfaceAnchor next = surfaceAt(state, home, surface.x() + direction.x(), surface.z() + direction.z());
                int nextCost = Math.addExact(current.cost(), 1);
                if ((!next.equals(destination) && excluded.contains(next)) || !traversable(state, home, next, intact, blocked)
                        || Math.abs(next.y() - surface.y()) > 1 || !admission.permits(surface, next, nextCost)) continue;
                var node = new RouteNode(next, Math.min(nextCost, reservationHorizon));
                if (nextCost >= cost.getOrDefault(node, Integer.MAX_VALUE)) continue;
                previous.put(node, current.node()); cost.put(node, nextCost);
                frontier.add(new Candidate(node, nextCost, Math.addExact(nextCost, distance(next, destination))));
            }
        }
        throw new RouteUnavailable("hive ground search has no bounded route: " + start + " -> " + destination);
    }

    static boolean traversable(FrontierWorldState state, HiveOrgan home, SurfaceAnchor surface,
                                       Set<BlockPosition> intactOrgans, Set<SurfaceAnchor> blockedBodySurfaces) {
        if (!state.bootstrap().bounds().contains(surface.support())) return false;
        if (blockedBodySurfaces.contains(surface)) return false;
        if (insideTray(home, surface)) return true;
        return !intactOrgans.contains(surface.support()) && surface.equals(surfaceAt(state, home, surface.x(), surface.z()));
    }

    /**
     * Every retained ground-bioform surface requires both air cells above its support.  Compile
     * that two-cell clearance from the same bounded immutable organ geometry that materializes
     * the hive, plus each still occupied cocoon.  A floor-only check is insufficient on a grade:
     * a cocoon or organ cell can occupy the body's head while standing on the lower support.
     * Members of the completed release group are ASSEMBLING and therefore no longer contribute a
     * cocoon cell; an unrelated dormant or recovering organism still does.
     */
    private static Set<SurfaceAnchor> blockedBodySurfaces(FrontierWorldState state, Set<BlockPosition> physicalHiveCells) {
        Set<BlockPosition> occupiedCells = new LinkedHashSet<>(physicalHiveCells);
        state.hiveColony().bioformLifecycles().entrySet().stream()
                .filter(entry -> entry.getValue().phase().occupiesCocoon())
                .forEach(entry -> {
                    HiveCocoonSlot slot = entry.getValue().homeSlot().orElseThrow();
                    HiveOrgan home = java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(),
                                    state.hiveColony().addedOrgans().values().stream())
                            .filter(organ -> organ.id().equals(slot.hibernaculumId()) && organ.kind() == HiveOrganKind.HIBERNACULUM)
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("occupied cocoon has no HIBERNACULUM"));
                    occupiedCells.add(HiveCocoonPlan.cocoonCell(home, slot));
                });
        Set<SurfaceAnchor> blocked = new LinkedHashSet<>();
        for (BlockPosition cell : occupiedCells) {
            // A solid cell blocks a body whose feet or head would occupy it. It remains legal
            // to stand on that cell itself when it is the named semantic support surface.
            blocked.add(new SurfaceAnchor(cell.offset(0, -1, 0)));
            blocked.add(new SurfaceAnchor(cell.offset(0, -2, 0)));
        }
        return Set.copyOf(blocked);
    }

    static SurfaceAnchor surfaceAt(FrontierWorldState state, HiveOrgan home, int x, int z) {
        if (x >= home.anchor().x() - 2 && x <= home.anchor().x() + 2 && z >= home.anchor().z() - 2 && z <= home.anchor().z() + 2) {
            return SurfaceAnchor.at(x, home.anchor().y(), z);
        }
        return KnownPedestrianGround.forFrontier(state).at(x, z);
    }

    static HiveOrgan homeHibernaculum(FrontierWorldState state, SubjectId member) {
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(member);
        if (lifecycle == null || (lifecycle.phase() != BioformLifecyclePhase.ASSEMBLING
                && lifecycle.phase() != BioformLifecyclePhase.WAKING && lifecycle.phase() != BioformLifecyclePhase.ACTIVE
                && lifecycle.phase() != BioformLifecyclePhase.RETURNING)
                || lifecycle.homeSlot().isEmpty()) {
            throw new IllegalArgumentException("hive assembly/return member has no exact retained cocoon home");
        }
        SubjectId id = lifecycle.homeSlot().orElseThrow().hibernaculumId();
        return java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.id().equals(id) && organ.kind() == HiveOrganKind.HIBERNACULUM).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hive assembly member has no exact Hibernaculum"));
    }

    /** The last member is compiled before its durable receipt updates the actor to this surface. */
    private static boolean insideTray(HiveOrgan tray, SurfaceAnchor surface) {
        return surface.y() == tray.anchor().y() && surface.x() >= tray.anchor().x() - 2 && surface.x() <= tray.anchor().x() + 2
                && surface.z() >= tray.anchor().z() - 2 && surface.z() <= tray.anchor().z() + 2;
    }

    private static List<SurfaceAnchor> materialize(RouteNode start, RouteNode destination, Map<RouteNode, RouteNode> previous) {
        ArrayDeque<SurfaceAnchor> route = new ArrayDeque<>();
        for (RouteNode cursor = destination;; cursor = previous.get(cursor)) {
            route.addFirst(cursor.surface()); if (cursor.equals(start)) break;
        }
        if (route.size() > TraversalTopology.MAX_NODES) throw new RouteUnavailable("hive ground route exceeds topology limit");
        return List.copyOf(route);
    }

    private static int distance(SurfaceAnchor left, SurfaceAnchor right) {
        return Math.addExact(Math.abs(left.x() - right.x()), Math.abs(left.z() - right.z()));
    }

    private record RouteNode(SurfaceAnchor surface, int reservationStep) { }
    private record Candidate(RouteNode node, int cost, int estimated) { }
    private record Step(int x, int z) { }
}
