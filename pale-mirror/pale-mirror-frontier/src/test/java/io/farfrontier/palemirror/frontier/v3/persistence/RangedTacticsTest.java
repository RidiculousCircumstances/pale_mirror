package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.combat.*;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Choices exercise the public permission boundary; no generated live resources or private navigator. */
class RangedTacticsTest {
    private static final SurfaceAnchor ORIGIN=new SurfaceAnchor(new BlockPosition(0,63,0));
    private static final CombatTarget TARGET=new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(3,7),"test:world");
    private static CombatProfile profile(int skill){return new CombatProfile(new CombatAttributes(50,50,50,50,50),
            Map.of(CombatSkillId.CROSSBOW,skill,CombatSkillId.EVASION,0,CombatSkillId.WEAPON_DEFENCE,0));}
    private static CombatOrder order(int skill,String style){
        var execution=new ActorExecutionId(new SubjectId("actor:ranged"),ActorActivityKind.GUARD,new SubjectId("duty:ranged"),1);
        return new CombatOrder(execution,1,new SubjectId("weapon:ranged"),new CombatPartyId(new SubjectId("party:test")),
                new CombatEnvelope(TARGET,CombatEnvelope.Kind.AREA_DEFENCE,ORIGIN,64),ORIGIN.offset(11,0,0),100,100,ORIGIN,UnitNavigationProfile.VILLAGER,Optional.of(ORIGIN),
                CombatStyleCatalog.standard().require(new CombatStyleId("frontier:"+style)),profile(skill),new CombatUse.Crossbow(CombatMethod.CROSSBOW,CombatSkillId.CROSSBOW,"minecraft:arrow"));
    }
    private static RangedStance sight(CombatOrder order,boolean approach,boolean moving,boolean attacking){return new RangedStance(
            new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution()),TARGET,order.targetPosition(),true,true,approach,moving,attacking,-1,100);}
    @Test void eachAcceptedThresholdOpensItsTechniqueAndNeverAnUnlearnedOne(){
        assertArrayEquals(new int[]{0,15,30,45,60,75,90},Arrays.stream(RangedTechnique.values()).mapToInt(RangedTechnique::minimum).toArray());
        for(var move:RangedTechnique.values()){
            assertTrue(move.unlocked(profile(move.minimum())));
            if(move.minimum()>0)assertFalse(move.unlocked(profile(move.minimum()-1)));
            assertEquals(move,RangedTechnique.require(move.tag()));
        }
        assertThrows(IllegalArgumentException.class,()->RangedTechnique.require(255));
        for(int skill=0;skill<=100;skill++)for(var style:List.of("pressing","cautious","mobile","supporting")){
            var o=order(skill,style);assertTrue(RangedTactics.choose(RangedState.empty(),o,sight(o,true,true,true),100,RangedRules.initial()).unlocked(o.profile()));
        }
    }
    @Test void contactPermissionIsALearnedKickAndDoesNotGrantSwordAttacks(){
        assertFalse(order(19,"pressing").permitsStrike(MeleeAttack.KICK));
        var trained=order(20,"pressing");assertTrue(trained.permitsStrike(MeleeAttack.KICK));
        for(var move:MeleeAttack.values())if(move!=MeleeAttack.KICK)assertFalse(trained.permitsStrike(move));
    }
    @Test void theSameEnemyExposesActualStyleAndSpeedAccuracyTradeoffs(){
        var pressing=order(100,"pressing");var cautious=order(100,"cautious");var mobile=order(100,"mobile");
        assertEquals(RangedTechnique.HURRIED,RangedTactics.choose(RangedState.empty(),pressing,sight(pressing,true,true,false),100,RangedRules.initial()));
        assertEquals(RangedTechnique.PRECISE,RangedTactics.choose(RangedState.empty(),cautious,sight(cautious,false,false,false),100,RangedRules.initial()));
        assertEquals(RangedTechnique.REPOSITIONING_SEQUENCE,RangedTactics.choose(RangedState.empty(),mobile,sight(mobile,false,true,false),100,RangedRules.initial()));
        assertEquals(RangedTechnique.HELD,RangedTactics.choose(RangedState.empty(),cautious,sight(cautious,false,true,false),100,RangedRules.initial()));
        assertEquals(RangedTechnique.COUNTERSHOT,RangedTactics.choose(RangedState.empty(),pressing,sight(pressing,false,true,true),100,RangedRules.initial()));
        assertTrue(RangedTactics.preferredRange(cautious,RangedRules.initial())>RangedTactics.preferredRange(pressing,RangedRules.initial()));
        var r=RangedRules.initial();var p=profile(50);
        assertTrue(RangedMechanics.aimTicks(p,r,RangedTechnique.HURRIED)<RangedMechanics.aimTicks(p,r,RangedTechnique.PRECISE));
        assertTrue(RangedMechanics.inaccuracy(p,r,RangedTechnique.HURRIED)>RangedMechanics.inaccuracy(p,r,RangedTechnique.PRECISE));
        assertTrue(RangedMechanics.inaccuracy(profile(100),r,RangedTechnique.PRECISE)>0,"mastery never grants a guaranteed hit");
    }
    @Test void sequenceRequiresRealRetreatThenOrdinaryShotAndCannotLeakAcrossEncounterOrWaitForever()throws IOException{
        var o=order(100,"mobile");var last=new RangedFollowup(o.execution(),o.weapon(),o.target(),ORIGIN,RangedTechnique.REPOSITIONING_SEQUENCE,100);
        var ranged=new RangedState(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(o.owner(),last));
        assertTrue(RangedTactics.relocate(ranged,o,101));
        assertFalse(RangedTactics.relocationStation(last,o,ORIGIN.offset(3,0,0)),"moving toward the enemy is not the declared retreat");
        var retreat=ORIGIN.offset(-3,0,0);assertTrue(RangedTactics.relocationStation(last,o,retreat));
        var arrived=o.atActorObservation(retreat);assertFalse(RangedTactics.relocate(ranged,arrived,105));
        assertEquals(RangedTechnique.ORDINARY,RangedTactics.choose(ranged,arrived,sight(arrived,false,false,false),105,RangedRules.initial()));
        assertFalse(RangedTactics.relocate(ranged,o,181),"an unreachable optional maneuver expires without freezing all later shots");
        var foreign=new RangedFollowup(o.execution(),new SubjectId("weapon:other"),o.target(),ORIGIN,RangedTechnique.REPOSITIONING_SEQUENCE,100);
        assertFalse(RangedTactics.relocate(new RangedState(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(o.owner(),foreign)),o,101));
        var combat=CombatState.empty().withRanged(ranged);var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),combat);
        assertEquals(combat,CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertTrue(ranged.retired(o.owner()).followups().isEmpty());
    }
}
