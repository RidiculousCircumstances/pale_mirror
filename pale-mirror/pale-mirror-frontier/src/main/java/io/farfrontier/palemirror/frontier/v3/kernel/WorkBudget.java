package io.farfrontier.palemirror.frontier.v3.kernel;

/** Deterministic per-tick work limit; time measurement never decides canonical order. */
public record WorkBudget(int maxActions, int maxWeight) {
    /** Separate calculation allowance: selection + commit checks for admitted
     * actions and a bounded allowance for refusals. Holds never spend an
     * independent action's execution slot. */
    public int maxAdmissionEvaluations() {
        return (int) Math.min(Integer.MAX_VALUE, 2L * ((long) maxActions + maxWeight));
    }
    public int maxExecutionEvaluations() { return Math.min(maxActions, maxAdmissionEvaluations() / 2); }
    public int maxClassificationEvaluations() { return maxAdmissionEvaluations() - maxExecutionEvaluations(); }
    public WorkBudget {
        if (maxActions <= 0) {
            throw new IllegalArgumentException("max actions must be positive");
        }
        if (maxWeight <= 0) {
            throw new IllegalArgumentException("max weight must be positive");
        }
    }
}
