package io.farfrontier.palemirror.frontier.v3.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/**
 * Compiles one exact resident corridor around the immutable graybox geometry.
 *
 * <p>The deterministic bounded planner uses the visible route network while avoiding foundation,
 * wall, roof and organ geometry.  It also treats every other living actor's canonical hand-off
 * cell as occupied: a HOT traveller must never be assigned a route through an exact body's
 * resting place.  This is pure canonical planning; loaded-world collision and player-made
 * obstructions remain observations at the HOT boundary and never cause this compiler to rewrite
 * the route.</p>
 */
final class FrontierMigrationCorridor {
    private static final int MAX_SEARCHED_CELLS_PER_SEGMENT = 32_768;
    private static final List<Step> STEPS = List.of(new Step(0, -1), new Step(-1, 0), new Step(1, 0), new Step(0, 1));

    private FrontierMigrationCorridor() { }

    static List<BlockPosition> compile(FrontierWorldState state, SubjectId residentId, BlockPosition start, Settlement source, Settlement destination,
                                       BlockPosition arrival) {
        Objects.requireNonNull(state, "migration state"); Objects.requireNonNull(start, "migration start");
        Objects.requireNonNull(residentId, "migration resident");
        Objects.requireNonNull(source, "migration source"); Objects.requireNonNull(destination, "migration destination");
        Objects.requireNonNull(arrival, "migration arrival");
        Set<BlockPosition> structural = FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> cell.semanticPart() != GrayboxSemanticPart.ROUTE_SURFACE)
                .map(GrayboxCell::position).collect(java.util.stream.Collectors.toUnmodifiableSet());
        java.util.LinkedHashSet<BlockPosition> blocked = new java.util.LinkedHashSet<>(structural);
        state.actorLocations().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(residentId))
                .filter(entry -> entry.getValue().condition().status() == ActorLifeStatus.ALIVE)
                .map(entry -> entry.getValue().position())
                .filter(position -> !position.equals(arrival))
                .forEach(blocked::add);
        List<BlockPosition> anchors = new ArrayList<>();
        appendSupplyLaneAnchors(anchors, state.routeTopology().supplyWaypoints(state.bootstrap(), source.id()), false, blocked, state.bootstrap().bounds());
        appendSupplyLaneAnchors(anchors, state.routeTopology().supplyWaypoints(state.bootstrap(), destination.id()), true, blocked, state.bootstrap().bounds());
        anchors.add(arrival);
        List<BlockPosition> route = new ArrayList<>(); route.add(start);
        blocked.remove(start);
        blocked.remove(arrival);
        if (!traversable(state.bootstrap().bounds(), blocked, start) || !traversable(state.bootstrap().bounds(), blocked, arrival)) {
            throw new IllegalArgumentException("resident migration has no clear endpoint");
        }
        for (BlockPosition anchor : anchors) appendPath(route, nearestClearLane(state.bootstrap().bounds(), blocked, anchor), blocked, state.bootstrap().bounds());
        if (route.size() > ResidentMigrationJourney.MAX_WAYPOINTS) throw new IllegalArgumentException("migration route exceeds bounded frontier profile");
        return List.copyOf(route);
    }

    private static void appendSupplyLaneAnchors(List<BlockPosition> anchors, List<BlockPosition> supply, boolean reverse,
                                                 Set<BlockPosition> blocked, WorldBounds bounds) {
        if (reverse) {
            for (int index = supply.size() - 1; index >= 1; index--) anchors.add(nearestClearLane(bounds, blocked, supply.get(index)));
        } else {
            for (int index = 1; index < supply.size(); index++) anchors.add(nearestClearLane(bounds, blocked, supply.get(index)));
        }
    }

    private static BlockPosition nearestClearLane(WorldBounds bounds, Set<BlockPosition> blocked, BlockPosition anchor) {
        if (traversable(bounds, blocked, anchor)) return anchor;
        for (int radius = 1; radius <= 8; radius++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (Math.abs(x) + Math.abs(z) != radius) continue;
            BlockPosition candidate = anchor.offset(x, 0, z);
            if (traversable(bounds, blocked, candidate)) return candidate;
        }
        throw new IllegalArgumentException("migration corridor anchor has no clear adjacent lane: " + anchor);
    }

    private static void appendPath(List<BlockPosition> route, BlockPosition destination, Set<BlockPosition> blocked, WorldBounds bounds) {
        BlockPosition start = route.getLast();
        if (start.equals(destination)) return;
        Map<BlockPosition, BlockPosition> previous = new HashMap<>();
        Map<BlockPosition, Integer> cost = new HashMap<>();
        PriorityQueue<Candidate> frontier = new PriorityQueue<>(Comparator.comparingInt(Candidate::estimatedCost)
                .thenComparingInt(Candidate::cost).thenComparingInt(value -> value.position().x())
                .thenComparingInt(value -> value.position().z()));
        cost.put(start, 0); frontier.add(new Candidate(start, 0, distance(start, destination)));
        int searched = 0;
        while (!frontier.isEmpty()) {
            Candidate current = frontier.remove();
            if (current.cost() != cost.getOrDefault(current.position(), Integer.MAX_VALUE)) continue;
            if (++searched > MAX_SEARCHED_CELLS_PER_SEGMENT) throw new IllegalArgumentException("migration corridor search exceeds bounded profile from " + start + " to " + destination);
            if (current.position().equals(destination)) {
                ArrayDeque<BlockPosition> suffix = new ArrayDeque<>();
                for (BlockPosition cursor = destination; !cursor.equals(start); cursor = previous.get(cursor)) suffix.addFirst(cursor);
                suffix.forEach(route::add);
                if (route.size() > ResidentMigrationJourney.MAX_WAYPOINTS) throw new IllegalArgumentException("migration route exceeds bounded frontier profile");
                return;
            }
            for (Step step : STEPS) {
                BlockPosition next = current.position().offset(step.x(), 0, step.z());
                if (!traversable(bounds, blocked, next)) continue;
                int nextCost = Math.addExact(current.cost(), 1);
                if (nextCost >= cost.getOrDefault(next, Integer.MAX_VALUE)) continue;
                previous.put(next, current.position()); cost.put(next, nextCost);
                frontier.add(new Candidate(next, nextCost, Math.addExact(nextCost, distance(next, destination))));
            }
        }
        throw new IllegalArgumentException("migration corridor has no bounded path to " + destination);
    }

    private static int distance(BlockPosition first, BlockPosition second) {
        return Math.addExact(Math.abs(first.x() - second.x()), Math.abs(first.z() - second.z()));
    }

    private static boolean traversable(WorldBounds bounds, Set<BlockPosition> blocked, BlockPosition position) {
        return bounds.contains(position) && !blocked.contains(position) && !blocked.contains(position.offset(0, 1, 0))
                && !blocked.contains(position.offset(0, 2, 0));
    }

    private record Step(int x, int z) { }
    private record Candidate(BlockPosition position, int cost, int estimatedCost) { }
}
