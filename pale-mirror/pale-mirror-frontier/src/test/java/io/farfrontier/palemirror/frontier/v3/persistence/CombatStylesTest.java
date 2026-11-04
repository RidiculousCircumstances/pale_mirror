package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Same observations/weapon/body, different real preferences; no timing or animation-count guesses. */
class CombatStylesTest {
    private static final MeleeRules RULES=MeleeRules.initial();
    private static final SurfaceAnchor TARGET=SurfaceAnchor.at(0,63,0);
    private static final WeaponProfile SWORD=new WeaponProfile("minecraft:iron_sword",6,13,3,1);
    private static CombatStyle style(String name){return RULES.styles().require(new CombatStyleId("frontier:"+name));}
    private static CombatOrder order(int i,CombatStyle style){
        var owner=new SubjectId("shipment:style-"+i);var actor=new SubjectId("resident:style-"+i);
        var enemy=new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,new UUID(1,2),"minecraft:overworld");
        return new CombatOrder(new ActorExecutionId(actor,ActorActivityKind.COURIER,owner,1),1,new SubjectId("item:style-"+i),
                new CombatPartyId(new SubjectId("party:styles")),new CombatEnvelope(enemy,CombatEnvelope.Kind.SELF_DEFENCE,TARGET,32),
                TARGET,100,1000,TARGET.offset(4,0,0),UnitNavigationProfile.VILLAGER,Optional.empty(),style);
    }
    private static CombatState armed(CombatOrder order){return CombatState.empty().observed(new CombatState.Armament(order.execution(),order.weapon(),SWORD));}
    private static MeleeDuelPolicy.Choice choose(CombatState state,CombatOrder order,MeleeManeuvers.Facts facts,long tick,boolean attack){
        return MeleeDuelPolicy.choose(state,order,facts,tick,RULES,OptionalLong.empty(),EnumSet.allOf(MeleeManeuver.Kind.class),attack);
    }
    @Test void identicalThreatsProduceNoticeablyDifferentRetreatAndFootworkChoices(){
        int pressingRetreat=0,cautiousRetreat=0,mobileSides=0,pressingSides=0;
        for(int i=0;i<200;i++)for(var name:List.of("pressing","cautious","mobile")){
            var order=order(i,style(name));var selected=choose(armed(order),order,new MeleeManeuvers.Facts(2.8,0,true),100,true);
            if(selected.maneuver().orElse(null)==MeleeManeuver.Kind.BACKSTEP){if(name.equals("pressing"))pressingRetreat++;if(name.equals("cautious"))cautiousRetreat++;}
            if(selected.maneuver().filter(MeleeManeuver.Kind::lateral).isPresent()){if(name.equals("mobile"))mobileSides++;if(name.equals("pressing"))pressingSides++;}
        }
        assertTrue(cautiousRetreat>=80 && cautiousRetreat>pressingRetreat*3,"cautious must visibly yield to the same threatened swing");
        assertTrue(mobileSides>=50);assertEquals(0,pressingSides,"unpressured pressing does not inherit mobile footwork");
    }
    @Test void cautiousConservesEffortAndRepeatedInspectionCannotRerollAChoice(){
        int pressingRuns=0;
        for(int i=0;i<100;i++){
            var cautious=order(i,style("cautious"));var state=armed(cautious).spent(cautious.owner(),100,60,RULES);
            assertEquals(MeleeDuelPolicy.Action.APPROACH,choose(state,cautious,new MeleeManeuvers.Facts(8,0),100,false).action());
            var pressing=order(i,style("pressing"));var sameEffort=armed(pressing).spent(pressing.owner(),100,60,RULES);
            var choice=choose(sameEffort,pressing,new MeleeManeuvers.Facts(8,0),100,false);
            if(choice.maneuver().orElse(null)==MeleeManeuver.Kind.PURSUIT_RUN)pressingRuns++;
            assertEquals(choice,choose(sameEffort,pressing,new MeleeManeuvers.Facts(8,0),100,false));
        }
        assertTrue(pressingRuns>=80,"same fatigue must not erase the pressing/cautious difference");
        var order=order(1,style("mobile"));var state=armed(order);var expected=choose(state,order,new MeleeManeuvers.Facts(2.8,0,true),100,true);
        for(long tick=101;tick<108;tick++)assertEquals(expected,choose(state,order,new MeleeManeuvers.Facts(2.8,0,true),tick,true));
    }
    @Test void preferredStationsDifferButShortWeaponReachAndCommittedCooldownRemainHard(){
        var sector=new MeleeSector(MeleeSector.Role.PRESSURE,1000,0);var candidates=List.of(TARGET.offset(2,0,0),TARGET.offset(3,0,0));
        var pressing=order(1,style("pressing"));var cautious=order(1,style("cautious"));
        assertEquals(candidates.getFirst(),MeleePositions.rank(pressing,SWORD,candidates,List.of(),sector,RULES,Optional.empty()).getFirst());
        assertEquals(candidates.getLast(),MeleePositions.rank(cautious,SWORD,candidates,List.of(),sector,RULES,Optional.empty()).getFirst());
        var shortWeapon=new WeaponProfile("short",2,20,1,1);
        assertTrue(RULES.holdReach(shortWeapon,true,cautious.style())<=shortWeapon.reach());
        var actor=new ActorActuationId(new ActorBodyId(pressing.execution().actorId(),1),pressing.execution());
        var move=new MeleeManeuver(actor,pressing.weapon(),pressing.target(),MeleeManeuver.Kind.BACKSTEP,101,
                pressing.actorPosition(),pressing.actorPosition().offset(1,0,0),100,101,0,0,160,140,0);
        assertEquals(MeleeDuelPolicy.Action.WAIT,choose(armed(pressing).maneuver(move),pressing,new MeleeManeuvers.Facts(2.8,0),105,true).action());
    }
    @Test void personalReferenceSurvivesRoleTrainingRelocationSnapshotAndUnknownDefinitionsFail(){
        var state=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:combat-styles"),139));
        var person=state.humanPopulation().resident(new SubjectId("resident:1-16"));
        assertEquals(style("cautious").id(),person.combatStyle());
        assertEquals(style("pressing").id(),state.humanPopulation().resident(new SubjectId("resident:1-22")).combatStyle());
        assertEquals(person.combatStyle(),person.withRole(ResidentRole.CRAFTER).withCapabilities(person.capabilities())
                .withCharacteristics(person.characteristics()).relocated(new SubjectId("settlement:other"),new SubjectId("household:other")).combatStyle());
        var codec=new FrontierWorldStateCodec();assertEquals(state,codec.decode(codec.encode(state)));
        assertThrows(IllegalArgumentException.class,()->person.withCombatStyle(new CombatStyleId("frontier:missing")));
    }
    @Test void rearPreferenceChangesTheApproachButFreshContactAndMissingSightDoNotCauseAnOrbit()throws IOException{
        var order=order(1,style("cautious"));var front=TARGET.offset(1,0,2);var back=TARGET.offset(-1,0,2);
        var facing=Optional.of(new CombatFacing(1000,0));var sector=new MeleeSector(MeleeSector.Role.FLANK_LEFT,1000,0);
        assertEquals(back,MeleePositions.rank(order,SWORD,List.of(front,back),List.of(),sector,RULES,facing).getFirst());
        assertEquals(front,MeleePositions.rank(order,SWORD,List.of(front,back),List.of(),sector,RULES,Optional.empty()).getFirst());
        var body=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var state=armed(order).stance(new MeleeStance(body,order.target(),false,false,100,facing));
        assertEquals(facing,MeleeRearApproach.facing(state,order,100,RULES));
        assertTrue(MeleeRearApproach.facing(state,order,113,RULES).isEmpty(),"an old sighting cannot supply a live facing");
        assertTrue(MeleeRearApproach.facing(state.stance(new MeleeStance(body,order.target(),true,true,100,facing)),order,100,RULES).isEmpty(),
                "real contact must not be sacrificed for another orbit");
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        assertEquals(state,CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertTrue(CombatFacing.observed(0,0).isEmpty());assertThrows(IllegalArgumentException.class,()->new CombatFacing(0,0));
    }
    @Test void definitionsAreDataAndInvalidWeightsCannotSilentlyFallBack()throws IOException{
        try(var input=getClass().getResourceAsStream("/io/farfrontier/palemirror/frontier/v3/combat-styles.properties")){
            var text=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);
            var decoded=CombatStyleCatalog.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            assertEquals(RULES.styles().canonicalText(),decoded.canonicalText());
            String invalid=text.replace("pressing.attack=120","pressing.attack=0");assertNotEquals(text,invalid);
            assertThrows(IllegalArgumentException.class,()->CombatStyleCatalog.read(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
