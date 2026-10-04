package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Compiles bounded immutable ground-bioform approaches into one retained hive departure port. */
public final class HiveAssemblyCorridor {
    private HiveAssemblyCorridor() { }

    /** Rejoin the next retained bioform checkpoint using the same home/tray and obstacle policy. */
    static List<SurfaceAnchor> rejoin(FrontierWorldState state, SubjectId actor, SurfaceAnchor start,
                                     HiveTaskAssembly.Member member, Map<SubjectId, HiveTaskAssembly.Member> members) {
        var home = HiveGroundNavigation.homeHibernaculum(state, actor);
        var target = member.corridor().get(Math.min(member.cursor() + 1, member.corridor().size() - 1));
        var view = HiveGroundNavigation.ground(state);
        var occupied = new LinkedHashSet<SurfaceAnchor>();
        members.forEach((id, other) -> { if (!id.equals(actor)) occupied.add(other.currentSurface()); });
        var blocked = new LinkedHashSet<>(view.blocked());
        blocked.addAll(occupied);
        return HiveGroundNavigation.route(state, home, start, target, occupied, view.organs(), blocked);
    }

    /** COLD never traverses a newly witnessed solid block just because the original plan is retained. */
    public static boolean stepClear(FrontierWorldState state, SubjectId actor, HiveTaskAssembly.Member member) {
        if (member.arrived()) return false;
        var blocked = HiveGroundNavigation.ground(state).blocked();
        return !blocked.contains(member.currentSurface()) && !blocked.contains(member.nextSurface())
                && !state.physicalDeltas().containsKey(member.nextSurface().support())
                && state.bootstrap().bounds().contains(member.nextSurface().support());
    }

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
        var view = HiveGroundNavigation.ground(state);
        Set<BlockPosition> intactOrgans = view.organs();
        Set<SurfaceAnchor> blockedBodySurfaces = view.blocked();
        for (SubjectId member : mobilization.memberIds()) {
            HiveOrgan home = HiveGroundNavigation.homeHibernaculum(state, member);
            SurfaceAnchor start = releasedSurface(state, member, home);
            SurfaceAnchor destination = port.memberStagingSurfaces().get(member);
            List<SurfaceAnchor> surfaces = HiveGroundNavigation.route(state, home, start, destination, allStagingSurfaces, intactOrgans, blockedBodySurfaces);
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
     * Compiles one legal return from canonical survivor bodies to their retained trays.
     * Authored floors and roads come from shared ground knowledge, not an interpolation of
     * coarse outbound waypoints that may cross solid hive tissue.
     */
    public static HiveReturnAssembly compileReturn(FrontierWorldState state, HiveMobilization mobilization) {
        Objects.requireNonNull(state, "hive return state");
        Objects.requireNonNull(mobilization, "hive return mobilization");
        if (mobilization.status() != HiveMobilizationStatus.DEPARTED) {
            throw new IllegalArgumentException("only a departed expedition may compile its return");
        }
        Map<SubjectId, HiveTaskAssembly.Member> members = new LinkedHashMap<>();
        var view = HiveGroundNavigation.ground(state);
        Set<BlockPosition> intactOrgans = view.organs();
        Set<SurfaceAnchor> blockedBodySurfaces = view.blocked();
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
            HiveOrgan home = HiveGroundNavigation.homeHibernaculum(state, member);
            homes.put(member, home);
            destinations.put(member, HiveCocoonPlan.wakingSurface(home, lifecycle.homeSlot().orElseThrow()));
        }
        if (destinations.isEmpty()) throw new IllegalArgumentException("a casualty-only expedition completes without a return cursor");
        Set<SurfaceAnchor> allHomeSurfaces = Set.copyOf(destinations.values());
        for (SubjectId member : destinations.keySet()) {
            SurfaceAnchor start = state.actorLocations().get(member).supportingSurface();
            SurfaceAnchor destination = destinations.get(member);
            HiveOrgan home = homes.get(member);
            List<SurfaceAnchor> surfaces = returnRoute(state, home, start, destination, allHomeSurfaces,
                    intactOrgans, blockedBodySurfaces, outbound.get(member));
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

    /** Adjacent bounded segments can meet at an earlier surveyed surface without retaining a loop. */
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

    /** Retained strategic waypoints guide bounded searches, never fabricate physical unit edges. */
    private static List<SurfaceAnchor> returnRoute(FrontierWorldState state, HiveOrgan home, SurfaceAnchor start,
                                                  SurfaceAnchor destination, Set<SurfaceAnchor> reservedHomes,
                                                  Set<BlockPosition> intactOrgans, Set<SurfaceAnchor> blocked,
                                                  SettlementAssaultAttacker outbound) {
        var result = new java.util.ArrayList<SurfaceAnchor>();
        result.add(start);
        if (outbound != null && !outbound.route().isEmpty() && outbound.route().getLast().equals(start.support())) {
            for (BlockPosition waypoint : outbound.route().reversed().subList(1, outbound.route().size())) {
                var target = HiveGroundNavigation.surfaceAt(state, home, waypoint.x(), waypoint.z());
                if (target.equals(result.getLast()) || reservedHomes.contains(target)
                        || !HiveGroundNavigation.traversable(state, home, target, intactOrgans, blocked)) continue;
                var segment = HiveGroundNavigation.route(state, home, result.getLast(), target, reservedHomes, intactOrgans, blocked);
                result.addAll(segment.subList(1, segment.size()));
            }
        }
        var homeward = HiveGroundNavigation.route(state, home, result.getLast(), destination, reservedHomes, intactOrgans, blocked);
        result.addAll(homeward.subList(1, homeward.size()));
        return withoutCycles(result);
    }

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

}
