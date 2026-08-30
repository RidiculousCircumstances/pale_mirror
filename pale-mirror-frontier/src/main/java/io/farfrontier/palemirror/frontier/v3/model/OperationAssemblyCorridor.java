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
final class OperationAssemblyCorridor {
    private static final int MAX_SEARCHED_CELLS = 16_384;
    private static final List<Step> STEPS = List.of(new Step(0, -1), new Step(-1, 0), new Step(1, 0), new Step(0, 1));

    private OperationAssemblyCorridor() { }

    static List<BlockPosition> compile(FrontierWorldState state, SubjectId actorId, BlockPosition destination) {
        Objects.requireNonNull(state, "assembly state"); Objects.requireNonNull(actorId, "assembly actor"); Objects.requireNonNull(destination, "assembly destination");
        BlockPosition start = Objects.requireNonNull(state.actorLocations().get(actorId), "assembly actor location").position();
        Set<BlockPosition> bodyGeometry = new LinkedHashSet<>();
        FrontierGrayboxPlan.compile(state).cells().values().stream()
                .map(GrayboxCell::position).forEach(bodyGeometry::add);
        Set<BlockPosition> occupiedFloors = new LinkedHashSet<>();
        // COLD records are not physical obstacles. Only an actor which already owns a live
        // ambient body can occupy this loaded-world floor before assembly; the later HOT
        // admission observes actual Minecraft obstructions again rather than inventing them.
        state.ambientLeases().entrySet().stream().filter(entry -> !entry.getKey().equals(actorId))
                .filter(entry -> entry.getValue().status() != AmbientLeaseStatus.CLOSED)
                .map(Map.Entry::getKey).map(state.actorLocations()::get)
                .filter(java.util.Objects::nonNull).filter(location -> location.condition().status() == ActorLifeStatus.ALIVE)
                .map(ActorLocation::position).forEach(occupiedFloors::add);
        if (!traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, start)
                || !traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, destination)) {
            throw new IllegalArgumentException("operation assembly has no clear actor or port endpoint");
        }
        Map<BlockPosition, BlockPosition> previous = new HashMap<>(); Map<BlockPosition, Integer> cost = new HashMap<>();
        PriorityQueue<Candidate> frontier = new PriorityQueue<>(Comparator.comparingInt(Candidate::estimatedCost).thenComparingInt(Candidate::cost)
                .thenComparingInt(value -> value.position().x()).thenComparingInt(value -> value.position().z()));
        cost.put(start, 0); frontier.add(new Candidate(start, 0, distance(start, destination))); int searched = 0;
        while (!frontier.isEmpty()) {
            Candidate current = frontier.remove(); if (current.cost() != cost.getOrDefault(current.position(), Integer.MAX_VALUE)) continue;
            if (++searched > MAX_SEARCHED_CELLS) throw new IllegalArgumentException("operation assembly corridor search exceeds bounded profile");
            if (current.position().equals(destination)) return route(start, destination, previous);
            for (Step step : STEPS) {
                BlockPosition next = current.position().offset(step.x(), 0, step.z());
                if (!traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, next)) continue;
                int nextCost = Math.addExact(current.cost(), 1); if (nextCost >= cost.getOrDefault(next, Integer.MAX_VALUE)) continue;
                previous.put(next, current.position()); cost.put(next, nextCost);
                frontier.add(new Candidate(next, nextCost, Math.addExact(nextCost, distance(next, destination))));
            }
        }
        throw new IllegalArgumentException("operation assembly corridor has no path to declared port slot");
    }

    private static List<BlockPosition> route(BlockPosition start, BlockPosition destination, Map<BlockPosition, BlockPosition> previous) {
        ArrayDeque<BlockPosition> result = new ArrayDeque<>();
        for (BlockPosition cursor = destination;; cursor = previous.get(cursor)) { result.addFirst(cursor); if (cursor.equals(start)) break; }
        if (result.size() > OperationTravel.MAX_CELLS) throw new IllegalArgumentException("operation assembly corridor exceeds bounded profile");
        return List.copyOf(result);
    }
    private static int distance(BlockPosition first, BlockPosition second) { return Math.addExact(Math.abs(first.x() - second.x()), Math.abs(first.z() - second.z())); }
    /** A canonical route cell is a floor anchor, so its own semantic floor never blocks a body. */
    private static boolean traversable(WorldBounds bounds, Set<BlockPosition> bodyGeometry,
                                       Set<BlockPosition> occupiedFloors, BlockPosition floor) {
        return bounds.contains(floor) && !occupiedFloors.contains(floor)
                && !bodyGeometry.contains(floor.offset(0, 1, 0))
                && !bodyGeometry.contains(floor.offset(0, 2, 0));
    }
    private record Step(int x, int z) { }
    private record Candidate(BlockPosition position, int cost, int estimatedCost) { }
}
