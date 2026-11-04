package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regressions for the recorded moving-player contact failures, not wall-clock performance tests. */
class CombatContactPressureTest {
    private static final MeleeRules RULES=MeleeRules.initial();
    private static final SurfaceAnchor TARGET=SurfaceAnchor.at(0,63,0);
    private static final WeaponProfile SWORD=new WeaponProfile("minecraft:iron_sword",6,13,3,1);
    private static CombatOrder order(int i){
        return new CombatOrder(new ActorExecutionId(new SubjectId("resident:contact-"+i),ActorActivityKind.COURIER,new SubjectId("shipment:contact-"+i),1),
                1,new SubjectId("item:contact-"+i),new CombatPartyId(new SubjectId("party:contact")),
                new CombatEnvelope(new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,new UUID(1,2),"minecraft:overworld"),CombatEnvelope.Kind.SELF_DEFENCE,TARGET,32),
                TARGET,100,1000,TARGET.offset(4,0,0),UnitNavigationProfile.VILLAGER,Optional.empty(),RULES.styles().require(new CombatStyleId("frontier:pressing")));
    }
    @Test void observedCoordinatesSupplyTheSameVectorToLeadAndRadialDecisions(){
        var motion=CombatMotion.between(10,20,11,22,4);
        assertEquals(.25,motion.x());assertEquals(.5,motion.z());assertEquals(.25,motion.separating(1,0));assertEquals(.5,motion.lateral(1,0));
        var lead=MeleePursuit.lead(new MeleeManeuvers.Facts(6,motion.separating(1,0),false,motion.x(),motion.z()),RULES.maneuvers().pursuit());
        assertTrue(lead.x()>0 && lead.z()>0);assertTrue(Math.hypot(lead.x(),lead.z())<=2.000001);
        assertThrows(IllegalArgumentException.class,()->CombatMotion.between(0,0,1,1,0));
        assertThrows(IllegalArgumentException.class,()->new CombatMotion(Double.NaN,0));
    }
    @Test void combatApproachIsBriskFlankIsFasterAndQuickRetainsBoundedContactGait(){
        var first=order(1);var second=order(2);var combat=CombatState.empty().enlisted(first,100,RULES).enlisted(second,100,RULES);
        var key=CombatEngagement.Key.of(first);
        var pressure=CombatLocomotion.plan(combat,first.execution(),key,first.actorPosition(),RULES,first.style());
        var flank=CombatLocomotion.plan(combat,second.execution(),key,second.actorPosition(),RULES,second.style());
        assertTrue(pressure.multiplierPermille()>1000);assertTrue(flank.multiplierPermille()>pressure.multiplierPermille());
        var body=new ActorActuationId(new ActorBodyId(first.execution().actorId(),1),first.execution());
        var strike=new MeleeStrike(first.owner(),1,body,first.target(),first.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.QUICK,1,101,100);
        combat=combat.prepared(strike);var gait=CombatLocomotion.plan(combat,first.execution(),key,first.actorPosition(),RULES,first.style());
        assertEquals(LocomotionMode.RUN,gait.mode());assertTrue(MeleeAttack.QUICK.guidesApproach());assertFalse(MeleeAttack.QUICK.closesDistance());
        assertFalse(MeleeAttack.SWEEP.guidesApproach());
        assertEquals(MeleeAttack.QUICK,MeleeTactics.attack(combat,first,body,1,100,SWORD,1.8,.2,.3,RULES),"retreat must not invite a slow stationary sweep");
    }
    @Test void reachableButStalledFlankChangesSideAndItsProgressSurvivesRecovery()throws IOException{
        var first=order(1);var second=order(2);var combat=CombatState.empty().enlisted(first,100,RULES)
                .positioned(second,Optional.of(TARGET.offset(0,0,2)),MeleePosition.Reason.RESERVED,100,RULES);
        var plan=combat.engagements().get(CombatEngagement.Key.of(first));var members=new LinkedHashMap<>(plan.participants());
        var orders=Map.of(first.owner(),first,second.owner(),second);var role=members.get(second.owner()).role();
        assertFalse(CombatFormation.reconcile(members,orders,combat,plan.axisX(),plan.axisZ(),100,RULES));
        assertFalse(CombatFormation.reconcile(members,orders,combat,plan.axisX(),plan.axisZ(),159,RULES));
        assertTrue(CombatFormation.reconcile(members,orders,combat,plan.axisX(),plan.axisZ(),160,RULES));
        assertNotEquals(role,members.get(second.owner()).role());assertNotEquals(MeleeSector.Role.SUPPORT,members.get(second.owner()).role());
        combat=combat.withEngagement(new CombatEngagement(plan.key(),plan.revision()+1,plan.anchor(),plan.axisX(),plan.axisZ(),plan.frameSince(),members,0,Optional.empty()));
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),combat);
        assertEquals(combat,CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
    @Test void formationFrameFollowsActualPressureAfterCommitmentButCannotRotateThroughAnOpenSwing(){
        var first=order(1);var plan=CombatEngagement.first(first,100);var changed=first.atActorObservation(TARGET.offset(0,0,4));
        assertFalse(CombatFormation.reframe(plan,changed,CombatState.empty(),119,RULES.engagements()));
        assertTrue(CombatFormation.reframe(plan,changed,CombatState.empty(),120,RULES.engagements()));
        var body=new ActorActuationId(new ActorBodyId(first.execution().actorId(),1),first.execution());
        var pending=CombatState.empty().prepared(new MeleeStrike(first.owner(),1,body,first.target(),first.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.QUICK,1,121,120));
        assertFalse(CombatFormation.reframe(plan,changed,pending,120,RULES.engagements()));
    }
}
