package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Hashed world tactical policy; physical reach and readiness come from the actual weapon. */
public record MeleeRules(int sensingInterval,int routeRequestLimit,int maximumRadius,int obstructionTicks,
        int windupTicks,int rangeHysteresisMicros,int positionLeaseTicks,int unreachableTicks,int retryTicks,int sectorWeightPermille,int alignmentPermille,MeleeTechniques techniques,ManeuverRules maneuvers) {
    public MeleeRules(int sensingInterval,int routeRequestLimit,int maximumRadius,int obstructionTicks){
        this(sensingInterval,routeRequestLimit,maximumRadius,obstructionTicks,8,350_000,80,100,100);
    }
    public MeleeRules(int sensingInterval,int routeRequestLimit,int maximumRadius,int obstructionTicks,int windupTicks,
            int rangeHysteresisMicros,int positionLeaseTicks,int unreachableTicks,int retryTicks){
        this(sensingInterval,routeRequestLimit,maximumRadius,obstructionTicks,windupTicks,rangeHysteresisMicros,positionLeaseTicks,unreachableTicks,retryTicks,4000,500);
    }
    public MeleeRules(int sensingInterval,int routeRequestLimit,int maximumRadius,int obstructionTicks,int windupTicks,
            int rangeHysteresisMicros,int positionLeaseTicks,int unreachableTicks,int retryTicks,int sectorWeightPermille,int alignmentPermille){
        this(sensingInterval,routeRequestLimit,maximumRadius,obstructionTicks,windupTicks,rangeHysteresisMicros,positionLeaseTicks,
                unreachableTicks,retryTicks,sectorWeightPermille,alignmentPermille,MeleeTechniques.initial());
    }
    public MeleeRules(int sensingInterval,int routeRequestLimit,int maximumRadius,int obstructionTicks,int windupTicks,
            int rangeHysteresisMicros,int positionLeaseTicks,int unreachableTicks,int retryTicks,int sectorWeightPermille,int alignmentPermille,MeleeTechniques techniques){
        this(sensingInterval,routeRequestLimit,maximumRadius,obstructionTicks,windupTicks,rangeHysteresisMicros,positionLeaseTicks,unreachableTicks,retryTicks,sectorWeightPermille,alignmentPermille,techniques,ManeuverRules.initial());
    }
    public MeleeRules {java.util.Objects.requireNonNull(maneuvers);java.util.Objects.requireNonNull(techniques);if(sensingInterval<1 || routeRequestLimit<1 || routeRequestLimit>32 || maximumRadius<1 || maximumRadius>8
            || obstructionTicks<1 || obstructionTicks>200 || windupTicks<1 || windupTicks>40 || rangeHysteresisMicros<1
            || rangeHysteresisMicros>1_000_000 || positionLeaseTicks<3*sensingInterval || positionLeaseTicks>1200
            || unreachableTicks<1 || retryTicks<1 || sectorWeightPermille<0 || sectorWeightPermille>20000 || alignmentPermille<0 || alignmentPermille>1000)throw new IllegalArgumentException("invalid melee policy");}
    public static MeleeRules initial(){return new MeleeRules(4,8,8,40);}
    public CombatReadinessRules readiness(){return CombatReadinessRules.initial();}
    public MeleeFollowupRules followup(){return MeleeFollowupRules.initial();}
    public CombatStyleCatalog styles(){return CombatStyleCatalog.standard();}
    public EngagementRules engagements(){return EngagementRules.initial();}
    public long windup(MeleeAttack attack){
        return Math.max(1,Math.min(40,Math.round((double)windupTicks*techniques.require(attack).preparationTicks()/8)));
    }
    public double holdReach(WeaponProfile profile,boolean alreadyHolding,CombatStyle style){
        double close=Math.max(0.1,Math.min(style.preferredGapMicros()/1_000_000.0,profile.reach()-rangeHysteresisMicros/1_000_000.0));
        return alreadyHolding?Math.min(profile.reach(),close+rangeHysteresisMicros/1_000_000.0):close;
    }
    public String canonicalText(){return sensingInterval+":"+routeRequestLimit+":"+maximumRadius+":"+obstructionTicks+":"
            +windupTicks+":"+rangeHysteresisMicros+":"+positionLeaseTicks+":"+unreachableTicks+":"+retryTicks+":"+sectorWeightPermille+":"+alignmentPermille+"|techniques="+techniques.canonicalText()+"|maneuvers="+maneuvers.canonicalText()
            +"|readiness="+readiness().canonicalText()+"|engagements="+engagements().canonicalText()+"|followup="+followup().canonicalText()+"|styles="+styles().canonicalText();}
}
