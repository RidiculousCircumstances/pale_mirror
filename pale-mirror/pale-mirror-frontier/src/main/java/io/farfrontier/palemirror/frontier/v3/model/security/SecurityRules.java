package io.farfrontier.palemirror.frontier.v3.model.security;

/** Hashed world policy; safety bounds remain separate from tunable security behaviour. */
public record SecurityRules(int observationRadius, int pursuitMargin, int homePursuitLimit,
                            long watchTicks, long contactLifetime, long coverageCycleTicks,
                            int observationInterval, long contactRefreshTicks, long investigationTicks) {
    public SecurityRules {
        if (observationRadius < 1 || observationRadius > 128 || pursuitMargin < 1 || pursuitMargin > 1024
                || homePursuitLimit < pursuitMargin || homePursuitLimit > 4096 || watchTicks < 1
                || contactLifetime < 1 || coverageCycleTicks < 1 || observationInterval < 1
                || investigationTicks < contactLifetime || investigationTicks > 1200
                || contactRefreshTicks < 1 || contactRefreshTicks > contactLifetime)
            throw new IllegalArgumentException("invalid security policy");
    }
    public static SecurityRules initial() { return new SecurityRules(24,64,256,160,60,1200,10,20,400); }
    public String canonicalText() {
        return observationRadius+":"+pursuitMargin+":"+homePursuitLimit+":"+watchTicks+":"+contactLifetime
                +":"+coverageCycleTicks+":"+observationInterval+":"+contactRefreshTicks+":"+investigationTicks;
    }
}
