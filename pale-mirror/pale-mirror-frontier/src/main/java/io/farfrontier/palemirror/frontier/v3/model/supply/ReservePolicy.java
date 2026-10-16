package io.farfrontier.palemirror.frontier.v3.model.supply;

/** Behavioural policy, never a per-resource stock quantity. Persisted through the ruleset digest. */
public record ReservePolicy(long reviewTicks, int safetyPermille) {
    public ReservePolicy {
        if (reviewTicks < 1 || safetyPermille < 0 || safetyPermille > 10_000)
            throw new IllegalArgumentException("invalid reserve coverage policy");
    }
    public String canonicalText() { return reviewTicks + ":" + safetyPermille; }
}
