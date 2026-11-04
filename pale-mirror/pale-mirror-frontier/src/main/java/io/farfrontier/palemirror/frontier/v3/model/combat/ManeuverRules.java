package io.farfrontier.palemirror.frontier.v3.model.combat;
/** World-hashed movement intentions. Geometry and locomotion still belong to navigation. */
public record ManeuverRules(int preparation,int travelTicks,int recovery,int burstCooldown,int retreatCooldown,
        int sharedCooldown,int harmLifetime,int burstDistance,int retreatDistance,int burstPacePermille,int retreatPacePermille,
        int minimumBurstGapMicros,int maximumBurstGapMicros,int distantGapMicros,int retreatSpeedMicros,int crowdedGapMicros,int hurtGapMicros) {
    public ManeuverRules {
        if(minimumBurstGapMicros<1 || maximumBurstGapMicros<minimumBurstGapMicros || maximumBurstGapMicros>12_000_000
                || distantGapMicros<minimumBurstGapMicros || distantGapMicros>maximumBurstGapMicros || retreatSpeedMicros<0 || retreatSpeedMicros>1_000_000
                || crowdedGapMicros<1 || crowdedGapMicros>minimumBurstGapMicros || hurtGapMicros<crowdedGapMicros || hurtGapMicros>maximumBurstGapMicros
                || preparation<1 || preparation>20 || travelTicks<1 || travelTicks>60 || recovery<1 || recovery>40
                || sharedCooldown<recovery || burstCooldown<sharedCooldown || retreatCooldown<sharedCooldown
                || burstCooldown>1200 || retreatCooldown>1200 || harmLifetime<1 || harmLifetime>=retreatCooldown
                || burstDistance<1 || burstDistance>3 || retreatDistance<1 || retreatDistance>2
                || burstPacePermille<1000 || burstPacePermille>3000 || retreatPacePermille<1000 || retreatPacePermille>2500)
            throw new IllegalArgumentException("invalid melee maneuver policy");
    }
    public static ManeuverRules initial(){return new ManeuverRules(4,20,6,100,60,40,30,3,1,2200,1500,3_500_000,8_000_000,4_500_000,30_000,1_000_000,4_000_000);}
    public MeleeDuelRules duel(){return MeleeDuelRules.initial();}
    public PursuitRules pursuit(){return PursuitRules.initial();}
    public int preparation(MeleeManeuver.Kind kind){return kind==MeleeManeuver.Kind.PURSUIT_RUN?pursuit().preparation():kind.lateral()?duel().preparation():preparation;}
    public int travelTicks(MeleeManeuver.Kind kind){return kind==MeleeManeuver.Kind.PURSUIT_RUN?pursuit().travelTicks():kind.lateral()?duel().travelTicks():travelTicks;}
    public int recovery(MeleeManeuver.Kind kind){return kind==MeleeManeuver.Kind.PURSUIT_RUN?pursuit().recovery():kind.lateral()?duel().recovery():recovery;}
    public int distance(MeleeManeuver.Kind kind){return kind==MeleeManeuver.Kind.PURSUIT_RUN?pursuit().distance():kind.lateral()?duel().lateralDistance():kind==MeleeManeuver.Kind.BURST?burstDistance:retreatDistance;}
    public int pacePermille(MeleeManeuver.Kind kind){return kind==MeleeManeuver.Kind.PURSUIT_RUN?pursuit().pacePermille():kind.lateral()?duel().pacePermille():kind==MeleeManeuver.Kind.BURST?burstPacePermille:retreatPacePermille;}
    public String canonicalText(){
        return preparation+":"+travelTicks+":"+recovery+":"+burstCooldown+":"+retreatCooldown+":"+sharedCooldown
            +":"+harmLifetime+":"+burstDistance+":"+retreatDistance+":"+burstPacePermille+":"+retreatPacePermille
            +":"+minimumBurstGapMicros+":"+maximumBurstGapMicros+":"+distantGapMicros+":"+retreatSpeedMicros+":"+crowdedGapMicros+":"+hurtGapMicros+"|duel="+duel().canonicalText()+"|pursuit="+pursuit().canonicalText();
    }
}
