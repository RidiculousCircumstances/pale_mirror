package io.farfrontier.palemirror.frontier.v3.model.combat;

/** Finite tactical running and bounded prediction from already observed target motion. */
public record PursuitRules(int distance,int preparation,int travelTicks,int recovery,int cooldown,
        int sharedCooldown,int pacePermille,int leadTicks,int maximumLeadMicros,int maximumVelocityMicros) {
    public PursuitRules {
        if(distance<2 || distance>8 || preparation<1 || preparation>10 || travelTicks<1 || travelTicks>40
                || recovery<1 || recovery>sharedCooldown || sharedCooldown<1 || cooldown<travelTicks+sharedCooldown
                || cooldown>400 || pacePermille<1000 || pacePermille>3000 || leadTicks<1 || leadTicks>12
                || maximumLeadMicros<1 || maximumLeadMicros>3_000_000 || maximumVelocityMicros<1 || maximumVelocityMicros>1_000_000)
            throw new IllegalArgumentException("invalid tactical pursuit rules");
    }
    public static PursuitRules initial(){return new PursuitRules(6,1,30,2,70,12,3000,6,2_000_000,350_000);}
    public String canonicalText(){return distance+":"+preparation+":"+travelTicks+":"+recovery+":"+cooldown+":"+sharedCooldown
            +":"+pacePermille+":"+leadTicks+":"+maximumLeadMicros+":"+maximumVelocityMicros;}
}
