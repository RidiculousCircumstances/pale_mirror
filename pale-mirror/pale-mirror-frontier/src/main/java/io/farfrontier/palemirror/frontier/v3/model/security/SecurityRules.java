package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;

/** Hashed world policy; safety bounds remain separate from tunable security behaviour. */
public record SecurityRules(int observationRadius, int pursuitMargin, int homePursuitLimit,
                            long watchTicks, long contactLifetime, long coverageCycleTicks,
                            int observationInterval, long contactRefreshTicks, long investigationTicks,
                            int responsePercent, int respondersPerThreat, int searchProbes, int searchRadius, long searchPauseTicks, long harmMemoryTicks, int reassignmentAdvantage) {
    public SecurityRules {
        if (harmMemoryTicks<1 || harmMemoryTicks>1200 || reassignmentAdvantage<1 || reassignmentAdvantage>128
                || responsePercent<1 || responsePercent>100 || respondersPerThreat<1 || respondersPerThreat>8
                || searchProbes<1 || searchProbes>8 || searchRadius<1 || searchRadius>8 || searchPauseTicks<1
                || observationRadius < 1 || observationRadius > 128 || pursuitMargin < 1 || pursuitMargin > 1024
                || homePursuitLimit < pursuitMargin || homePursuitLimit > 4096 || watchTicks < 1
                || contactLifetime < 1 || coverageCycleTicks < 1 || observationInterval < 1
                || investigationTicks < contactLifetime || investigationTicks > 1200
                || contactRefreshTicks < 1 || contactRefreshTicks > contactLifetime)
            throw new IllegalArgumentException("invalid security policy");
    }
    public static SecurityRules initial() { return new SecurityRules(24,64,256,160,60,1200,10,20,400,50,2,4,4,40,100,8); }
    public CombatReactionPolicy.Rules reactionRules() { return new CombatReactionPolicy.Rules(observationRadius,contactLifetime,harmMemoryTicks,reassignmentAdvantage); }
    public String canonicalText() {
        return observationRadius+":"+pursuitMargin+":"+homePursuitLimit+":"+watchTicks+":"+contactLifetime
                +":"+coverageCycleTicks+":"+observationInterval+":"+contactRefreshTicks+":"+investigationTicks
                +":"+responsePercent+":"+respondersPerThreat+":"+searchProbes+":"+searchRadius+":"+searchPauseTicks+":"+harmMemoryTicks+":"+reassignmentAdvantage;
    }
}
