package io.farfrontier.palemirror.frontier.v3.model.combat;

/** A cancelled intent is not an observed missed swing. Explicit stable receipt grammar. */
public enum MeleeOutcome {
    HIT(1), MISS(2), CANCELLED(3);
    private final int tag;
    MeleeOutcome(int tag){this.tag=tag;}
    public int wireTag(){return tag;}
    public static MeleeOutcome require(int tag){
        for(var value:values())if(value.tag==tag)return value;
        throw new IllegalArgumentException("unknown melee outcome tag: "+tag);
    }
}
