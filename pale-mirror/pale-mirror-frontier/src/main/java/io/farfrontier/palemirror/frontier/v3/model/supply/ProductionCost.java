package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Owner-declared unit recipe and labour costs. No dated output, stock or admission authority. */
public record ProductionCost(String id, String product, int outputPerBatch, Map<String, Integer> inputs,
        long burnTicksPerBatch, Map<String, Long> fuels, long labourTicksPerOperation, int recipesPerOperation) {
    public ProductionCost {
        Objects.requireNonNull(id); Objects.requireNonNull(product);
        inputs = Map.copyOf(inputs); fuels = Map.copyOf(fuels);
        if (id.isBlank() || product.isBlank() || outputPerBatch < 1 || recipesPerOperation < 1
                || labourTicksPerOperation < 0 || burnTicksPerBatch < 0
                || inputs.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue() < 1)
                || fuels.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue() < 1)
                || burnTicksPerBatch > 0 && fuels.isEmpty())
            throw new IllegalArgumentException("invalid production cost declaration");
    }
    public static ProductionCost from(SupplyPlanner.Process process) {
        return new ProductionCost(process.id(), process.product(), process.outputPerBatch(), process.inputs(),
                process.burnTicksPerBatch(), process.fuels(), process.labourTicksPerOperation(), process.recipesPerOperation());
    }
}
