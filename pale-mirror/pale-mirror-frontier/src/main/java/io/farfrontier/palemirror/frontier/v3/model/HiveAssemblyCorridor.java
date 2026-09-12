package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/** Compiles bounded immutable ground-bioform approaches into one retained hive departure port. */
public final class HiveAssemblyCorridor {
    private static final int MAX_SEARCHED_CELLS = 16_384;
    private static final List<Step> STEPS = List.of(new Step(0, -1), new Step(-1, 0), new Step(1, 0), new Step(0, 1));

    private HiveAssemblyCorridor() { }

    /**
     * Builds all exact member corridors from the already observed cocoon-release surfaces.
     * It does not consult loaded Minecraft blocks; actual HOT obstruction is a later observed
     * fact against the same retained next edge.
     */
    public static HiveTaskAssembly compile(FrontierWorldState state, HiveMobilization mobilization,
                                           HiveAssemblyPortPlan.Port port) {
        Objects.requireNonNull(state, "hive assembly state");
        Objects.requireNonNull(mobilization, "hive assembly mobilization");
        Objects.requireNonNull(port, "hive assembly port");
        // Map iteration is deliberately irrelevant, but the named set must be exact.
        if (!new LinkedHashSet<>(mobilization.memberIds()).equals(port.memberStagingSurfaces().keySet())) {
            throw new IllegalArgumentException("hive assembly port does not retain the exact mobilized group");
        }
        Map<SubjectId, HiveTaskAssembly.Member> members = new LinkedHashMap<>();
        Set<SurfaceAnchor> allStagingSurfaces = Set.copyOf(port.memberStagingSurfaces().values());
        List<HiveOrgan> hiveOrgans = java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(),
                state.hiveColony().addedOrgans().values().stream()).toList();
        Set<BlockPosition> intactOrgans = FrontierGrayboxPlan.intactOrganOccupancy(hiveOrgans);
        // The materializer emits both tissue and the terrain-provider's HIVE_TISSUE roots.
        // A corridor that sees only the former can retain a body edge that is physically full.
        Set<BlockPosition> physicalHiveCells = new LinkedHashSet<>(intactOrgans);
        hiveOrgans.forEach(organ -> physicalHiveCells.addAll(HiveOrganSupportPlan.foundationCells(state.bootstrap().terrain(), organ)));
        Set<SurfaceAnchor> blockedBodySurfaces = blockedBodySurfaces(state, physicalHiveCells);
        for (SubjectId member : mobilization.memberIds()) {
            HiveOrgan home = homeHibernaculum(state, member);
            SurfaceAnchor start = releasedSurface(state, member, home);
            SurfaceAnchor destination = port.memberStagingSurfaces().get(member);
            List<SurfaceAnchor> surfaces = route(state, home, start, destination, allStagingSurfaces, intactOrgans, blockedBodySurfaces);
            TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:hive-assembly:"
                    + mobilization.id().value() + ":" + member.value()), 0L, mobilization.id(), TraversalKind.GROUND_BIOFORM,
                    Set.of(TraversalCapability.GROUND_BIOFORM), surfaces);
            members.put(member, new HiveTaskAssembly.Member(topology, 0));
        }
        HiveTaskAssembly assembly = new HiveTaskAssembly(port.ganglionId(), members);
        if (!completesUnderRetainedSchedule(assembly)) {
            throw new IllegalArgumentException("hive assembly has no jointly completable retained approaches");
        }
        return assembly;
    }

    /**
     * Compiles the return from canonical survivor bodies to their retained trays.  A settlement
     * expedition first reverses its own immutable outbound corridor: a battle's public-access
     * floor is provider geometry, not necessarily a terrain-height cell.  Only once the body is
     * back at its retained terrain/hive approach do we compile the ordinary homeward corridor.
     */
    public static HiveReturnAssembly compileReturn(FrontierWorldState state, HiveMobilization mobilization) {
        Objects.requireNonNull(state, "hive return state");
        Objects.requireNonNull(mobilization, "hive return mobilization");
        if (mobilization.status() != HiveMobilizationStatus.DEPARTED) {
            throw new IllegalArgumentException("only a departed expedition may compile its return");
        }
        Map<SubjectId, HiveTaskAssembly.Member> members = new LinkedHashMap<>();
        List<HiveOrgan> hiveOrgans = java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(),
                state.hiveColony().addedOrgans().values().stream()).toList();
        Set<BlockPosition> intactOrgans = FrontierGrayboxPlan.intactOrganOccupancy(hiveOrgans);
        Set<BlockPosition> physicalHiveCells = new LinkedHashSet<>(intactOrgans);
        hiveOrgans.forEach(organ -> physicalHiveCells.addAll(HiveOrganSupportPlan.foundationCells(state.bootstrap().terrain(), organ)));
        Set<SurfaceAnchor> blockedBodySurfaces = blockedBodySurfaces(state, physicalHiveCells);
        Map<SubjectId, SettlementAssaultAttacker> outbound = retainedOutboundAssault(state, mobilization);
        Map<SubjectId, HiveOrgan> homes = new LinkedHashMap<>();
        Map<SubjectId, SurfaceAnchor> destinations = new LinkedHashMap<>();
        for (SubjectId member : mobilization.memberIds()) {
            ActorLocation actor = state.actorLocations().get(member);
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) continue;
            BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(member);
            if (lifecycle == null || lifecycle.phase() != BioformLifecyclePhase.ACTIVE || lifecycle.homeSlot().isEmpty()) {
                throw new IllegalArgumentException("returning survivor has no active retained cocoon home");
            }
            HiveOrgan home = homeHibernaculum(state, member);
            homes.put(member, home);
            destinations.put(member, HiveCocoonPlan.wakingSurface(home, lifecycle.homeSlot().orElseThrow()));
        }
        if (destinations.isEmpty()) throw new IllegalArgumentException("a casualty-only expedition completes without a return cursor");
        Set<SurfaceAnchor> allHomeSurfaces = Set.copyOf(destinations.values());
        for (SubjectId member : destinations.keySet()) {
            SurfaceAnchor start = state.actorLocations().get(member).supportingSurface();
            SurfaceAnchor destination = destinations.get(member);
            HiveOrgan home = homes.get(member);
            List<SurfaceAnchor> surfaces = returnRoute(state, home, start, destination, allHomeSurfaces, intactOrgans,
                    blockedBodySurfaces, outbound.get(member));
            TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:hive-return:"
                    + mobilization.id().value() + ":" + member.value()), 0L, mobilization.id(), TraversalKind.GROUND_BIOFORM,
                    Set.of(TraversalCapability.GROUND_BIOFORM), surfaces);
            members.put(member, new HiveTaskAssembly.Member(topology, 0));
        }
        HiveReturnAssembly result = new HiveReturnAssembly(mobilization.nestId(), members);
        if (!completesUnderRetainedSchedule(result)) {
            throw new IllegalArgumentException("hive return has no jointly completable retained approaches");
        }
        return result;
    }

    private static Map<SubjectId, SettlementAssaultAttacker> retainedOutboundAssault(FrontierWorldState state,
                                                                                        HiveMobilization mobilization) {
        return state.strategicPlans().settlementAssaults().values().stream()
                .filter(assault -> assault.taskId().equals(mobilization.taskId()))
                .filter(assault -> new LinkedHashSet<>(assault.attackerIds()).equals(new LinkedHashSet<>(mobilization.memberIds())))
                .findFirst().map(assault -> assault.attackers().stream()
                        .collect(java.util.stream.Collectors.toMap(SettlementAssaultAttacker::actorId, value -> value,
                                (left, right) -> { throw new IllegalArgumentException("retained assault duplicates an attacker"); }, LinkedHashMap::new)))
                .map(Map::copyOf).orElseGet(Map::of);
    }

    private static List<SurfaceAnchor> returnRoute(FrontierWorldState state, HiveOrgan home, SurfaceAnchor start,
                                                    SurfaceAnchor destination, Set<SurfaceAnchor> allHomeSurfaces,
                                                    Set<BlockPosition> intactOrgans, Set<SurfaceAnchor> blockedBodySurfaces,
                                                    SettlementAssaultAttacker outbound) {
        if (outbound == null) return route(state, home, start, destination, allHomeSurfaces, intactOrgans, blockedBodySurfaces);
        List<SurfaceAnchor> assaultSurfaces = outbound.route().stream().map(SurfaceAnchor::new).toList();
        if (assaultSurfaces.isEmpty() || !start.equals(assaultSurfaces.getLast())) {
            // A COLD child may resolve before its first strategic travel edge.  In that case the
            // actor is still on its ordinary terrain/hive approach and needs no remote leg.
            return route(state, home, start, destination, allHomeSurfaces, intactOrgans, blockedBodySurfaces);
        }
        List<SurfaceAnchor> reverse = expandAssaultWaypoints(assaultSurfaces.reversed());
        List<SurfaceAnchor> homeward = route(state, home, reverse.getLast(), destination, allHomeSurfaces, intactOrgans, blockedBodySurfaces);
        java.util.ArrayList<SurfaceAnchor> result = new java.util.ArrayList<>(reverse);
        result.addAll(homeward.subList(1, homeward.size()));
        return withoutCycles(result);
    }

    /**
     * COLD assault travel retains strategic waypoints at a coarser cadence than HOT body edges.
     * Reverse them into bounded unit edges without consulting loaded blocks; subsequent HOT
     * observation remains the sole authority that can report an obstruction or conflict.
     */
    private static List<SurfaceAnchor> expandAssaultWaypoints(List<SurfaceAnchor> waypoints) {
        java.util.ArrayList<SurfaceAnchor> result = new java.util.ArrayList<>();
        for (SurfaceAnchor waypoint : waypoints) {
            if (result.isEmpty()) {
                result.add(waypoint);
                continue;
            }
            SurfaceAnchor prior = result.getLast();
            int horizontal = Math.abs(waypoint.x() - prior.x()) + Math.abs(waypoint.z() - prior.z());
            if (horizontal == 0) throw new IllegalArgumentException("retained assault route has a vertical-only edge");
            int x = prior.x(), z = prior.z();
            for (int step = 1; step <= horizontal; step++) {
                if (x != waypoint.x()) x += Integer.signum(waypoint.x() - x);
                else z += Integer.signum(waypoint.z() - z);
                int y = prior.y() + Math.toIntExact(Math.floorDiv((long) (waypoint.y() - prior.y()) * step, horizontal));
                result.add(SurfaceAnchor.at(x, y, z));
            }
        }
        return List.copyOf(result);
    }

    /** A reverse COLD leg and homeward corridor can meet at an earlier surveyed surface. */
    private static List<SurfaceAnchor> withoutCycles(List<SurfaceAnchor> surfaces) {
        java.util.ArrayList<SurfaceAnchor> result = new java.util.ArrayList<>();
        Map<SurfaceAnchor, Integer> indexes = new HashMap<>();
        for (SurfaceAnchor surface : surfaces) {
            Integer prior = indexes.get(surface);
            if (prior == null) {
                indexes.put(surface, result.size());
                result.add(surface);
                continue;
            }
            while (result.size() > prior + 1) indexes.remove(result.removeLast());
        }
        return List.copyOf(result);
    }

    private static List<SurfaceAnchor> route(FrontierWorldState state, HiveOrgan home, SurfaceAnchor start,
                                             SurfaceAnchor destination, Set<SurfaceAnchor> allStagingSurfaces,
                                             Set<BlockPosition> intactOrgans, Set<SurfaceAnchor> blockedBodySurfaces) {
        if (!traversable(state, home, start, intactOrgans, blockedBodySurfaces)
                || !traversable(state, home, destination, intactOrgans, blockedBodySurfaces)) {
            throw new IllegalArgumentException("hive assembly has no valid tray or Ganglion port endpoint");
        }
        Map<SurfaceAnchor, SurfaceAnchor> previous = new HashMap<>();
        Map<SurfaceAnchor, Integer> cost = new HashMap<>();
        PriorityQueue<Candidate> frontier = new PriorityQueue<>(Comparator.comparingInt(Candidate::estimated)
                .thenComparingInt(Candidate::cost).thenComparingInt(value -> value.surface().x())
                .thenComparingInt(value -> value.surface().y()).thenComparingInt(value -> value.surface().z()));
        cost.put(start, 0); frontier.add(new Candidate(start, 0, distance(start, destination)));
        int searched = 0;
        while (!frontier.isEmpty()) {
            Candidate current = frontier.remove();
            if (current.cost() != cost.getOrDefault(current.surface(), Integer.MAX_VALUE)) continue;
            if (++searched > MAX_SEARCHED_CELLS) throw new IllegalArgumentException("hive assembly corridor search exceeds bounded profile");
            if (current.surface().equals(destination)) return materialize(start, destination, previous);
            for (Step step : STEPS) {
                SurfaceAnchor next = surfaceAt(state, home, current.surface().x() + step.x(), current.surface().z() + step.z());
                if ((!next.equals(destination) && allStagingSurfaces.contains(next))
                        || !traversable(state, home, next, intactOrgans, blockedBodySurfaces)
                        || Math.abs(next.y() - current.surface().y()) > 1) continue;
                int nextCost = Math.addExact(current.cost(), 1);
                if (nextCost >= cost.getOrDefault(next, Integer.MAX_VALUE)) continue;
                previous.put(next, current.surface()); cost.put(next, nextCost);
                frontier.add(new Candidate(next, nextCost, Math.addExact(nextCost, distance(next, destination))));
            }
        }
        throw new IllegalArgumentException("hive assembly corridor has no retained route to its Ganglion port");
    }

    private static boolean traversable(FrontierWorldState state, HiveOrgan home, SurfaceAnchor surface,
                                       Set<BlockPosition> intactOrgans, Set<SurfaceAnchor> blockedBodySurfaces) {
        if (!state.bootstrap().bounds().contains(surface.support())) return false;
        if (blockedBodySurfaces.contains(surface)) return false;
        if (insideTray(home, surface)) return true;
        return !intactOrgans.contains(surface.support()) && surface.y() == state.bootstrap().terrain().supportYAt(surface.x(), surface.z());
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

    private static SurfaceAnchor surfaceAt(FrontierWorldState state, HiveOrgan home, int x, int z) {
        if (x >= home.anchor().x() - 2 && x <= home.anchor().x() + 2 && z >= home.anchor().z() - 2 && z <= home.anchor().z() + 2) {
            return SurfaceAnchor.at(x, home.anchor().y(), z);
        }
        return SurfaceAnchor.at(x, state.bootstrap().terrain().supportYAt(x, z), z);
    }

    private static HiveOrgan homeHibernaculum(FrontierWorldState state, SubjectId member) {
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(member);
        if (lifecycle == null || (lifecycle.phase() != BioformLifecyclePhase.ASSEMBLING
                && lifecycle.phase() != BioformLifecyclePhase.WAKING && lifecycle.phase() != BioformLifecyclePhase.ACTIVE)
                || lifecycle.homeSlot().isEmpty()) {
            throw new IllegalArgumentException("hive assembly/return member has no exact retained cocoon home");
        }
        SubjectId id = lifecycle.homeSlot().orElseThrow().hibernaculumId();
        return java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.id().equals(id) && organ.kind() == HiveOrganKind.HIBERNACULUM).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hive assembly member has no exact Hibernaculum"));
    }

    /** The last member is compiled before its durable receipt updates the actor to this surface. */
    private static SurfaceAnchor releasedSurface(FrontierWorldState state, SubjectId member, HiveOrgan home) {
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(member);
        SurfaceAnchor expected = HiveCocoonPlan.wakingSurface(home, lifecycle.homeSlot().orElseThrow());
        ActorLocation location = Objects.requireNonNull(state.actorLocations().get(member), "released hive assembly actor location");
        if (lifecycle.phase() == BioformLifecyclePhase.WAKING) {
            BodyPosition cocoonBody = BodyPosition.above(new SurfaceAnchor(HiveCocoonPlan.cocoonCell(home, lifecycle.homeSlot().orElseThrow())));
            if (!location.body().equals(cocoonBody)) throw new IllegalArgumentException("unreleased hive assembly member was relocated before receipt");
            return expected;
        }
        if (!location.supportingSurface().equals(expected)) throw new IllegalArgumentException("released hive assembly actor left its exact tray surface");
        return expected;
    }

    private static boolean insideTray(HiveOrgan tray, SurfaceAnchor surface) {
        return surface.y() == tray.anchor().y() && surface.x() >= tray.anchor().x() - 2 && surface.x() <= tray.anchor().x() + 2
                && surface.z() >= tray.anchor().z() - 2 && surface.z() <= tray.anchor().z() + 2;
    }

    private static List<SurfaceAnchor> materialize(SurfaceAnchor start, SurfaceAnchor destination,
                                                    Map<SurfaceAnchor, SurfaceAnchor> previous) {
        ArrayDeque<SurfaceAnchor> route = new ArrayDeque<>();
        for (SurfaceAnchor cursor = destination;; cursor = previous.get(cursor)) {
            route.addFirst(cursor); if (cursor.equals(start)) break;
        }
        if (route.size() > TraversalTopology.MAX_NODES) throw new IllegalArgumentException("hive assembly corridor exceeds topology limit");
        return List.copyOf(route);
    }

    private static boolean completesUnderRetainedSchedule(HiveTaskAssembly initial) {
        HiveTaskAssembly current = initial;
        int maximumMoves = current.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int move = 0; move < maximumMoves; move++) {
            if (current.complete()) return true;
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) return false;
            current = current.advance(safe.getFirst());
        }
        return current.complete();
    }

    private static boolean completesUnderRetainedSchedule(HiveReturnAssembly initial) {
        HiveReturnAssembly current = initial;
        int maximumMoves = current.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int move = 0; move < maximumMoves; move++) {
            if (current.complete()) return true;
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) return false;
            current = current.advance(safe.getFirst());
        }
        return current.complete();
    }

    private static int distance(SurfaceAnchor left, SurfaceAnchor right) {
        return Math.addExact(Math.abs(left.x() - right.x()), Math.abs(left.z() - right.z()));
    }

    private record Candidate(SurfaceAnchor surface, int cost, int estimated) { }
    private record Step(int x, int z) { }
}
