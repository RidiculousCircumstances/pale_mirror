package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Complete deterministic recipe matching. Overlapping ingredient alternatives share actual supply. */
public record ProductionMaterialAllocation(Map<ProductionRecipe.InputId, Map<String, Integer>> inputs) {
    public ProductionMaterialAllocation {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 9) throw new IllegalArgumentException("invalid material requirement allocation");
        var copy = new TreeMap<ProductionRecipe.InputId, Map<String, Integer>>();
        inputs.forEach((id, quantities) -> {
            Objects.requireNonNull(id);
            if (quantities.isEmpty() || quantities.size() > 64 || quantities.values().stream().anyMatch(quantity -> quantity < 1)
                    || quantities.values().stream().mapToLong(Integer::longValue).sum() > 64)
                throw new IllegalArgumentException("material allocation needs positive input portions");
            quantities.keySet().forEach(ProductionRecipe::requireItemKey);
            copy.put(id, Map.copyOf(quantities));
        });
        if (copy.values().stream().flatMap(quantities -> quantities.values().stream()).mapToLong(Integer::longValue).sum() > 64)
            throw new IllegalArgumentException("material allocation exceeds one bounded operation");
        inputs = Collections.unmodifiableMap(copy);
    }
    public Map<String, Integer> quantities() {
        var result = new TreeMap<String, Integer>();
        inputs.values().forEach(group -> group.forEach((item, quantity) -> result.merge(item, quantity, Math::addExact)));
        return Map.copyOf(result);
    }
    static Optional<ProductionMaterialAllocation> select(ProductionRecipe recipe, Map<String, Integer> supply, int batches) {
        Objects.requireNonNull(recipe); Objects.requireNonNull(supply);
        if (batches < 1 || batches > 64 || !(recipe.outcome() instanceof ProductionRecipeOutcome.Fungible) && batches != 1 || (long) batches * recipe.inputUnitsPerBatch() > 64
                || (long) batches * recipe.product().quantity() > 64
                || supply.values().stream().anyMatch(quantity -> quantity < 0))
            throw new IllegalArgumentException("invalid bounded recipe allocation request");
        var requirements = recipe.ingredients().stream().sorted(Comparator.comparing(ProductionRecipe.Ingredient::id)).toList();
        var items = supply.keySet().stream().filter(recipe::acceptsInput).sorted().toList();
        int sink = 1 + items.size() + requirements.size();
        var graph = new ArrayList<List<Edge>>(sink + 1);
        for (int i = 0; i <= sink; i++) graph.add(new ArrayList<>());
        for (int i = 0; i < items.size(); i++) add(graph, 0, 1 + i, Math.min(64, supply.get(items.get(i))));
        var matches = new Edge[items.size()][requirements.size()];
        for (int r = 0; r < requirements.size(); r++) {
            var requirement = requirements.get(r);
            int node = 1 + items.size() + r;
            add(graph, node, sink, Math.multiplyExact(batches, requirement.quantity()));
            for (int i = 0; i < items.size(); i++) if (requirement.accepts(items.get(i)))
                matches[i][r] = add(graph, 1 + i, node, 64);
        }
        int required = Math.multiplyExact(batches, recipe.inputUnitsPerBatch()), flow = 0;
        while (flow < required) {
            var parent = new Edge[sink + 1]; var previous = new int[sink + 1]; Arrays.fill(previous, -1);
            var pending = new ArrayDeque<Integer>(); pending.add(0); previous[0] = 0;
            while (!pending.isEmpty() && previous[sink] < 0) {
                int node = pending.remove();
                for (var edge : graph.get(node)) if (edge.capacity > 0 && previous[edge.to] < 0) {
                    previous[edge.to] = node; parent[edge.to] = edge; pending.add(edge.to);
                }
            }
            if (previous[sink] < 0) return Optional.empty();
            int amount = required - flow;
            for (int at = sink; at != 0; at = previous[at]) amount = Math.min(amount, parent[at].capacity);
            for (int at = sink; at != 0; at = previous[at]) {
                var edge = parent[at]; edge.capacity -= amount; edge.reverse.capacity += amount;
            }
            flow += amount;
        }
        var allocated = new TreeMap<ProductionRecipe.InputId, Map<String, Integer>>();
        for (int r = 0; r < requirements.size(); r++) {
            var quantities = new TreeMap<String, Integer>();
            for (int i = 0; i < items.size(); i++) if (matches[i][r] != null && matches[i][r].reverse.capacity > 0)
                quantities.put(items.get(i), matches[i][r].reverse.capacity);
            allocated.put(requirements.get(r).id(), quantities);
        }
        return Optional.of(new ProductionMaterialAllocation(allocated));
    }
    private static Edge add(List<List<Edge>> graph, int from, int to, int capacity) {
        var edge = new Edge(to, capacity); var reverse = new Edge(from, 0);
        edge.reverse = reverse; reverse.reverse = edge;
        graph.get(from).add(edge); graph.get(to).add(reverse); return edge;
    }
    private static final class Edge {
        final int to; int capacity; Edge reverse;
        Edge(int to, int capacity) { this.to = to; this.capacity = capacity; }
    }
}
