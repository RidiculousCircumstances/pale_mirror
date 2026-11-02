package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.frontier.v3.model.combat.MeleeAttack;
import io.farfrontier.palemirror.internal.network.CombatGesturePayload;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MeleeAnimationTest {
    private static PaleMirrorCombatClient.Frame frame(MeleeAttack attack,CombatGesturePayload.Phase phase,float age,int sampled){
        var technique=io.farfrontier.palemirror.frontier.v3.model.combat.MeleeRules.initial().techniques().require(attack);
        var cue=new CombatGesturePayload(1,new UUID(1,2),1,1,attack,phase,100,100+sampled,
                technique.preparationTicks(),technique.activeTicks(),technique.recoveryTicks(),0,0);
        return new PaleMirrorCombatClient.Frame(cue,age);
    }
    @Test void techniquesHaveDistinctPreparationAndBothReturnToTheirReadyPose(){
        var quick=MeleeAnimation.sample(frame(MeleeAttack.QUICK,CombatGesturePayload.Phase.PREPARING,3,0));
        var lunge=MeleeAnimation.sample(frame(MeleeAttack.LUNGE,CombatGesturePayload.Phase.PREPARING,7.5f,0));
        assertTrue(quick.shoulder()<-1.5f);assertTrue(lunge.elbow()<-1.8f);assertTrue(lunge.lean()<-0.1f);
        for(var attack:MeleeAttack.values()){
            var technique=io.farfrontier.palemirror.frontier.v3.model.combat.MeleeRules.initial().techniques().require(attack);
            var ready=MeleeAnimation.sample(frame(attack,CombatGesturePayload.Phase.PREPARING,0,0));
            var ended=MeleeAnimation.sample(frame(attack,CombatGesturePayload.Phase.IMPACT,
                    technique.preparationTicks()+technique.activeTicks()+technique.recoveryTicks(),0));
            assertEquals(ready,ended);
        }
    }
    @Test void cancelledPreparationRetractsInsteadOfPlayingTheForwardStrike(){
        var atCancel=MeleeAnimation.sample(frame(MeleeAttack.LUNGE,CombatGesturePayload.Phase.CANCELLED,5,5));
        var before=MeleeAnimation.sample(frame(MeleeAttack.LUNGE,CombatGesturePayload.Phase.PREPARING,5,0));
        assertEquals(before,atCancel);
        var recovered=MeleeAnimation.sample(frame(MeleeAttack.LUNGE,CombatGesturePayload.Phase.CANCELLED,15,5));
        assertEquals(MeleeAnimation.sample(frame(MeleeAttack.LUNGE,CombatGesturePayload.Phase.PREPARING,0,0)),recovered);
    }
}
