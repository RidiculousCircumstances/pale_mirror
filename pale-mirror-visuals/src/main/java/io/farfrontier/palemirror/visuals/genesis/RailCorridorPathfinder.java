package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiPredicate;

/** Bounded deterministic coarse search which strongly prefers land but permits short bridge spans. */
final class RailCorridorPathfinder {
    private static final int STEP = 4;
    private static final int STEP_COST = 10;

    private RailCorridorPathfinder() { }

    static List<VisualPoint> find(VisualPoint from, VisualPoint to, RoutePlacementRequirement policy,
                                  BiPredicate<Integer, Integer> water) {
        Node start = new Node(0, 0, 0);
        int goalX = Math.floorDiv(to.x() - from.x(), STEP);
        int goalZ = Math.floorDiv(to.z() - from.z(), STEP);
        int minimumX = Math.min(0, goalX) - policy.searchCorridorHalfWidth() / STEP;
        int maximumX = Math.max(0, goalX) + policy.searchCorridorHalfWidth() / STEP;
        int minimumZ = Math.min(0, goalZ) - policy.searchCorridorHalfWidth() / STEP;
        int maximumZ = Math.max(0, goalZ) + policy.searchCorridorHalfWidth() / STEP;
        PriorityQueue<Visit> open = new PriorityQueue<>(Comparator.comparingInt(Visit::score)
                .thenComparingInt(value -> value.node().x()).thenComparingInt(value -> value.node().z())
                .thenComparingInt(value -> value.node().wetRun()));
        Map<Node, Integer> costs = new HashMap<>();
        Map<Node, Node> previous = new HashMap<>();
        costs.put(start, 0);
        open.add(new Visit(start, heuristic(0, 0, goalX, goalZ)));
        Node goal = null;
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < policy.searchBudget()) {
            Visit visit = open.remove();
            Node current = visit.node();
            int currentCost = costs.getOrDefault(current, Integer.MAX_VALUE);
            if (visit.score() != currentCost + heuristic(current.x(), current.z(), goalX, goalZ)) continue;
            if (current.x() == goalX && current.z() == goalZ) { goal = current; break; }
            for (int[] delta : List.of(new int[]{1, 0}, new int[]{0, 1}, new int[]{-1, 0}, new int[]{0, -1})) {
                int x = current.x() + delta[0];
                int z = current.z() + delta[1];
                if (x < minimumX || x > maximumX || z < minimumZ || z > maximumZ) continue;
                boolean wet = water.test(from.x() + x * STEP, from.z() + z * STEP);
                int wetRun = wet ? current.wetRun() + STEP : 0;
                if (wetRun > policy.maximumWaterSpan()) continue;
                Node next = new Node(x, z, wetRun);
                int cost = currentCost + STEP_COST + (wet && policy.preferDryLand() ? policy.waterPenalty() : 0);
                if (cost >= costs.getOrDefault(next, Integer.MAX_VALUE)) continue;
                costs.put(next, cost);
                previous.put(next, current);
                open.add(new Visit(next, cost + heuristic(x, z, goalX, goalZ)));
            }
        }
        if (goal == null) throw new DryMineSiteUnavailableException("No bounded dry railway corridor between "
                + from.x() + "," + from.z() + " and " + to.x() + "," + to.z());
        List<Node> coarse = new ArrayList<>();
        for (Node cursor = goal; cursor != null; cursor = previous.get(cursor)) coarse.add(cursor);
        java.util.Collections.reverse(coarse);
        List<VisualPoint> result = new ArrayList<>();
        result.add(from);
        for (int index = 1; index < coarse.size(); index++) appendCardinal(result,
                new VisualPoint(from.x() + coarse.get(index).x() * STEP, from.y(),
                        from.z() + coarse.get(index).z() * STEP));
        appendCardinal(result, new VisualPoint(to.x(), from.y(), to.z()));
        return removeLoops(result);
    }

    private static int heuristic(int x, int z, int goalX, int goalZ) {
        return (Math.abs(goalX - x) + Math.abs(goalZ - z)) * STEP_COST;
    }

    private static void appendCardinal(List<VisualPoint> path, VisualPoint target) {
        VisualPoint cursor = path.getLast();
        while (cursor.x() != target.x()) {
            cursor = new VisualPoint(cursor.x() + Integer.signum(target.x() - cursor.x()), cursor.y(), cursor.z());
            path.add(cursor);
        }
        while (cursor.z() != target.z()) {
            cursor = new VisualPoint(cursor.x(), cursor.y(), cursor.z() + Integer.signum(target.z() - cursor.z()));
            path.add(cursor);
        }
    }

    private static List<VisualPoint> removeLoops(List<VisualPoint> path) {
        List<VisualPoint> result = new ArrayList<>();
        Map<Long, Integer> positions = new HashMap<>();
        for (VisualPoint point : path) {
            long key = ((long) point.x() << 32) ^ Integer.toUnsignedLong(point.z());
            Integer previous = positions.get(key);
            if (previous != null) {
                while (result.size() > previous + 1) {
                    VisualPoint removed = result.removeLast();
                    positions.remove(((long) removed.x() << 32) ^ Integer.toUnsignedLong(removed.z()));
                }
                continue;
            }
            positions.put(key, result.size());
            result.add(point);
        }
        return List.copyOf(result);
    }

    private record Node(int x, int z, int wetRun) { }
    private record Visit(Node node, int score) { }
}
