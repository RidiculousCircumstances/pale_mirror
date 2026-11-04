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

/** Unlock/physical execution/recovery contracts, including a non-resident caller. */
class CombatTrainingTest {
    private static final MeleeRules RULES=MeleeRules.initial();
    private static final WeaponProfile SWORD=new WeaponProfile("minecraft:iron_sword",6,13,3,1,CombatSkillId.SWORD);
    private static CombatProfile profile(int sword,int evade,int defence,int agility){
        return new CombatProfile(new CombatAttributes(50,agility,50,50,50),Map.of(CombatSkillId.SWORD,sword,CombatSkillId.EVASION,evade,CombatSkillId.WEAPON_DEFENCE,defence));
    }
    private static CombatOrder order(CombatProfile profile){
        var actor=new SubjectId("creature:training-test");var owner=new SubjectId("shipment:training-test");
        var target=new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(1,2),"minecraft:overworld");var at=SurfaceAnchor.at(0,63,0);
        return new CombatOrder(new ActorExecutionId(actor,ActorActivityKind.COURIER,owner,1),1,new SubjectId("item:test-sword"),
            new CombatPartyId(new SubjectId("party:training-test")),new CombatEnvelope(target,CombatEnvelope.Kind.SELF_DEFENCE,at,24),
            at,100,1000,at.offset(2,0,0),UnitNavigationProfile.VILLAGER,Optional.empty(),RULES.styles().require(new CombatStyleId("frontier:mobile")),profile,CombatSkillId.SWORD);
    }
    @Test void independentTrainingUnlocksButAgilityChangesExecutionRatherThanGrantingMoves(){
        var novice=profile(0,9,0,100);var skilledSlow=profile(60,55,50,0);var skilledFast=profile(60,55,50,100);
        assertFalse(CombatMechanics.unlocked(novice,SWORD,MeleeAttack.CIRCLE));assertTrue(CombatMechanics.unlocked(novice,SWORD,MeleeAttack.BASIC_CUT));
        assertTrue(CombatMechanics.unlocked(skilledSlow,SWORD,MeleeAttack.CIRCLE));
        assertFalse(CombatMechanics.permits(novice,MeleeManeuver.Kind.BACKSTEP));
        assertTrue(CombatMechanics.permits(skilledSlow,MeleeManeuver.Kind.ADVANCE_LEFT));
        assertTrue(CombatMechanics.permits(profile(0,0,10,0),MeleeManeuver.Kind.BLOCK));
        assertFalse(CombatMechanics.permits(profile(100,100,0,100),MeleeManeuver.Kind.BLOCK));
        assertTrue(CombatMechanics.timing(skilledFast,MeleeAttack.RISING,RULES).preparationTicks()<CombatMechanics.timing(skilledSlow,MeleeAttack.RISING,RULES).preparationTicks());
        assertTrue(CombatMechanics.defensivePace(skilledFast,MeleeManeuver.Kind.SIDESTEP_LEFT)>CombatMechanics.defensivePace(skilledSlow,MeleeManeuver.Kind.SIDESTEP_LEFT));
        assertEquals(SWORD.reach(),CombatMechanics.reach(SWORD,MeleeAttack.RISING));
        assertTrue(CombatMechanics.reach(SWORD,MeleeAttack.KICK)<SWORD.reach());
    }
    @Test void noviceNonResidentCallerUsesOnlyLearnedTechniquesAndUnavailableSkillCannotBeInferred(){
        var order=order(profile(0,0,0,100));var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var state=CombatState.empty().observed(new CombatState.Armament(order.execution(),order.weapon(),SWORD)).enlisted(order,100,RULES);
        for(int tick=100;tick<130;tick++){
            var selected=CombatTechniqueSelection.choose(state,order,actor,1,tick,SWORD,2,0,0,RULES);
            assertTrue(selected==MeleeAttack.BASIC_CUT || selected==MeleeAttack.OVERHEAD);
        }
        var foreign=new CombatProfile(new CombatAttributes(100,100,100,100,100),Map.of(new CombatSkillId("other:claws"),100,CombatSkillId.EVASION,0,CombatSkillId.WEAPON_DEFENCE,0));
        assertFalse(CombatMechanics.unlocked(foreign,SWORD,MeleeAttack.QUICK));
        assertThrows(IllegalArgumentException.class,()->foreign.skill(CombatSkillId.SWORD));
    }
    @Test void generationAndBothProfileWireBoundariesRetainPersonalPreparation()throws IOException{
        var state=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:combat-training"),139));
        var person=state.humanPopulation().residents().values().iterator().next();var retained=person.combatProfile();
        assertEquals(retained,person.withRole(ResidentRole.CRAFTER).withCapabilities(person.capabilities()).withCharacteristics(person.characteristics())
            .relocated(new SubjectId("settlement:other"),new SubjectId("household:other")).combatProfile());
        assertEquals(retained,CombatProfileGeneration.create(139,person.id().value().hashCode()));
        assertTrue(state.humanPopulation().residents().values().stream().map(ResidentProfile::combatProfile).distinct().count()>10);
        var codec=new FrontierWorldStateCodec();assertEquals(state,codec.decode(codec.encode(state)));
        var bytes=new ByteArrayOutputStream();CombatProfileCodec.write(new DataOutputStream(bytes),retained);
        assertEquals(retained,CombatProfileCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertThrows(IllegalArgumentException.class,()->new CombatAttributes(101,50,50,50,50));
        assertThrows(IllegalArgumentException.class,()->new CombatProfile(retained.attributes(),Map.of(CombatSkillId.SWORD,-1)));
    }
    @Test void doubleStrikeRetainsOneExactFiniteContinuationAndNeverReopensTheChain()throws IOException{
        var order=order(profile(100,100,100,50));var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var first=new MeleeStrike(order.owner(),1,actor,order.target(),order.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.DOUBLE,1,101,100,order.profile(),List.of());
        var state=CombatState.empty().observed(new CombatState.Armament(order.execution(),order.weapon(),SWORD)).prepared(first).completed(first,110,RULES);
        var continuation=state.followups().get(order.owner());assertEquals(MeleeAttack.DOUBLE_SECOND,continuation.next());
        assertEquals(MeleeAttack.DOUBLE_SECOND,CombatTechniqueSelection.choose(state,order,actor,1,115,SWORD,2,0,0,RULES));
        assertFalse(continuation.matches(order,new ActorActuationId(new ActorBodyId(order.execution().actorId(),2),order.execution()),1,115));
        assertFalse(continuation.matches(order,actor,2,115));assertFalse(continuation.matches(order,actor,1,continuation.expiresAt()));
        var second=new MeleeStrike(order.owner(),1,actor,order.target(),order.weapon(),first.before(),SWORD,MeleeAttack.DOUBLE_SECOND,1,121,120,order.profile(),List.of());
        var finished=state.prepared(second).completed(second,130,RULES);assertFalse(finished.followups().containsKey(order.owner()));
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        assertEquals(state,CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
    @Test void authoredDataRejectsCyclicChainsMissingMovesAndDuplicateKeys()throws IOException{
        try(var in=getClass().getResourceAsStream("/io/farfrontier/palemirror/frontier/v3/combat-training.properties")){
            var text=new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(RULES.training().canonicalText(),CombatTrainingCatalog.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).canonicalText());
            for(var bad:List.of(text+"\ngeneration.spread=11\n",text.replace("CONTINUATION,NONE","CONTINUATION,DOUBLE"),text.replace("BASIC_CUT,","")))
                assertThrows(IllegalArgumentException.class,()->CombatTrainingCatalog.read(new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8))));
        }
    }
    @Test void aBlockCanHoldItsExactOriginButCannotMasqueradeAsMovement(){
        var order=order(profile(0,0,10,50));var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var at=order.actorPosition();var block=new MeleeManeuver(actor,order.weapon(),order.target(),MeleeManeuver.Kind.BLOCK,101,at,at,100,0,0,0,145,140,0);
        assertTrue(block.open());assertTrue(block.blocksStrike(100,RULES.maneuvers()));
        assertFalse(block.closed(120).ready(MeleeManeuver.Kind.BLOCK,144));
        assertThrows(IllegalArgumentException.class,()->new MeleeManeuver(actor,order.weapon(),order.target(),MeleeManeuver.Kind.BACKSTEP,101,at,at,100,0,0,0,145,140,0));
        assertTrue(CombatMechanics.blockReduction(order.profile())<1000,"no complete immunity");
    }
}
