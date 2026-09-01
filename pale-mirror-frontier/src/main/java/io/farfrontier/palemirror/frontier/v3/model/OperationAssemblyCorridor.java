package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/** Compiles a bounded exact approach to one declared public port slot. */
public final class OperationAssemblyCorridor {
    private static final int MAX_SEARCHED_CELLS = 16_384;
    private static final List<Step> STEPS = List.of(new Step(0, -1), new Step(-1, 0), new Step(1, 0), new Step(0, 1));

    private OperationAssemblyCorridor() { }

    public static TraversalTopology compile(FrontierWorldState state, SubjectId operationId, SubjectId actorId, SurfaceAnchor destination) {
        Objects.requireNonNull(state, "assembly state"); Objects.requireNonNull(operationId, "assembly operation"); Objects.requireNonNull(actorId, "assembly actor"); Objects.requireNonNull(destination, "assembly destination");
        SurfaceAnchor start = new SurfaceAnchor(Objects.requireNonNull(state.actorLocations().get(actorId), "assembly actor location").position());
        Set<BlockPosition> bodyGeometry = FrontierGrayboxPlan.currentBodyGeometry(state);
        Set<BlockPosition> occupiedFloors = new LinkedHashSet<>();
        // COLD records are not physical obstacles. Only an actor which already owns a live
        // ambient body can occupy this loaded-world floor before assembly; the later HOT
        // admission observes actual Minecraft obstructions again rather than inventing them.
        state.ambientLeases().entrySet().stream().filter(entry -> !entry.getKey().equals(actorId))
                .filter(entry -> entry.getValue().status() != AmbientLeaseStatus.CLOSED)
                .map(Map.Entry::getKey).map(state.actorLocations()::get)
                .filter(java.util.Objects::nonNull).filter(location -> location.condition().status() == ActorLifeStatus.ALIVE)
                .map(ActorLocation::position).forEach(occupiedFloors::add);
        if (!traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, start.support())
                || !traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, destination.support())) {
            throw new IllegalArgumentException("operation assembly has no clear actor or port endpoint");
        }
        Map<SurfaceAnchor, SurfaceAnchor> previous = new HashMap<>(); Map<SurfaceAnchor, Integer> cost = new HashMap<>();
        PriorityQueue<Candidate> frontier = new PriorityQueue<>(Comparator.comparingInt(Candidate::estimatedCost).thenComparingInt(Candidate::cost)
                .thenComparingInt(value -> value.position().x()).thenComparingInt(value -> value.position().y()).thenComparingInt(value -> value.position().z()));
        cost.put(start, 0); frontier.add(new Candidate(start, 0, distance(start, destination))); int searched = 0;
        while (!frontier.isEmpty()) {
            Candidate current = frontier.remove(); if (current.cost() != cost.getOrDefault(current.position(), Integer.MAX_VALUE)) continue;
            if (++searched > MAX_SEARCHED_CELLS) throw new IllegalArgumentException("operation assembly corridor search exceeds bounded profile");
            if (current.position().equals(destination)) return route(operationId, actorId, start, destination, previous);
            for (Step step : STEPS) {
                SurfaceAnchor next = current.position().offset(step.x(), 0, step.z());
                if (!traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, next.support())) continue;
                int nextCost = Math.addExact(current.cost(), 1); if (nextCost >= cost.getOrDefault(next, Integer.MAX_VALUE)) continue;
                previous.put(next, current.position()); cost.put(next, nextCost);
                frontier.add(new Candidate(next, nextCost, Math.addExact(nextCost, distance(next, destination))));
            }
        }
        throw new IllegalArgumentException("operation assembly corridor has no path to declared port slot");
    }

    private static TraversalTopology route(SubjectId operationId, SubjectId actorId, SurfaceAnchor start, SurfaceAnchor destination,
                                           Map<SurfaceAnchor, SurfaceAnchor> previous) {
        ArrayDeque<SurfaceAnchor> result = new ArrayDeque<>();
        for (SurfaceAnchor cursor = destination;; cursor = previous.get(cursor)) { result.addFirst(cursor); if (cursor.equals(start)) break; }
        if (result.size() > OperationTravel.MAX_CELLS) throw new IllegalArgumentException("operation assembly corridor exceeds bounded profile");
        return TraversalTopology.corridor(new TraversalTopologyId("topology:operation-assembly:" + operationId.value() + ":" + actorId.value()), 0L,
                operationId, TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), List.copyOf(result));
    }
    private static int distance(SurfaceAnchor first, SurfaceAnchor second) { return Math.addExact(Math.abs(first.x() - second.x()), Math.abs(first.z() - second.z())); }
    /** A canonical route cell is a floor anchor, so its own semantic floor never blocks a body. */
    private static boolean traversable(WorldBounds bounds, Set<BlockPosition> bodyGeometry,
                                       Set<BlockPosition> occupiedFloors, BlockPosition floor) {
        return bounds.contains(floor) && !occupiedFloors.contains(floor)
                && !bodyGeometry.contains(floor.offset(0, 1, 0))
                && !bodyGeometry.contains(floor.offset(0, 2, 0));
    }
    private record Step(int x, int z) { }
    private record Candidate(SurfaceAnchor position, int cost, int estimatedCost) { }
}
