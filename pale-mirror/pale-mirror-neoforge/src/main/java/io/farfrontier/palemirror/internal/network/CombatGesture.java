package io.farfrontier.palemirror.internal.network;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
/** Presentation vocabulary; movement cues can never become weapon attack techniques. */
public enum CombatGesture {
    QUICK(1),LUNGE(2),BURST(3),BACKSTEP(4);
    private final int tag;CombatGesture(int tag){this.tag=tag;}public int wireTag(){return tag;}
    public static CombatGesture require(int tag){for(var k:values())if(k.tag==tag)return k;throw new IllegalArgumentException("unknown combat gesture");}
    public static CombatGesture strike(MeleeAttack attack){return switch(attack){case QUICK->QUICK;case LUNGE->LUNGE;};}
    public static CombatGesture maneuver(MeleeManeuver.Kind kind){return switch(kind){case BURST->BURST;case BACKSTEP->BACKSTEP;};}
}
