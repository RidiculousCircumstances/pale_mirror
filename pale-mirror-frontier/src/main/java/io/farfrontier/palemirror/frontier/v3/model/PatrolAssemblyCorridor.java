package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
        Objects.requireNonNull(state, "patrol assembly state");
        Objects.requireNonNull(patrolId, "patrol assembly id");
        Objects.requireNonNull(settlement, "patrol assembly settlement");
        Objects.requireNonNull(unit, "patrol assembly unit");
        if (unit.kind() != RouteUnitKind.PATROL || unit.memberIds().size() != 2) {
            throw new IllegalArgumentException("graybox patrol assembly requires one exact leader and scout");
        }

        TraversalTopology supply = state.routeTopology().supplyTraversalTopology(state.bootstrap(), settlement.id());
        List<SurfaceAnchor> route = supply.linearCorridorSurfaces();
        if (route.size() < 3 || !supply.edgeAfterCursor(0).traversableBy(TraversalCapability.PEDESTRIAN)) {
            throw new IllegalArgumentException("patrol assembly requires an open first surveyed route edge");
        }
        SettlementResidentIngressPlan.Plan residentIngress = SettlementResidentIngressPlan.compile(state.bootstrap().bounds(),
                state.bootstrap().terrain(), settlement, state.bootstrap().ruleset().facilityCapacity().intactHousingBeds());
        SurfaceAnchor routePort = route.getFirst();
        if (!residentIngress.topology().nodes().containsValue(routePort)) {
            throw new IllegalArgumentException("resident ingress does not join the declared Hall route port");
        }

        SubjectId leader = unit.leaderId();
        SubjectId scout = unit.memberIds().stream().filter(member -> !member.equals(leader)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("patrol unit has no scout"));
        Map<SubjectId, PatrolAssembly.Member> members = new LinkedHashMap<>();
        members.put(leader, member(state, patrolId, residentIngress.topology(), leader, routePort, route.get(1)));
        members.put(scout, member(state, patrolId, residentIngress.topology(), scout, routePort, null));
        PatrolAssembly assembly = new PatrolAssembly(members);
        if (!completesUnderRetainedSchedule(assembly)) {
            throw new IllegalArgumentException("patrol assembly has no collision-safe retained ingress schedule");
        }
        return assembly;
    }

    private static PatrolAssembly.Member member(FrontierWorldState state, SubjectId patrolId, TraversalTopology ingress,
                                                SubjectId actorId, SurfaceAnchor routePort, SurfaceAnchor firstInspectionSurface) {
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("patrol assembly actor is absent or not alive");
        }
        List<SurfaceAnchor> surfaces = new ArrayList<>(path(ingress, actor.supportingSurface(), routePort));
        if (firstInspectionSurface != null) surfaces.add(firstInspectionSurface);
        if (surfaces.size() < 2 || surfaces.size() > TraversalTopology.MAX_NODES) {
            throw new IllegalArgumentException("patrol assembly corridor is outside bounded profile");
        }
        TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:patrol-assembly:"
                + patrolId.value() + ":" + actorId.value()), revision(surfaces), patrolId, TraversalKind.PEDESTRIAN,
                Set.of(TraversalCapability.PEDESTRIAN), surfaces);
        return new PatrolAssembly.Member(topology, 0);
    }

    /** Returns one deterministic directed path on an already compiled semantic topology. */
    private static List<SurfaceAnchor> path(TraversalTopology topology, SurfaceAnchor start, SurfaceAnchor destination) {
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
        outgoing.values().forEach(edges -> edges.sort(Comparator.comparing(edge -> edge.to().value())));
        Map<TraversalNodeId, TraversalNodeId> previous = new HashMap<>();
        ArrayDeque<TraversalNodeId> frontier = new ArrayDeque<>();
        frontier.add(startNode); previous.put(startNode, startNode);
        while (!frontier.isEmpty()) {
            TraversalNodeId current = frontier.removeFirst();
            if (current.equals(destinationNode)) return materialize(nodes, previous, startNode, destinationNode);
            for (TraversalTopology.Edge edge : outgoing.getOrDefault(current, List.of())) {
                if (previous.putIfAbsent(edge.to(), current) == null) frontier.addLast(edge.to());
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

    private static boolean completesUnderRetainedSchedule(PatrolAssembly initial) {
        PatrolAssembly current = initial;
        int maximumMoves = current.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int move = 0; move < maximumMoves; move++) {
            if (current.complete()) return true;
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) return false;
            current = current.advanceOne(safe.getFirst());
        }
        return current.complete();
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
