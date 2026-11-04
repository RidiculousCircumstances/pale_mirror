package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.internal.network.CombatGesture;
import io.farfrontier.palemirror.internal.network.CombatGesturePayload;
import org.junit.jupiter.api.Test;
import io.farfrontier.palemirror.frontier.v3.model.combat.MeleeAttack;
import io.farfrontier.palemirror.frontier.v3.model.combat.MeleeRules;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MeleeAnimationTest {
    private static PaleMirrorCombatClient.Frame frame(CombatGesture attack,CombatGesturePayload.Phase phase,float age,int sampled){
        var technique=MeleeRules.initial().techniques().require(MeleeAttack.valueOf(attack.name()));
        var cue=new CombatGesturePayload(1,new UUID(1,2),1,1,attack,phase,100,100+sampled,
                technique.preparationTicks(),technique.activeTicks(),technique.recoveryTicks(),0,0);
        return new PaleMirrorCombatClient.Frame(cue,age);
    }
    @Test void techniquesHaveDistinctPreparationAndBothReturnToTheirReadyPose(){
        var quick=MeleeAnimation.sample(frame(CombatGesture.QUICK,CombatGesturePayload.Phase.PREPARING,3,0));
        var preparation=MeleeRules.initial().techniques().require(MeleeAttack.LUNGE).preparationTicks();
        var lunge=MeleeAnimation.sample(frame(CombatGesture.LUNGE,CombatGesturePayload.Phase.PREPARING,preparation*0.75f,0));
        assertTrue(quick.shoulder()<-1.5f);assertTrue(lunge.elbow()<-1.8f);assertTrue(lunge.lean()<-0.1f);
        for(var attack:java.util.List.of(CombatGesture.QUICK,CombatGesture.LUNGE,CombatGesture.SWEEP,CombatGesture.FOLLOWUP)){
            var technique=MeleeRules.initial().techniques().require(MeleeAttack.valueOf(attack.name()));
            var ready=MeleeAnimation.sample(frame(attack,CombatGesturePayload.Phase.PREPARING,0,0));
            var ended=MeleeAnimation.sample(frame(attack,CombatGesturePayload.Phase.IMPACT,
                    technique.preparationTicks()+technique.activeTicks()+technique.recoveryTicks(),0));
            assertEquals(ready,ended);
        }
    }
    @Test void sweepAndFollowupHaveSeparateReadableWarnings(){
        var sweep=MeleeAnimation.sample(frame(CombatGesture.SWEEP,CombatGesturePayload.Phase.PREPARING,7.5f,0));
        var follow=MeleeAnimation.sample(frame(CombatGesture.FOLLOWUP,CombatGesturePayload.Phase.PREPARING,4.5f,0));
        var quick=MeleeAnimation.sample(frame(CombatGesture.QUICK,CombatGesturePayload.Phase.PREPARING,3,0));
        assertTrue(sweep.yaw()<-1.4);assertNotEquals(follow,quick);assertNotEquals(sweep,follow);
    }
    @Test void movementGesturesHaveDifferentBodyIntentAndCannotPlayAWeaponStrike(){
        var burst=new CombatGesturePayload(1,new UUID(1,2),1,1,CombatGesture.BURST,CombatGesturePayload.Phase.IMPACT,100,104,4,20,6,0,0);
        var back=new CombatGesturePayload(1,new UUID(1,2),1,1,CombatGesture.BACKSTEP,CombatGesturePayload.Phase.IMPACT,100,104,4,20,6,0,0);
        var forward=MeleeAnimation.sample(new PaleMirrorCombatClient.Frame(burst,7));var retreat=MeleeAnimation.sample(new PaleMirrorCombatClient.Frame(back,7));
        assertTrue(forward.lean()>0.2);assertTrue(retreat.lean()<0);assertNotEquals(forward,retreat);
    }
    @Test void longAttackSweepsAcrossTheBodyDuringContactInsteadOfHoldingAStraightThrust(){
        var technique=MeleeRules.initial().techniques().require(MeleeAttack.LUNGE);
        var drawn=MeleeAnimation.sample(frame(CombatGesture.LUNGE,CombatGesturePayload.Phase.PREPARING,technique.preparationTicks()*0.75f,0));
        var contact=MeleeAnimation.sample(frame(CombatGesture.LUNGE,CombatGesturePayload.Phase.IMPACT,technique.preparationTicks(),technique.preparationTicks()));
        var follow=MeleeAnimation.sample(frame(CombatGesture.LUNGE,CombatGesturePayload.Phase.IMPACT,technique.preparationTicks()+technique.activeTicks(),technique.preparationTicks()));
        assertTrue(drawn.yaw()<-1);assertEquals(0,contact.yaw(),0.01);assertTrue(follow.yaw()>1);
        assertTrue(drawn.torso()<0 && follow.torso()>0,"torso follows the visible cross-body sword arc");
    }
    @Test void cancelledPreparationRetractsInsteadOfPlayingTheForwardStrike(){
        var atCancel=MeleeAnimation.sample(frame(CombatGesture.LUNGE,CombatGesturePayload.Phase.CANCELLED,5,5));
        var before=MeleeAnimation.sample(frame(CombatGesture.LUNGE,CombatGesturePayload.Phase.PREPARING,5,0));
        assertEquals(before,atCancel);
        var recovered=MeleeAnimation.sample(frame(CombatGesture.LUNGE,CombatGesturePayload.Phase.CANCELLED,19,5));
        assertEquals(MeleeAnimation.sample(frame(CombatGesture.LUNGE,CombatGesturePayload.Phase.PREPARING,0,0)),recovered);
    }
}
