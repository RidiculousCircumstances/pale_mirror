package io.farfrontier.palemirror.frontier.v3.kernel;

import java.util.Optional;

/** Measured deterministic work, allowing several causal intervals to share one turn budget. */
public record WorkConsumption(int actions, int weight, int classifications, int executions) {
    public static final WorkConsumption NONE = new WorkConsumption(0, 0, 0, 0);
    public WorkConsumption {
        if (actions < 0 || weight < 0 || classifications < 0 || executions < 0)
            throw new IllegalArgumentException("negative consumed work");
    }
    public Optional<WorkBudget> remaining(WorkBudget budget) {
        int a = budget.maxActions() - actions, w = budget.maxWeight() - weight;
        int c = budget.maxClassificationEvaluations() - classifications, e = budget.maxExecutionEvaluations() - executions;
        if (a < 0 || w < 0 || c < 0 || e < 0) throw new IllegalArgumentException("execution exceeded its turn budget");
        return a == 0 || w == 0 || c == 0 || e == 0 ? Optional.empty()
                : Optional.of(new WorkBudget(a, w, c, e, budget.maxActionWeight()));
    }
}
