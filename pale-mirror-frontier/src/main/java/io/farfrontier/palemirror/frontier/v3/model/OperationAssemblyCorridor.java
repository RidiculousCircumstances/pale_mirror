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
    private static final int MAX_CANDIDATES_PER_ACTOR = 8;
    private static final int MAX_JOINT_COMBINATIONS = 256;
    private static final List<Step> STEPS = List.of(new Step(0, -1), new Step(-1, 0), new Step(1, 0), new Step(0, 1));

    private OperationAssemblyCorridor() { }

    public static TraversalTopology compile(FrontierWorldState state, SubjectId operationId, SubjectId actorId, SurfaceAnchor destination) {
        Objects.requireNonNull(state, "assembly state"); Objects.requireNonNull(operationId, "assembly operation"); Objects.requireNonNull(actorId, "assembly actor"); Objects.requireNonNull(destination, "assembly destination");
        return topology(operationId, actorId, route(state, actorId, destination, Set.of()));
    }

    /**
     * Compiles all exact convoy approaches together. Individual shortest paths are not enough:
     * two otherwise valid people can claim each other's next cell at a public throat forever.
     * This bounded selection proves that the same deterministic COLD scheduler can take every
     * retained member to its declared slot before the operation is created.
     */
    public static OperationAssembly compileJoint(FrontierWorldState state, SubjectId operationId, SubjectId cargoCarrierId,
                                                Map<SubjectId, SurfaceAnchor> destinations) {
        Objects.requireNonNull(state, "assembly state"); Objects.requireNonNull(operationId, "assembly operation");
        Objects.requireNonNull(cargoCarrierId, "assembly cargo carrier"); Objects.requireNonNull(destinations, "assembly destinations");
        if (destinations.isEmpty() || destinations.size() > 8 || !destinations.containsKey(cargoCarrierId)
                || destinations.values().stream().anyMatch(Objects::isNull)
                || destinations.values().stream().distinct().count() != destinations.size()) {
            throw new IllegalArgumentException("joint operation assembly needs 1..8 distinct declared member slots");
        }
        Map<SubjectId, List<TraversalTopology>> candidates = new HashMap<>();
        destinations.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                candidates.put(entry.getKey(), candidates(state, operationId, entry.getKey(), entry.getValue())));
        SelectionBudget budget = new SelectionBudget();
        List<SubjectId> members = destinations.keySet().stream().sorted().toList();
        OperationAssembly result = selectJoint(members, candidates, cargoCarrierId, 0, new HashMap<>(), budget);
        if (result == null) throw new IllegalArgumentException("operation assembly has no bounded jointly completable approach");
        return result;
    }

    private static OperationAssembly selectJoint(List<SubjectId> members, Map<SubjectId, List<TraversalTopology>> candidates,
                                                 SubjectId cargoCarrierId, int index,
                                                 Map<SubjectId, OperationAssembly.Member> selected, SelectionBudget budget) {
        if (budget.exhausted()) return null;
        if (index == members.size()) {
            budget.record();
            OperationAssembly assembly = new OperationAssembly(selected, cargoCarrierId);
            return completesUnderRetainedSchedule(assembly) ? assembly : null;
        }
        SubjectId actor = members.get(index);
        for (TraversalTopology corridor : candidates.get(actor)) {
            selected.put(actor, new OperationAssembly.Member(corridor, 0));
            OperationAssembly result = selectJoint(members, candidates, cargoCarrierId, index + 1, selected, budget);
            if (result != null) return result;
            if (budget.exhausted()) break;
        }
        selected.remove(actor);
        return null;
    }

    /** Mirrors {@link OperationAssembly#safeAdvances()} and its lexicographic COLD owner. */
    private static boolean completesUnderRetainedSchedule(OperationAssembly initial) {
        OperationAssembly current = initial;
        int maximumMoves = current.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int move = 0; move < maximumMoves; move++) {
            if (current.complete()) return true;
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) return false;
            current = current.advance(safe.getFirst());
        }
        return current.complete();
    }

    private static List<TraversalTopology> candidates(FrontierWorldState state, SubjectId operationId, SubjectId actorId,
                                                       SurfaceAnchor destination) {
        List<SurfaceAnchor> shortest = route(state, actorId, destination, Set.of());
        LinkedHashSet<List<SurfaceAnchor>> paths = new LinkedHashSet<>();
        paths.add(shortest);
        // Deterministically remove a spread of non-endpoint cells from the shortest route.
        // This asks the same bounded A* for local alternatives without inventing a separate
        // movement authority or a coordinate-specific escape hatch.
        int interior = shortest.size() - 2;
        for (int ordinal = 1; ordinal <= MAX_CANDIDATES_PER_ACTOR - 1 && interior > 0; ordinal++) {
            int index = 1 + Math.floorDiv(Math.multiplyExact(ordinal, interior), MAX_CANDIDATES_PER_ACTOR - 1);
            if (index >= shortest.size() - 1) index = shortest.size() - 2;
            try {
                paths.add(route(state, actorId, destination, Set.of(shortest.get(index).support())));
            } catch (IllegalArgumentException unavailable) {
                // One particular detour need not exist; another retained candidate may.
            }
        }
        return paths.stream().limit(MAX_CANDIDATES_PER_ACTOR).map(path -> topology(operationId, actorId, path)).toList();
    }

    private static TraversalTopology topology(SubjectId operationId, SubjectId actorId, List<SurfaceAnchor> corridor) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:operation-assembly:" + operationId.value() + ":" + actorId.value()), 0L,
                operationId, TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), corridor);
    }

    private static List<SurfaceAnchor> route(FrontierWorldState state, SubjectId actorId, SurfaceAnchor destination,
                                             Set<BlockPosition> prohibitedFloors) {
        SurfaceAnchor start = Objects.requireNonNull(state.actorLocations().get(actorId), "assembly actor location").supportingSurface();
        Set<BlockPosition> bodyGeometry = FrontierGrayboxPlan.currentBodyGeometry(state);
        Set<BlockPosition> occupiedFloors = new LinkedHashSet<>();
        // COLD records are not physical obstacles. Only an actor which already owns a live
        // ambient body can occupy this loaded-world floor before assembly; the later HOT
        // admission observes actual Minecraft obstructions again rather than inventing them.
        state.ambientLeases().entrySet().stream().filter(entry -> !entry.getKey().equals(actorId))
                .filter(entry -> entry.getValue().status() != AmbientLeaseStatus.CLOSED)
                .map(Map.Entry::getKey).map(state.actorLocations()::get)
                .filter(java.util.Objects::nonNull).filter(location -> location.condition().status() == ActorLifeStatus.ALIVE)
                .map(ActorLocation::supportingSurface).map(SurfaceAnchor::support).forEach(occupiedFloors::add);
        if (!traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, prohibitedFloors, start.support())
                || !traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, prohibitedFloors, destination.support())) {
            throw new IllegalArgumentException("operation assembly has no clear actor or port endpoint");
        }
        Map<SurfaceAnchor, SurfaceAnchor> previous = new HashMap<>(); Map<SurfaceAnchor, Integer> cost = new HashMap<>();
        PriorityQueue<Candidate> frontier = new PriorityQueue<>(Comparator.comparingInt(Candidate::estimatedCost).thenComparingInt(Candidate::cost)
                .thenComparingInt(value -> value.position().x()).thenComparingInt(value -> value.position().y()).thenComparingInt(value -> value.position().z()));
        cost.put(start, 0); frontier.add(new Candidate(start, 0, distance(start, destination))); int searched = 0;
        while (!frontier.isEmpty()) {
            Candidate current = frontier.remove(); if (current.cost() != cost.getOrDefault(current.position(), Integer.MAX_VALUE)) continue;
            if (++searched > MAX_SEARCHED_CELLS) throw new IllegalArgumentException("operation assembly corridor search exceeds bounded profile");
            if (current.position().equals(destination)) return materializeRoute(start, destination, previous);
            for (Step step : STEPS) {
                SurfaceAnchor next = current.position().offset(step.x(), 0, step.z());
                if (!traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, prohibitedFloors, next.support())) continue;
                int nextCost = Math.addExact(current.cost(), 1); if (nextCost >= cost.getOrDefault(next, Integer.MAX_VALUE)) continue;
                previous.put(next, current.position()); cost.put(next, nextCost);
                frontier.add(new Candidate(next, nextCost, Math.addExact(nextCost, distance(next, destination))));
            }
        }
        throw new IllegalArgumentException("operation assembly corridor has no path to declared port slot");
    }

    private static List<SurfaceAnchor> materializeRoute(SurfaceAnchor start, SurfaceAnchor destination,
                                                        Map<SurfaceAnchor, SurfaceAnchor> previous) {
        ArrayDeque<SurfaceAnchor> result = new ArrayDeque<>();
        for (SurfaceAnchor cursor = destination;; cursor = previous.get(cursor)) { result.addFirst(cursor); if (cursor.equals(start)) break; }
        if (result.size() > OperationTravel.MAX_CELLS) throw new IllegalArgumentException("operation assembly corridor exceeds bounded profile");
        return List.copyOf(result);
    }

    private static int distance(SurfaceAnchor first, SurfaceAnchor second) { return Math.addExact(Math.abs(first.x() - second.x()), Math.abs(first.z() - second.z())); }
    /** A canonical route cell is a floor anchor, so its own semantic floor never blocks a body. */
    private static boolean traversable(WorldBounds bounds, Set<BlockPosition> bodyGeometry,
                                       Set<BlockPosition> occupiedFloors, Set<BlockPosition> prohibitedFloors, BlockPosition floor) {
        return bounds.contains(floor) && !occupiedFloors.contains(floor) && !prohibitedFloors.contains(floor)
                && !bodyGeometry.contains(floor.offset(0, 1, 0))
                && !bodyGeometry.contains(floor.offset(0, 2, 0));
    }
    private static final class SelectionBudget {
        private int combinations;
        boolean exhausted() { return combinations >= MAX_JOINT_COMBINATIONS; }
        void record() { combinations++; }
    }
    private record Step(int x, int z) { }
    private record Candidate(SurfaceAnchor position, int cost, int estimatedCost) { }
}
