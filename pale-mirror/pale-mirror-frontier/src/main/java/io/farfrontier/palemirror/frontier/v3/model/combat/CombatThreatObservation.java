package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Dated observed swing, not target intent. -1 explicitly denotes absent knowledge. */
public record CombatThreatObservation(long swingAt,long missedAt,int opponents) {
    public CombatThreatObservation {if(swingAt< -1 || missedAt< -1 || opponents<0 || opponents>8)throw new IllegalArgumentException("invalid observed combat threat");}
    public static CombatThreatObservation none(){return new CombatThreatObservation(-1,-1,0);}
    public boolean reacts(CombatProfile profile,long tick){return swingAt>=0 && tick>=swingAt+CombatMechanics.reactionTicks(profile) && tick-swingAt<=12;}
    public boolean counter(long tick,MeleeRules rules){return missedAt>=0 && tick>=missedAt && tick-missedAt<=rules.training().number("observation.counterTicks");}
}
