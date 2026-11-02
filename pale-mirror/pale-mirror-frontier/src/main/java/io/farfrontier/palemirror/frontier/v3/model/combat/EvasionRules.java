package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Hashed safety policy; a threat has dated observed knowledge, never omniscient tracking. */
public record EvasionRules(int reviewTicks,int dangerRadius,int safeRadius,int searchRadius,int routeRequests,long quietTicks) {
    public EvasionRules {
        if(reviewTicks<1 || dangerRadius<1 || safeRadius<=dangerRadius || safeRadius>64
                || searchRadius<1 || searchRadius>32 || routeRequests<1 || routeRequests>16 || quietTicks<reviewTicks || quietTicks>1200)
            throw new IllegalArgumentException("invalid evasion policy");
    }
    public static EvasionRules initial(){return new EvasionRules(10,12,20,16,4,100);}
    public String canonicalText(){return reviewTicks+":"+dangerRadius+":"+safeRadius+":"+searchRadius+":"+routeRequests+":"+quietTicks;}
}
