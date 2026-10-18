package io.farfrontier.palemirror.frontier.v3.kernel;

/** Deterministic per-tick work limit; time measurement never decides canonical order. */
public record WorkBudget(int maxActions, int maxWeight, int maxClassificationEvaluations, int maxExecutionEvaluations,
                         int maxActionWeight) {
    public WorkBudget(int maxActions, int maxWeight) {
        this(maxActions, maxWeight, allowance(maxActions, maxWeight) - maxActions, maxActions, maxWeight);
    }
    private static int allowance(int actions, int weight) {
        return (int) Math.min(Integer.MAX_VALUE, 2L * ((long) actions + weight));
    }
    /** Separate calculation allowance: selection + commit checks for admitted
     * actions and a bounded allowance for refusals. Holds never spend an
     * independent action's execution slot. */
    public int maxAdmissionEvaluations() {
        return Math.addExact(maxClassificationEvaluations, maxExecutionEvaluations);
    }
    public WorkBudget {
        if (maxActions <= 0) {
            throw new IllegalArgumentException("max actions must be positive");
        }
        if (maxWeight <= 0 || maxActionWeight < maxWeight) {
            throw new IllegalArgumentException("max weight must be positive");
        }
        if (maxClassificationEvaluations < 0 || maxExecutionEvaluations < 0
                || (long) maxClassificationEvaluations + maxExecutionEvaluations > Integer.MAX_VALUE)
            throw new IllegalArgumentException("invalid remaining assessment budget");
    }
}
