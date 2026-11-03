package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SecurityLocalReactionTest {
    private static final SubjectId HOME=new SubjectId("settlement:local"),ACTOR=new SubjectId("resident:guard"),ALLY=new SubjectId("resident:ally");
    private static final CombatReactionPolicy.Rules RULES=SecurityRules.initial().reactionRules();
    private static final SurfaceAnchor HERE=SurfaceAnchor.at(0,63,0);
    private static CombatTarget target(long id){return new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(0,id),"pale_mirror:frontier_graybox");}
    private static CombatAwareness.Harm harm(SubjectId victim,CombatTarget target,long tick) {
        return new CombatAwareness.Harm(new CombatAwareness.HarmKey(HOME,victim,target),HERE.support(),tick);
    }
    private static CombatReactionPolicy.Threat threat(CombatTarget target,int x,boolean seen,List<CombatAwareness.Harm> harms) {
        var point=HERE.offset(x,0,0);
        return new CombatReactionPolicy.Threat(target,point,seen?Optional.of(new CombatAwareness.Sight(
                new CombatAwareness.SightKey(HOME,ACTOR,target),point.support(),HERE.support(),100)):Optional.empty(),harms);
    }
    @Test void selfDefenceDoesNotRequireAProtectedPostAndUsesTheWitnessedPose() {
        var enemy=target(1);
        var choice=CombatReactionPolicy.choose(RULES,ACTOR,HERE.offset(100,0,0),List.of(),
                List.of(threat(enemy,1,false,List.of(harm(ACTOR,enemy,100)))),Optional.empty(),100).orElseThrow();
        assertEquals(CombatEnvelope.Kind.SELF_DEFENCE,choice.kind());assertEquals(HERE,choice.anchor());
        assertTrue(CombatReactionPolicy.choose(RULES,ACTOR,HERE,List.of(),
                List.of(threat(enemy,25,false,List.of(harm(ACTOR,enemy,100)))),Optional.empty(),100).isEmpty());
    }
    @Test void nearbyConfirmedAllyHarmIsDistinctFromAnUnseenOrExpiredRumour() {
        var enemy=target(1);var seenHarm=threat(enemy,3,false,List.of(harm(ALLY,enemy,100)));
        assertEquals(CombatEnvelope.Kind.ALLY_DEFENCE,CombatReactionPolicy.choose(RULES,ACTOR,HERE,List.of(),
                List.of(seenHarm),Optional.empty(),100).orElseThrow().kind());
        assertTrue(CombatReactionPolicy.choose(RULES,ACTOR,HERE.offset(30,0,0),List.of(),List.of(seenHarm),Optional.empty(),100).isEmpty());
        assertTrue(CombatReactionPolicy.choose(RULES,ACTOR,HERE,List.of(),List.of(seenHarm),Optional.empty(),201).isEmpty());
    }
    @Test void anAreaNeedsPersonalSightAndDoesNotDragItsLeashAfterTheEnemy() {
        var enemy=target(1);var areas=List.of(new CombatReactionPolicy.Area(HERE,64));
        assertTrue(CombatReactionPolicy.choose(RULES,ACTOR,HERE,areas,List.of(threat(enemy,2,false,List.of())),Optional.empty(),100).isEmpty());
        var first=CombatReactionPolicy.choose(RULES,ACTOR,HERE.offset(100,0,0),areas,List.of(threat(enemy,2,true,List.of())),Optional.empty(),100).orElseThrow();
        assertEquals(CombatEnvelope.Kind.AREA_DEFENCE,first.kind());assertEquals(HERE,first.anchor());
        var distant=new CombatReactionPolicy.Threat(enemy,HERE.offset(65,0,0),Optional.of(new CombatAwareness.Sight(
                new CombatAwareness.SightKey(HOME,ACTOR,enemy),HERE.offset(65,0,0).support(),HERE.offset(64,0,0).support(),101)),List.of());
        assertTrue(CombatReactionPolicy.choose(RULES,ACTOR,HERE.offset(64,0,0),areas,List.of(distant),Optional.of(first),101).isEmpty());
    }
    @Test void selfDefenceWinsPriorityAndEqualChoicesUseDistanceHysteresis() {
        var enemy=target(1);var other=target(2);var areas=List.of(new CombatReactionPolicy.Area(HERE,64));
        var old=new CombatEnvelope(enemy,CombatEnvelope.Kind.AREA_DEFENCE,HERE,64);
        var choices=List.of(threat(enemy,9,true,List.of()),threat(other,2,true,List.of()));
        assertEquals(enemy,CombatReactionPolicy.choose(RULES,ACTOR,HERE,areas,choices,Optional.of(old),100).orElseThrow().target());
        choices=List.of(threat(enemy,12,true,List.of()),threat(other,2,true,List.of()));
        assertEquals(other,CombatReactionPolicy.choose(RULES,ACTOR,HERE,areas,choices,Optional.of(old),100).orElseThrow().target());
        choices=List.of(threat(enemy,2,true,List.of()),threat(other,12,true,List.of(harm(ACTOR,other,100))));
        assertEquals(CombatEnvelope.Kind.SELF_DEFENCE,CombatReactionPolicy.choose(RULES,ACTOR,HERE,areas,choices,Optional.of(old),100).orElseThrow().kind());
    }
}
