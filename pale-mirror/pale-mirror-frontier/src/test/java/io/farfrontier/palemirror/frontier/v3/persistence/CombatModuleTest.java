package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Mechanics use a non-guard execution; this fixture grants no new production combat role. */
class CombatModuleTest {
    private static final SurfaceAnchor TARGET=SurfaceAnchor.at(0,63,0);
    private static final WeaponProfile SWORD=new WeaponProfile("minecraft:iron_sword",6,13,3,1);
    private static CombatOrder order(int id,Optional<SurfaceAnchor> station){
        var execution=new ActorExecutionId(new SubjectId("resident:combat-"+id),ActorActivityKind.COURIER,new SubjectId("shipment:combat-"+id),1);
        var enemy=new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,new UUID(1,2),"pale_mirror:frontier_graybox");
        return new CombatOrder(execution,1,new SubjectId("item:sword-"+id),new CombatEngagement(enemy,CombatEngagement.Kind.SELF_DEFENCE,TARGET,24),
                TARGET,100,60,TARGET.offset(3,0,0),UnitNavigationProfile.VILLAGER,station);
    }
    @Test void tacticalSectorsSpreadAndStayStableAcrossTargetCellChangesAndHydration() throws Exception {
        var rules=MeleeRules.initial();var state=CombatState.empty();var first=order(1,Optional.empty());
        state=state.positioned(first,Optional.of(TARGET.offset(2,0,0)),MeleePosition.Reason.RESERVED,100,rules);
        assertEquals(MeleeSector.Role.PRESSURE,state.positions().get(first.owner()).sector().role());
        var second=order(2,Optional.empty());var sector=MeleeTactics.sector(state,second,101,rules);
        assertEquals(MeleeSector.Role.FLANK_LEFT,sector.role());
        state=state.positioned(second,Optional.of(TARGET.offset(0,0,2)),MeleePosition.Reason.RESERVED,101,rules);
        assertFalse(MeleeTactics.readyAt(state,second,TARGET.offset(2,0,0),102,rules));
        assertTrue(MeleeTactics.readyAt(state,second,TARGET.offset(0,0,2),102,rules));
        var third=order(3,Optional.empty());assertEquals(MeleeSector.Role.FLANK_RIGHT,MeleeTactics.sector(state,third,102,rules).role());
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        assertEquals(state,CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertEquals(MeleeSector.Role.PRESSURE,MeleeTactics.sector(state,second,182,rules).role());
    }
    @Test void attackChoiceUsesObservedDistanceAndRetreatButNeverGrantsExtraDamageReach() throws IOException {
        assertEquals(MeleeAttack.QUICK,MeleeTactics.attack(SWORD,1.2,0,MeleeRules.initial()));
        assertEquals(MeleeAttack.LUNGE,MeleeTactics.attack(SWORD,1.2,0.2,MeleeRules.initial()));
        assertEquals(MeleeAttack.LUNGE,MeleeTactics.attack(SWORD,3.4,0,MeleeRules.initial()));
        assertTrue(MeleeTactics.opportunity(SWORD,MeleeAttack.LUNGE,3.4,MeleeRules.initial()));
        assertFalse(MeleeTactics.opportunity(SWORD,MeleeAttack.LUNGE,4.2,MeleeRules.initial()));
        var order=order(1,Optional.empty());var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var strike=new MeleeStrike(order.owner(),1,actor,order.target(),order.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.LUNGE,1,101,100);
        var state=CombatState.empty().prepared(strike);var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        var restored=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(strike,restored.pending().get(order.owner()));assertEquals(110,strike.impactAt(MeleeRules.initial()));
        assertEquals(120L,restored.completed(strike,110,MeleeRules.initial()).nextAttack().get(order.owner()));
        assertThrows(IllegalArgumentException.class,()->MeleeAttack.require(0));
    }
    @Test void immediateDecisionHonoursVisibilityReadinessPendingEffectsAndExpiry(){
        var order=order(1,Optional.empty());
        assertEquals(MeleeTactics.Decision.ATTACK,MeleeTactics.decide(order,new MeleeTactics.Facts(100,true,true,true,false,100,true)));
        assertEquals(MeleeTactics.Decision.APPROACH,MeleeTactics.decide(order,new MeleeTactics.Facts(100,true,false,true,false,100,true)));
        assertEquals(MeleeTactics.Decision.WAIT,MeleeTactics.decide(order,new MeleeTactics.Facts(100,true,true,true,false,101,true)));
        assertEquals(MeleeTactics.Decision.WAIT,MeleeTactics.decide(order,new MeleeTactics.Facts(100,true,true,true,true,100,true)));
        assertEquals(MeleeTactics.Decision.FINISHED,MeleeTactics.decide(order,new MeleeTactics.Facts(161,true,true,true,false,100,true)));
    }
    @Test void attackStationsSpreadAroundTheEnemyAndExcludeOverlapsAndOutOfRangeCells(){
        var east=TARGET.offset(1,0,0);var west=TARGET.offset(-1,0,0);var north=TARGET.offset(0,0,-1);
        var peer=order(2,Optional.of(east));var candidates=List.of(TARGET,east,west,north,TARGET.offset(4,0,0));
        var ranked=MeleePositions.rank(order(1,Optional.empty()),SWORD,candidates,List.of(peer),new MeleeSector(MeleeSector.Role.FLANK_RIGHT,1000,0),MeleeRules.initial());
        assertEquals(north,ranked.getFirst());assertFalse(ranked.contains(east));assertFalse(ranked.contains(TARGET));assertFalse(ranked.contains(TARGET.offset(4,0,0)));
        var reversed=new ArrayList<>(candidates);Collections.reverse(reversed);
        assertEquals(ranked,MeleePositions.rank(order(1,Optional.empty()),SWORD,reversed,List.of(peer),new MeleeSector(MeleeSector.Role.FLANK_RIGHT,1000,0),MeleeRules.initial()));
    }
    @Test void nonGuardPendingHitAndBoundedPositionFeedbackSurviveRecoveryAndRetireExactly()throws IOException{
        var order=order(1,Optional.of(TARGET.offset(1,0,0)));
        var body=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var strike=new MeleeStrike(order.owner(),1,body,order.target(),order.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.QUICK,1,101,100);
        var rules=new MeleeRules(10,8,8,80);
        var state=CombatState.empty().prepared(strike).blocked(order,order.station().orElseThrow(),100,rules);
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        var recovered=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));assertEquals(state,recovered);
        assertTrue(recovered.rejected(order,order.station().orElseThrow(),141,rules));assertFalse(recovered.rejected(order,order.station().orElseThrow(),181,rules));
        assertThrows(IllegalArgumentException.class,()->recovered.retired(order.owner()));
        var completed=recovered.completed(strike,101,MeleeRules.initial());assertEquals(113L,completed.nextAttack().get(order.owner()));
        assertTrue(completed.retired(order.owner()).armaments().isEmpty());
        assertThrows(IllegalArgumentException.class,()->completed.completed(strike,102,MeleeRules.initial()));
        var changed=new MeleeStrike(order.owner(),1,body,order.target(),order.weapon(),strike.before(),SWORD,MeleeAttack.QUICK,2,102,100);
        assertThrows(IllegalArgumentException.class,()->recovered.completed(changed,101,MeleeRules.initial()));
    }
    @Test void completedNegativeRoutesCannotPermanentlyHideTheNextAttackStation(){
        var candidates=java.util.stream.IntStream.range(1,10).mapToObj(i->TARGET.offset(i,0,0)).toList();
        var selected=MeleePositions.reachable(candidates,8,station->{if(station.x()<9)throw new io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable("known disconnected candidate");});
        assertEquals(candidates.getLast(),selected.orElseThrow());
        var requested=new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable.class,()->
                MeleePositions.reachable(candidates,3,station->{requested.incrementAndGet();throw new io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable(
                        io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,"retained async dependency");}));
        assertEquals(3,requested.get());
    }
    @Test void positionLeasesExpireAndConfirmedUnavailabilityDiffersFromAQueuedRoute() throws IOException {
        var order=order(1,Optional.of(TARGET.offset(1,0,0)));var rules=MeleeRules.initial();
        var state=CombatState.empty().positioned(order,order.station(),MeleePosition.Reason.RESERVED,100,rules);
        var claim=state.positions().get(order.owner());assertTrue(claim.active(180,rules));assertFalse(claim.active(181,rules));
        assertTrue(claim.overlaps(order.station().orElseThrow().offset(1,0,0),1_500_000,1_950_000),"wide units reserve their actual footprint");
        assertFalse(claim.overlaps(order.station().orElseThrow().offset(0,3,0),600_000,1_950_000),"separate floors do not share a slot");
        state=state.positioned(order,Optional.empty(),MeleePosition.Reason.ROUTE_PENDING,101,rules)
                .positioned(order,Optional.empty(),MeleePosition.Reason.ROUTE_PENDING,1000,rules);
        assertFalse(state.deferred(order,1000),"a slow calculation is never a proof of no path");
        state=state.positioned(order,Optional.empty(),MeleePosition.Reason.NO_PATH,1001,rules)
                .positioned(order,Optional.empty(),MeleePosition.Reason.NO_POSITION,1101,rules);
        assertTrue(state.deferred(order,1102));assertFalse(state.deferred(order,1201));
        var moved=new CombatOrder(order.execution(),order.revision(),order.weapon(),order.engagement(),TARGET.offset(1,0,0),100,2000,order.actorPosition(),order.navigation(),order.station());
        assertFalse(state.deferred(moved,1102),"target movement reopens tactical admission");
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        assertEquals(state,CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertThrows(IllegalArgumentException.class,()->MeleePosition.Reason.require(99));
    }
    @Test void holdingUsesTwoStrictWeaponBoundariesAndCannotExpandAttackReach(){
        var rules=MeleeRules.initial();assertEquals(1.65,rules.holdReach(SWORD,false),0.0001);assertEquals(2.0,rules.holdReach(SWORD,true));
        assertEquals(4,rules.windup(MeleeAttack.QUICK));assertEquals(10,rules.windup(MeleeAttack.LUNGE));
        var order=order(1,Optional.empty());
        assertEquals(MeleeTactics.Decision.APPROACH,MeleeTactics.decide(order,new MeleeTactics.Facts(101,true,true,false,false,100,true)));
    }
    @Test void missingColumnsAreNotEvidenceThatEveryAttackPositionIsImpossible(){
        var unknown=new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry(){
            public WorldBounds bounds(){return new WorldBounds(-16,-16,32,32);}
            public SurfaceAnchor supportAt(int x,int z){return null;}
            public boolean blocked(SurfaceAnchor surface){return false;}
        };
        assertTrue(MeleePositions.knowledgeMissing(unknown,order(1,Optional.empty()),SWORD,3));
        var multiLevel=new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry(){
            public WorldBounds bounds(){return new WorldBounds(-16,-16,32,32);}
            public SurfaceAnchor supportAt(int x,int z){return SurfaceAnchor.at(x,63,z);}
            public List<SurfaceAnchor> surfacesAt(int x,int z){return List.of(SurfaceAnchor.at(x,63,z),SurfaceAnchor.at(x,66,z));}
            public boolean blocked(SurfaceAnchor surface){return true;}
        };
        assertFalse(MeleePositions.knowledgeMissing(multiLevel,order(1,Optional.empty()),SWORD,3));
    }
    @Test void allMechanicalIngressHasAnExactCodecAndRejectsTruncation(){
        var order=order(1,Optional.of(TARGET.offset(1,0,0)));var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var events=List.<CombatEvent>of(new CombatEvent.WeaponDefined(order.execution(),order.weapon(),SWORD),new CombatEvent.WeaponObserved(actor,order.weapon(),SWORD),
                new CombatEvent.PositionBlocked(actor,order.target(),order.station().orElseThrow()),
                new CombatEvent.StanceObserved(new MeleeStance(actor,order.target(),true,100)),
                new CombatEvent.PositionPlanned(order.execution(),order.target(),order.targetPosition(),order.station(),MeleePosition.Reason.RESERVED));
        var codecs=CombatPayloadCodecs.create();
        for(var event:events){var bytes=codecs.encode(event);assertEquals(event,codecs.decode(event.type(),bytes));
            assertThrows(IllegalArgumentException.class,()->codecs.decode(event.type(),Arrays.copyOf(bytes,bytes.length-1)));}
    }
}
