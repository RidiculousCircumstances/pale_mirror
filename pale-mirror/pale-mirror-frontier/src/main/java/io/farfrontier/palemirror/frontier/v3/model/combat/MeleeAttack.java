package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Explicit stable identity; the common catalog owns unlocks and mechanics. */
public enum MeleeAttack {
    QUICK(1), LUNGE(2), FOLLOWUP(3), SWEEP(4), BASIC_CUT(5), OVERHEAD(6), SIDE_CUT(7), KICK(8), RISING(9), ADVANCE(10), LATERAL(11), RETREAT(12), DOUBLE(13), COUNTER(14), CIRCLE(15), FLANK(16), FEINT(17),
            LUNGE_CHAIN(18), EVADE_COUNTER(19), DIRECTION_CHAIN(20), DOUBLE_SECOND(21), LUNGE_SECOND(22), DIRECTION_SECOND(23);
    private final int tag;MeleeAttack(int tag){this.tag=tag;}
    public boolean closesDistance(){return this==LUNGE || this==FOLLOWUP || this==ADVANCE || this==LUNGE_CHAIN || this==LUNGE_SECOND;}
    public boolean guidesApproach(){return closesDistance() || this==QUICK || this==BASIC_CUT || this==SIDE_CUT || this==COUNTER || this==LATERAL || this==RETREAT || this==FLANK;}
    public int wireTag(){return tag;}
    public static MeleeAttack require(int tag){for(var value:values())if(value.tag==tag)return value;throw new IllegalArgumentException("unknown melee attack tag: "+tag);}
}
