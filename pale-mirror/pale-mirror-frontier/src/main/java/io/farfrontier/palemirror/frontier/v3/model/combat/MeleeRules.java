package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Hashed world tactical policy; physical reach and readiness come from the actual weapon. */
public record MeleeRules(int sensingInterval,int routeRequestLimit,int maximumRadius,int obstructionTicks,
        int windupTicks,int rangeHysteresisMicros,int positionLeaseTicks,int unreachableTicks,int retryTicks,int sectorWeightPermille,int alignmentPermille) {
    public MeleeRules(int sensingInterval,int routeRequestLimit,int maximumRadius,int obstructionTicks){
        this(sensingInterval,routeRequestLimit,maximumRadius,obstructionTicks,8,350_000,80,100,100);
    }
    public MeleeRules(int sensingInterval,int routeRequestLimit,int maximumRadius,int obstructionTicks,int windupTicks,
            int rangeHysteresisMicros,int positionLeaseTicks,int unreachableTicks,int retryTicks){
        this(sensingInterval,routeRequestLimit,maximumRadius,obstructionTicks,windupTicks,rangeHysteresisMicros,positionLeaseTicks,unreachableTicks,retryTicks,4000,500);
    }
    public MeleeRules {if(sensingInterval<1 || routeRequestLimit<1 || routeRequestLimit>32 || maximumRadius<1 || maximumRadius>8
            || obstructionTicks<1 || obstructionTicks>200 || windupTicks<1 || windupTicks>40 || rangeHysteresisMicros<1
            || rangeHysteresisMicros>1_000_000 || positionLeaseTicks<3*sensingInterval || positionLeaseTicks>1200
            || unreachableTicks<1 || retryTicks<1 || sectorWeightPermille<0 || sectorWeightPermille>20000 || alignmentPermille<0 || alignmentPermille>1000)throw new IllegalArgumentException("invalid melee policy");}
    public static MeleeRules initial(){return new MeleeRules(10,8,8,40);}
    public long windup(WeaponProfile profile){return Math.min(windupTicks,Math.max(1,profile.attackIntervalTicks()-1));}
    public double holdReach(WeaponProfile profile,boolean alreadyHolding){
        return alreadyHolding?profile.reach():Math.max(0.1,profile.reach()-rangeHysteresisMicros/1_000_000.0);
    }
    public String canonicalText(){return sensingInterval+":"+routeRequestLimit+":"+maximumRadius+":"+obstructionTicks+":"
            +windupTicks+":"+rangeHysteresisMicros+":"+positionLeaseTicks+":"+unreachableTicks+":"+retryTicks+":"+sectorWeightPermille+":"+alignmentPermille;}
}
