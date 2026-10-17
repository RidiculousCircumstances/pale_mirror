package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Advisory finite work graph. No reservation, geometry, inventory or execution authority. */
public final class FiniteWorkForecast {
    public record Node(long id, Set<Long> prerequisites, OptionalLong labourTicks, boolean output) {
        public Node {
            prerequisites = Set.copyOf(prerequisites); Objects.requireNonNull(labourTicks);
            if (id < 1 || prerequisites.stream().anyMatch(p -> p < 1 || p >= id)
                    || labourTicks.isPresent() && labourTicks.getAsLong() < 0)
                throw new IllegalArgumentException("invalid finite work forecast node");
        }
    }
    public record Estimate(int outputs, long labourTicks, long firstOutputTicks) { }
    public static Estimate assess(List<Node> nodes) {
        var indexed = new TreeMap<Long, Node>();
        for (var node : nodes) if (indexed.put(node.id(), node) != null)
            throw new IllegalArgumentException("duplicate finite work node");
        var finish = new HashMap<Long, Long>();
        var pending = new ArrayDeque<Long>(); int outputs = 0; long first = Long.MAX_VALUE; long firstId = 0;
        for (var node : indexed.values()) {
            if (!indexed.keySet().containsAll(node.prerequisites()))
                throw new IllegalArgumentException("missing finite work prerequisite");
            if (node.labourTicks().isEmpty() || !finish.keySet().containsAll(node.prerequisites())) continue;
            long before = node.prerequisites().stream().mapToLong(finish::get).max().orElse(0);
            long at = Math.addExact(before, node.labourTicks().getAsLong()); finish.put(node.id(), at);
            if (node.output()) {
                outputs++; pending.add(node.id());
                if (at < first) { first = at; firstId = node.id(); }
            }
        }
        long labour = closureCost(indexed, pending);
        // Critical path selects a candidate, not an assumption of unlimited parallel
        // miners. Charge its entire prerequisite closure once for first output.
        long opening = outputs == 0 ? 0 : closureCost(indexed, new ArrayDeque<>(List.of(firstId)));
        return new Estimate(outputs, labour, opening);
    }
    private static long closureCost(Map<Long, Node> indexed, ArrayDeque<Long> pending) {
        long labour = 0; var required = new HashSet<Long>();
        while (!pending.isEmpty()) {
            long id = pending.removeFirst(); if (!required.add(id)) continue;
            var node = indexed.get(id); labour = Math.addExact(labour, node.labourTicks().orElseThrow());
            pending.addAll(node.prerequisites());
        }
        return labour;
    }
    private FiniteWorkForecast() { }
}
