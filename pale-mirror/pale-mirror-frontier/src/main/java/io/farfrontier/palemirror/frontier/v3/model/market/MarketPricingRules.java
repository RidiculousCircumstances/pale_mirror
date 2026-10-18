package io.farfrontier.palemirror.frontier.v3.model.market;

/** Persisted policy coefficients; disabled profiles retain their explicitly selected fixed limits. */
public record MarketPricingRules(boolean enabled, long reviewTicks, long recencyTicks, int stepPermille,
                                  int minimumSeedPermille, int maximumSeedPermille) {
    public MarketPricingRules {
        if (reviewTicks < 1 || recencyTicks < reviewTicks || stepPermille < 1 || stepPermille > 1000
                || !(minimumSeedPermille == 0 && maximumSeedPermille == 0)
                    && (minimumSeedPermille < 1 || minimumSeedPermille > 1000 || maximumSeedPermille < 1000
                        || maximumSeedPermille > 100000))
            throw new IllegalArgumentException("invalid market pricing rules");
    }
    public static MarketPricingRules disabled() { return new MarketPricingRules(false, 2400, 24000, 50, 250, 4000); }
    public static MarketPricingRules initial() { return new MarketPricingRules(true, 2400, 24000, 50, 250, 4000); }
    /** Explicit new-profile policy: seeds initialize quotes but never constrain later exchange. */
    public static MarketPricingRules adaptive() { return new MarketPricingRules(true, 2400, 24000, 50, 0, 0); }
    public boolean seedBounded() { return minimumSeedPermille > 0; }
    public String canonicalText() {
        return enabled + ":" + reviewTicks + ":" + recencyTicks + ":" + stepPermille + ":"
                + minimumSeedPermille + ":" + maximumSeedPermille;
    }
}
