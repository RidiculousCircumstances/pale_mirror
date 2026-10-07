package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles the exact two-person patrol ingress from named resident surfaces to
 * the named Hall route port.
 *
 * <p>This is deliberately not a nearest-road query.  The residential ingress
 * plan owns the only permitted path from a resident's home surface to the Hall
 * port, while the retained supply topology owns the first inspected route
 * edge.  The leader crosses that first edge during assembly; the scout ends
 * one cell behind.  Consequently the subsequent {@link PatrolTravel} starts
 * as a real one-cell column without teleporting either resident or silently
 * skipping the first route edge.</p>
 */
public final class PatrolAssemblyCorridor {
    private PatrolAssemblyCorridor() { }

    public static PatrolAssembly compile(FrontierWorldState state, SubjectId patrolId, Settlement settlement,
                                         RouteUnitManifest unit) {
        return compile(state, patrolId, settlement, unit,
                state.routeTopology().settlementTraversalTopology(state.bootstrap(), settlement.id()));
    }

    /** Compiles ingress only onto the caller's already retained causal inspection topology. */
    public static PatrolAssembly compile(FrontierWorldState state, SubjectId patrolId, Settlement settlement,
                                         RouteUnitManifest unit, TraversalTopology supply) {
        Objects.requireNonNull(state, "patrol assembly state");
        Objects.requireNonNull(patrolId, "patrol assembly id");
        Objects.requireNonNull(settlement, "patrol assembly settlement");
        Objects.requireNonNull(unit, "patrol assembly unit");
        if (unit.kind() != RouteUnitKind.PATROL || unit.memberIds().size() != 2) {
            throw new IllegalArgumentException("graybox patrol assembly requires one exact leader and scout");
        }

        List<SurfaceAnchor> route = supply.linearCorridorSurfaces();
        if (route.size() < 3) throw new IllegalArgumentException("patrol assembly requires a surveyed first route edge");
        SettlementResidentIngressPlan.Plan residentIngress = SettlementResidentIngressPlan.compile(state.bootstrap().bounds(),
                state.bootstrap().terrain(), settlement, state.bootstrap().ruleset().facilityCapacity().intactHousingBeds());
        SurfaceAnchor routePort = route.getFirst();
        if (!residentIngress.topology().nodes().containsValue(routePort)) {
            throw new IllegalArgumentException("resident ingress does not join the declared Hall route port");
        }

        SubjectId leader = unit.leaderId();
        SubjectId scout = unit.memberIds().stream().filter(member -> !member.equals(leader)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("patrol unit has no scout"));
        for (List<SurfaceAnchor> leaderApproach : pathCandidates(residentIngress.topology(), state.actorLocations().get(leader), routePort)) {
            for (List<SurfaceAnchor> scoutApproach : pathCandidates(residentIngress.topology(), state.actorLocations().get(scout), routePort)) {
                Map<SubjectId, PatrolAssembly.Member> capacity = new LinkedHashMap<>();
                capacity.put(leader, member(patrolId, leader, leaderApproach, route.get(1), TraversalAvailability.OPEN));
                capacity.put(scout, member(patrolId, scout, scoutApproach, null, TraversalAvailability.OPEN));
                if (canReachFormation(new PatrolAssembly(capacity))) {
                    Map<SubjectId, PatrolAssembly.Member> retained = new LinkedHashMap<>(capacity);
                    retained.put(leader, member(patrolId, leader, leaderApproach, route.get(1), supply.edgeAfterCursor(0).availability()));
                    return new PatrolAssembly(retained);
                }
            }
        }
        throw new IllegalArgumentException("patrol assembly pair has no non-overlapping retained ingress");
    }

    /**
     * Admission predicate for a named pair.  A guard left at an earlier
     * operation's lateral formation cell is not silently snapped home merely
     * because a route inspection needs staff; it is simply not a valid member
     * of this home-originating patrol.  The selector can therefore try the
     * next exact pair without converting an ordinary staffing fact into an
     * engine quarantine.
     */
    public static boolean canCompile(FrontierWorldState state, Settlement settlement, RouteUnitManifest unit,
                                     TraversalTopology supply) {
        Objects.requireNonNull(state, "patrol assembly admission state");
        Objects.requireNonNull(settlement, "patrol assembly admission settlement");
        Objects.requireNonNull(unit, "patrol assembly admission unit");
        Objects.requireNonNull(supply, "patrol assembly admission route");
        if (unit.kind() != RouteUnitKind.PATROL || unit.memberIds().size() != 2 || supply.linearCorridorSurfaces().size() < 3) return false;
        try {
            compile(state, unit.ownerId(), settlement, unit, supply);
            return true;
        } catch (IllegalArgumentException unavailable) {
            return false;
        }
    }

