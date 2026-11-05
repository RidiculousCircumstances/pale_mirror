package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalGoalFieldTest {
    private static final class Graph implements IncrementalGoalField.Graph<Integer> {
        final Map<Integer, List<Integer>> arcs;
        final Map<Integer, Integer> weights;
        Graph(Map<Integer, List<Integer>> arcs) { this(arcs, Map.of()); }
        Graph(Map<Integer, List<Integer>> arcs, Map<Integer, Integer> weights) { this.arcs = Map.copyOf(arcs); this.weights = Map.copyOf(weights); }
        public List<Integer> nodes() { return arcs.keySet().stream().sorted().toList(); }
        public boolean contains(Integer node) { return arcs.containsKey(node); }
        public List<Integer> successors(Integer node) { return arcs.getOrDefault(node, List.of()); }
        public List<Integer> predecessors(Integer node) { return nodes().stream().filter(from -> successors(from).contains(node)).toList(); }
        public int cost(Integer from, Integer to) { return weights.getOrDefault(to, 1); }
        public boolean unitCosts() { return weights.isEmpty(); }
    }
    @Test void weightedFieldPrefersAPassageAndRepairsChangedCostsWithoutChangingConnectivity() {
        var arcs = Map.of(0, List.of(1, 2), 1, List.of(4), 2, List.of(3), 3, List.of(4), 4, List.<Integer>of());
        var first = new IncrementalGoalField<>(new Graph(arcs, Map.of(1, 13)), 4, null); settle(first);
        assertEquals(2, first.next(0)); assertEquals(3, first.distance(0));
        var changed = new IncrementalGoalField<>(new Graph(arcs, Map.of(2, 20)), 4, first.image()); settle(changed);
        assertEquals(1, changed.next(0)); assertEquals(2, changed.distance(0));
        assertEquals(2, first.next(0), "another policy cannot mutate a retained field");
    }
    @Test void incrementalDirectedInsertionsRemovalsAndDisconnectedCyclesMatchIndependentBfs() {
        IncrementalGoalField.Image<Integer> seed = null;
        for (int turn = 0; turn < 40; turn++) {
            var arcs = new LinkedHashMap<Integer, List<Integer>>();
            for (int node = 0; node < 25; node++) {
                if (node != 24 && (node + turn * 3) % 11 == 0) continue;
                var adjacent = new ArrayList<Integer>();
                for (int other = 0; other < 25; other++) if (other == 24 || (other + turn * 3) % 11 != 0) {
                    if (Math.abs(node / 5 - other / 5) + Math.abs(node % 5 - other % 5) == 1
                            && (node * 7 + other * 3 + turn) % 5 != 0) adjacent.add(other);
                }
                arcs.put(node, List.copyOf(adjacent));
            }
            var graph = new Graph(arcs); var field = new IncrementalGoalField<>(graph, 24, seed);
            settle(field); var expected = bfs(graph, 24);
            assertEquals(expected, field.image().distances(), "changed directed graph " + turn);
            for (var node : graph.nodes()) if (expected.containsKey(node) && node != 24)
                assertEquals(expected.get(node) - 1, expected.get(field.next(node)));
            if (seed != null) assertEquals(bfs((Graph) seed.graph(), 24), seed.distances(), "repair must not mutate another captured image");
            seed = field.image();
        }
    }
    @Test void unchangedLabelsAreReusedAndMissingGoalNeverCreatesReachability() {
        var graph = new Graph(Map.of(0, List.of(1), 1, List.of(2), 2, List.of()));
        var first = new IncrementalGoalField<>(graph, 2, null); settle(first);
        var unchanged = new IncrementalGoalField<>(graph, 2, first.image()); settle(unchanged);
        assertEquals(3, unchanged.reusedLabels());
        var removed = new Graph(Map.of(0, List.of(1), 1, List.of()));
        var second = new IncrementalGoalField<>(removed, 2, first.image());
        assertThrows(IllegalStateException.class, second::image); settle(second);
        assertTrue(second.image().distances().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new IncrementalGoalField<>(graph, 1, first.image()));
    }
    @Test void aPartialSettledImageCanGrowAndRepairWithoutDeclaringItsUnvisitedVerticesUnreachable() {
        var old = new Graph(Map.of(0, List.of(1), 1, List.of(0, 2), 2, List.of(1, 3),
                3, List.of(2, 4), 4, List.of(3, 5), 5, List.of(4)));
        var field = new IncrementalGoalField<>(old, 5, null);
        for (int turn = 0; turn < 100 && !field.settled(4); turn++) field.advanceUntil(4, 1, () -> true);
        var partial = field.snapshot(); assertFalse(partial.complete());
        assertEquals(Map.of(4, 1, 5, 0), partial.distances());
        var changed = new Graph(Map.of(0, List.of(1), 1, List.of(0, 2), 2, List.of(1, 4), 4, List.of(2, 5), 5, List.of(4)));
        var repaired = new IncrementalGoalField<>(changed, 5, partial); settle(repaired);
        assertEquals(bfs(changed, 5), repaired.image().distances());
        assertEquals(Map.of(4, 1, 5, 0), partial.distances());
    }
    private static void settle(IncrementalGoalField<?> field) {
        for (int turn = 0; turn < 100_000 && !field.finished(); turn++) {
            int spent = field.advance(1, () -> true); assertEquals(1, spent, "every positive one-step slice must progress within its bound");
        }
        assertTrue(field.finished(), "bounded fixture must converge");
    }
    private static Map<Integer, Integer> bfs(Graph graph, int goal) {
        var distances = new HashMap<Integer, Integer>(); var queue = new ArrayDeque<Integer>();
        if (graph.contains(goal)) { distances.put(goal, 0); queue.add(goal); }
        while (!queue.isEmpty()) {
            int node = queue.remove();
            for (int from : graph.predecessors(node)) if (!distances.containsKey(from)) {
                distances.put(from, distances.get(node) + 1); queue.add(from);
            }
        }
        return distances;
    }
}
