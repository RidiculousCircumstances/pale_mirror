package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Explicit stable technique identity. World combat rules own all tunable mechanics. */
public enum MeleeAttack {
    QUICK(1), LUNGE(2);
    private final int tag;
    MeleeAttack(int tag){this.tag=tag;}
    public int wireTag(){return tag;}
    public static MeleeAttack require(int tag){
        for(var value:values())if(value.tag==tag)return value;
        throw new IllegalArgumentException("unknown melee attack tag: "+tag);
    }
}
