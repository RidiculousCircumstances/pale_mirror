package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Hashed content policy; distances are blocks, durations are simulation ticks. */
public record RangedRules(int loadSize, int reservePerGuard, int minimumRange,
        int preferredRange, int maximumRange, int reloadTicks, int aimTicks, int recoveryTicks,
        int sensingTicks, int friendlyClearanceMicros, int projectileSpeedMicros,
        int inaccuracyMicros, int trainingVariationPermille) {
    public RangedRules {
        if (loadSize < 1 || loadSize > 64 || reservePerGuard < loadSize
                || reservePerGuard > 1024 || minimumRange < 2 || preferredRange <= minimumRange
                || maximumRange <= preferredRange || maximumRange > 64 || reloadTicks < 1 || aimTicks < 1
                || recoveryTicks < 1 || sensingTicks < 1
                || friendlyClearanceMicros < 0 || friendlyClearanceMicros > 2000000
                || trainingVariationPermille < 0 || trainingVariationPermille > 750
                || projectileSpeedMicros < 1000000 || projectileSpeedMicros > 5000000 || inaccuracyMicros < 0)
            throw new IllegalArgumentException("invalid ranged combat policy");
    }
    public static RangedRules initial() { return new RangedRules(32, 64, 5, 11, 22, 25, 8, 12, 5, 450000, 3150000, 1000000, 500); }
    public String canonicalText() { return loadSize+":"+reservePerGuard+":"+minimumRange+":"+preferredRange+":"+maximumRange
            +":"+reloadTicks+":"+aimTicks+":"+recoveryTicks+":"+sensingTicks+":"+friendlyClearanceMicros
            +":"+projectileSpeedMicros+":"+inaccuracyMicros+":"+trainingVariationPermille+":"+RangedTechnique.canonicalText(); }
}
