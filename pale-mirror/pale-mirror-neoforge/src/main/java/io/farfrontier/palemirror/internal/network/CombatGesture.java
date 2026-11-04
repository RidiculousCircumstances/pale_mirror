package io.farfrontier.palemirror.internal.network;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
/** Closed presentation vocabulary; movement/defence cues never become weapon effects. */
public enum CombatGesture {
    QUICK(1), LUNGE(2), FOLLOWUP(6), SWEEP(5), BASIC_CUT(7), OVERHEAD(8), SIDE_CUT(9), KICK(10), RISING(11), ADVANCE(12), LATERAL(13), RETREAT(14), DOUBLE(15), COUNTER(16), CIRCLE(17), FLANK(18), FEINT(19),
            LUNGE_CHAIN(20), EVADE_COUNTER(21), DIRECTION_CHAIN(22), DOUBLE_SECOND(23), LUNGE_SECOND(24), DIRECTION_SECOND(25), BURST(3), BACKSTEP(4), BLOCK(26), SIDESTEP(27), DIAGONAL(28), EVADE_ADVANCE(29);
    private final int tag;CombatGesture(int tag){this.tag=tag;}public int wireTag(){return tag;}
    public int maximumActiveTicks(){return switch(this){case BURST,BACKSTEP,BLOCK,SIDESTEP,DIAGONAL,EVADE_ADVANCE->60;default->20;};}
    public static CombatGesture require(int tag){for(var k:values())if(k.tag==tag)return k;throw new IllegalArgumentException("unknown combat gesture");}
    public static CombatGesture strike(MeleeAttack attack){return switch(attack){
        case QUICK->QUICK;
        case LUNGE->LUNGE;
        case FOLLOWUP->FOLLOWUP;
        case SWEEP->SWEEP;
        case BASIC_CUT->BASIC_CUT;
        case OVERHEAD->OVERHEAD;
        case SIDE_CUT->SIDE_CUT;
        case KICK->KICK;
        case RISING->RISING;
        case ADVANCE->ADVANCE;
        case LATERAL->LATERAL;
        case RETREAT->RETREAT;
        case DOUBLE->DOUBLE;
        case COUNTER->COUNTER;
        case CIRCLE->CIRCLE;
        case FLANK->FLANK;
        case FEINT->FEINT;
        case LUNGE_CHAIN->LUNGE_CHAIN;
        case EVADE_COUNTER->EVADE_COUNTER;
        case DIRECTION_CHAIN->DIRECTION_CHAIN;
        case DOUBLE_SECOND->DOUBLE_SECOND;
        case LUNGE_SECOND->LUNGE_SECOND;
        case DIRECTION_SECOND->DIRECTION_SECOND;
    };}
    public static CombatGesture maneuver(MeleeManeuver.Kind kind){return switch(kind){
        case BURST,PURSUIT_RUN->BURST;case BACKSTEP->BACKSTEP;case BLOCK->BLOCK;
        case SIDESTEP_LEFT,SIDESTEP_RIGHT->SIDESTEP;case DIAGONAL_LEFT,DIAGONAL_RIGHT->DIAGONAL;case ADVANCE_LEFT,ADVANCE_RIGHT->EVADE_ADVANCE;
    };}
}
