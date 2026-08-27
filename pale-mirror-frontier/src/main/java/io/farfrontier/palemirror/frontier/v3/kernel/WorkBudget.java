package io.farfrontier.palemirror.frontier.v3.kernel;

/** Deterministic per-tick work limit; time measurement never decides canonical order. */
public record WorkBudget(int maxActions, int maxWeight) {
    public WorkBudget {
        if (maxActions <= 0) {
            throw new IllegalArgumentException("max actions must be positive");
        }
        if (maxWeight <= 0) {
            throw new IllegalArgumentException("max weight must be positive");
        }
    }
}
