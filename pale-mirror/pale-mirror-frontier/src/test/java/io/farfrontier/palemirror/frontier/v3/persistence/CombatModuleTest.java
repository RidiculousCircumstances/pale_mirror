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
        var ranked=MeleePositions.rank(order(1,Optional.empty()),SWORD,candidates,List.of(peer));
        assertEquals(west,ranked.getFirst());assertFalse(ranked.contains(east));assertFalse(ranked.contains(TARGET));assertFalse(ranked.contains(TARGET.offset(4,0,0)));
        var reversed=new ArrayList<>(candidates);Collections.reverse(reversed);
        assertEquals(ranked,MeleePositions.rank(order(1,Optional.empty()),SWORD,reversed,List.of(peer)));
    }
    @Test void nonGuardPendingHitAndBoundedPositionFeedbackSurviveRecoveryAndRetireExactly()throws IOException{
        var order=order(1,Optional.of(TARGET.offset(1,0,0)));
        var body=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var strike=new MeleeStrike(order.owner(),1,body,order.target(),order.weapon(),new ItemCondition(250,0),SWORD,1,101,100);
        var state=CombatState.empty().prepared(strike).blocked(order,order.station().orElseThrow(),100);
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        var recovered=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));assertEquals(state,recovered);
        assertTrue(recovered.rejected(order,order.station().orElseThrow(),101));assertFalse(recovered.rejected(order,order.station().orElseThrow(),141));
        assertThrows(IllegalArgumentException.class,()->recovered.retired(order.owner()));
        var completed=recovered.completed(strike,101);assertEquals(114L,completed.nextAttack().get(order.owner()));
        assertTrue(completed.retired(order.owner()).armaments().isEmpty());
        assertThrows(IllegalArgumentException.class,()->completed.completed(strike,102));
        var changed=new MeleeStrike(order.owner(),1,body,order.target(),order.weapon(),strike.before(),SWORD,2,102,100);
        assertThrows(IllegalArgumentException.class,()->recovered.completed(changed,101));
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
    @Test void allMechanicalIngressHasAnExactCodecAndRejectsTruncation(){
        var order=order(1,Optional.of(TARGET.offset(1,0,0)));var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var events=List.<CombatEvent>of(new CombatEvent.WeaponDefined(order.execution(),order.weapon(),SWORD),new CombatEvent.WeaponObserved(actor,order.weapon(),SWORD),
                new CombatEvent.PositionBlocked(actor,order.target(),order.station().orElseThrow()));
        var codecs=CombatPayloadCodecs.create();
        for(var event:events){var bytes=codecs.encode(event);assertEquals(event,codecs.decode(event.type(),bytes));
            assertThrows(IllegalArgumentException.class,()->codecs.decode(event.type(),Arrays.copyOf(bytes,bytes.length-1)));}
    }
}