    private static PatrolAssembly.Member member(SubjectId patrolId, SubjectId actorId, List<SurfaceAnchor> approach,
                                                SurfaceAnchor firstInspectionSurface,
                                                TraversalAvailability finalAvailability) {
        List<SurfaceAnchor> surfaces = new ArrayList<>(approach);
        if (firstInspectionSurface != null) surfaces.add(firstInspectionSurface);
        if (surfaces.size() < 2 || surfaces.size() > TraversalTopology.MAX_NODES) {
            throw new IllegalArgumentException("patrol assembly corridor is outside bounded profile");
        }
        TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:patrol-assembly:"
                + patrolId.value() + ":" + actorId.value()), revision(surfaces), patrolId, TraversalKind.PEDESTRIAN,
                Set.of(TraversalCapability.PEDESTRIAN), surfaces);
        if (finalAvailability != TraversalAvailability.OPEN) {
            topology = topology.withAvailability(Set.of(topology.edges().getLast().id()), finalAvailability);
        }
        return new PatrolAssembly.Member(topology, 0);
    }

    private static List<List<SurfaceAnchor>> pathCandidates(TraversalTopology topology, ActorLocation actor, SurfaceAnchor destination) {
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("patrol assembly actor is absent or not alive");
        }
        LinkedHashSet<List<SurfaceAnchor>> candidates = new LinkedHashSet<>();
        for (Comparator<String> order : List.<Comparator<String>>of(Comparator.naturalOrder(), Comparator.reverseOrder())) {
            List<SurfaceAnchor> primary = path(topology, actor.supportingSurface(), destination, order);
            candidates.add(primary);
            // A perimeter has two legitimate directions.  Breadth-first order alone only varies
            // equal-length forks, so it can still return one shared approach for both guards and
            // falsely reject a pair whose leader can clear the Hall port by taking the other
            // retained first edge.  Ban only that first hop and compile the alternate path on the
            // same declared topology; no runtime navigation or new entrance is introduced.
            if (primary.size() > 1) {
                try {
                    candidates.add(path(topology, actor.supportingSurface(), destination, order,
                            Set.of(nodeFor(topology.nodes(), primary.get(1)))));
                } catch (IllegalArgumentException unavailable) {
                    // Some bounded ingress graphs have only one departure; the primary remains
                    // the complete legal candidate set in that case.
                }
            }
        }
        return List.copyOf(candidates);
    }

    private static boolean canReachFormation(PatrolAssembly assembly) {
        PatrolAssembly current = assembly;
        int maximumTransitions = current.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int transition = 0; transition <= maximumTransitions; transition++) {
            if (current.complete()) return true;
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) return false;
            current = current.advanceOne(safe.getFirst());
        }
        throw new IllegalStateException("bounded patrol ingress exceeded its retained transition count");
    }

    /** Returns one deterministic directed path on an already compiled semantic topology. */
    private static List<SurfaceAnchor> path(TraversalTopology topology, SurfaceAnchor start, SurfaceAnchor destination,
                                            Comparator<String> nodeOrder) {
        return path(topology, start, destination, nodeOrder, Set.of());
    }

    private static List<SurfaceAnchor> path(TraversalTopology topology, SurfaceAnchor start, SurfaceAnchor destination,
                                            Comparator<String> nodeOrder, Set<TraversalNodeId> prohibited) {
        if (!topology.nodes().containsValue(start) || !topology.nodes().containsValue(destination)) {
            throw new IllegalArgumentException("patrol ingress endpoint is not a declared resident surface");
        }
        Map<TraversalNodeId, SurfaceAnchor> nodes = topology.nodes();
        TraversalNodeId startNode = nodeFor(nodes, start), destinationNode = nodeFor(nodes, destination);
        Map<TraversalNodeId, List<TraversalTopology.Edge>> outgoing = new HashMap<>();
        for (TraversalTopology.Edge edge : topology.edges()) {
            if (edge.kind() != TraversalKind.PEDESTRIAN || !edge.traversableBy(TraversalCapability.PEDESTRIAN)) continue;
            outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        }
        outgoing.values().forEach(edges -> edges.sort(Comparator.comparing(edge -> edge.to().value(), nodeOrder)));
        Map<TraversalNodeId, TraversalNodeId> previous = new HashMap<>();
        ArrayDeque<TraversalNodeId> frontier = new ArrayDeque<>();
        frontier.add(startNode); previous.put(startNode, startNode);
        while (!frontier.isEmpty()) {
            TraversalNodeId current = frontier.removeFirst();
            if (current.equals(destinationNode)) return materialize(nodes, previous, startNode, destinationNode);
            for (TraversalTopology.Edge edge : outgoing.getOrDefault(current, List.of())) {
                if (!prohibited.contains(edge.to()) && previous.putIfAbsent(edge.to(), current) == null) frontier.addLast(edge.to());
            }
        }
        throw new IllegalArgumentException("resident ingress has no retained pedestrian path to the Hall route port");
    }

    private static TraversalNodeId nodeFor(Map<TraversalNodeId, SurfaceAnchor> nodes, SurfaceAnchor surface) {
        return nodes.entrySet().stream().filter(entry -> entry.getValue().equals(surface)).map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("patrol ingress surface has no node"));
    }

    private static List<SurfaceAnchor> materialize(Map<TraversalNodeId, SurfaceAnchor> nodes,
                                                    Map<TraversalNodeId, TraversalNodeId> previous,
                                                    TraversalNodeId start, TraversalNodeId destination) {
        ArrayDeque<SurfaceAnchor> result = new ArrayDeque<>();
        for (TraversalNodeId cursor = destination;; cursor = previous.get(cursor)) {
            result.addFirst(nodes.get(cursor));
            if (cursor.equals(start)) return List.copyOf(result);
        }
    }

    private static long revision(List<SurfaceAnchor> surfaces) {
        long hash = 0xcbf29ce484222325L;
        for (SurfaceAnchor surface : surfaces) {
            hash = (hash ^ Integer.toUnsignedLong(surface.x())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.y())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.z())) * 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }
}
