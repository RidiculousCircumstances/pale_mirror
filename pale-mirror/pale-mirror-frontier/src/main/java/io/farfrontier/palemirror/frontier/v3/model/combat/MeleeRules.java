package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Shared bounded tactical policy. No role-specific radius or physical weapon table. */
public record MeleeRules(int sensingInterval,int routeRequestLimit,int maximumRadius,int obstructionTicks) {
    public MeleeRules {if(sensingInterval<1 || routeRequestLimit<1 || routeRequestLimit>32 || maximumRadius<1 || maximumRadius>8
            || obstructionTicks<1 || obstructionTicks>200)throw new IllegalArgumentException("invalid melee policy");}
    public static MeleeRules initial(){return new MeleeRules(10,8,8,40);}
    public String canonicalText(){return sensingInterval+":"+routeRequestLimit+":"+maximumRadius+":"+obstructionTicks;}
}
