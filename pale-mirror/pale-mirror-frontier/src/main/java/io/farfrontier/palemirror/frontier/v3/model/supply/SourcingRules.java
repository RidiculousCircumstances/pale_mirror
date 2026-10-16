package io.farfrontier.palemirror.frontier.v3.model.supply;

/** Hashed planning estimates, not wages, transfers or a promise of route availability. */
public record SourcingRules(long labourValuePerTick, int switchingSavingsPermille, int provisionalDistancePermille) {
    public SourcingRules {
        if (labourValuePerTick < 1 || switchingSavingsPermille < 0 || switchingSavingsPermille >= 1000
                || provisionalDistancePermille < 1000 || provisionalDistancePermille > 10000)
            throw new IllegalArgumentException("invalid economic sourcing rules");
    }
    public String canonicalText() { return labourValuePerTick + ":" + switchingSavingsPermille + ":" + provisionalDistancePermille; }
}
