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
    private static final CombatStyle MECHANICS=new CombatStyle(new CombatStyleId("test:mechanics"),"Mechanics",2200000,1,1,1,0,1,0,0);
    private static final SurfaceAnchor TARGET=SurfaceAnchor.at(0,63,0);
    private static final WeaponProfile SWORD=new WeaponProfile("minecraft:iron_sword",6,13,3,1);
    private static CombatOrder order(int id,Optional<SurfaceAnchor> station){
        var execution=new ActorExecutionId(new SubjectId("resident:combat-"+id),ActorActivityKind.COURIER,new SubjectId("shipment:combat-"+id),1);
        var enemy=new CombatTarget(CombatTarget.Kind.HOSTILE_MOB,new UUID(1,2),"pale_mirror:frontier_graybox");
        return new CombatOrder(execution,1,new SubjectId("item:sword-"+id),new CombatPartyId(new SubjectId("party:test")),new CombatEnvelope(enemy,CombatEnvelope.Kind.SELF_DEFENCE,TARGET,24),
                TARGET,100,60,TARGET.offset(3,0,0),UnitNavigationProfile.VILLAGER,station,MECHANICS);
    }
    @Test void tacticalSectorsSpreadAndStayStableAcrossTargetCellChangesAndHydration() throws Exception {
        var rules=MeleeRules.initial();var state=CombatState.empty();var first=order(1,Optional.empty());
        state=state.positioned(first,Optional.of(TARGET.offset(2,0,0)),MeleePosition.Reason.RESERVED,100,rules);
        assertEquals(MeleeSector.Role.PRESSURE,MeleeTactics.sector(state,first,100,rules).role());
        var second=order(2,Optional.empty());var sector=MeleeTactics.sector(state,second,101,rules);
        assertEquals(MeleeSector.Role.FLANK_LEFT,sector.role());
        state=state.positioned(second,Optional.of(TARGET.offset(0,0,2)),MeleePosition.Reason.RESERVED,101,rules);
        assertFalse(MeleeTactics.readyAt(state,second,TARGET.offset(2,0,0),102,rules));
        assertTrue(MeleeTactics.readyAt(state,second,TARGET.offset(0,0,2),102,rules));
        var third=order(3,Optional.empty());assertEquals(MeleeSector.Role.FLANK_RIGHT,MeleeTactics.sector(state,third,102,rules).role());
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        assertEquals(state,CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertEquals(MeleeSector.Role.FLANK_LEFT,MeleeTactics.sector(state,second,182,rules).role(),"a temporary station lease is not encounter membership");
    }
    @Test void encounterMembershipIsExplicitSeparatesPartiesAndReleasesOnlyTheExactExecution() throws IOException {
        var a=order(1,Optional.empty());var b=order(2,Optional.empty());var raw=order(3,Optional.empty());
        var c=new CombatOrder(raw.execution(),raw.revision(),raw.weapon(),new CombatPartyId(new SubjectId("party:other")),raw.engagement(),
                raw.targetPosition(),raw.observedAt(),raw.lifetime(),raw.actorPosition(),raw.navigation(),raw.station(),MECHANICS);
        var rules=MeleeRules.initial();var state=CombatState.empty().enlisted(a,100,rules).enlisted(b,100,rules).enlisted(c,100,rules);
        assertEquals(2,state.engagements().size());assertEquals(MeleeSector.Role.FLANK_LEFT,MeleeTactics.sector(state,b,100,rules).role());
        assertEquals(MeleeSector.Role.PRESSURE,MeleeTactics.sector(state,c,100,rules).role());
        var obsolete=new ActorExecutionId(a.execution().actorId(),a.execution().activityKind(),a.owner(),2);
        assertEquals(state,state.withdrawn(obsolete),"another execution cannot retire a participant");
        state=state.withdrawn(a.execution());assertEquals(MeleeSector.Role.PRESSURE,MeleeTactics.sector(state,b,100,rules).role());
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        assertEquals(state,CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertTrue(state.withdrawn(b.execution()).withdrawn(c.execution()).engagements().isEmpty());
    }
    @Test void waveSlotsAreSingleUseFencedAndRetainTheirUseAcrossRecovery() throws IOException {
        var a=order(1,Optional.empty());var observer=new ActorActuationId(new ActorBodyId(a.execution().actorId(),1),a.execution());
        var plan=CombatEngagement.first(a,100);var key=plan.key();
        plan=new CombatEngagement(key,2,plan.anchor(),plan.axisX(),plan.axisZ(),plan.participants(),100,
                Optional.of(new CombatEngagement.Wave(a.targetPosition(),List.of(new CombatEngagement.Slot(observer,104,110,false)))));
        assertFalse(plan.permits(a,observer,103,EngagementRules.initial()));assertTrue(plan.permits(a,observer,104,EngagementRules.initial()));
        assertFalse(plan.permits(a,new ActorActuationId(new ActorBodyId(a.execution().actorId(),2),a.execution()),104,EngagementRules.initial()));
        assertFalse(plan.permits(a,observer,111,EngagementRules.initial()));
        plan=plan.committed(observer);assertFalse(plan.permits(a,observer,105,EngagementRules.initial()));
        var state=CombatState.empty().withEngagement(plan);var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        var recovered=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertFalse(recovered.engagements().get(key).permits(a,observer,105,EngagementRules.initial()));
        var foreign=order(2,Optional.empty());var retained=plan;
        assertThrows(IllegalArgumentException.class,()->new CombatEngagement(key,3,retained.anchor(),retained.axisX(),retained.axisZ(),retained.participants(),100,
                Optional.of(new CombatEngagement.Wave(a.targetPosition(),List.of(new CombatEngagement.Slot(new ActorActuationId(new ActorBodyId(foreign.execution().actorId(),1),foreign.execution()),104,110,false))))));
    }
    @Test void encounterChoosesTheNearerFreeFlankAndDoesNotPullItBackAcrossTheTarget(){
        var first=order(1,Optional.empty());var next=order(2,Optional.empty()).atActorObservation(TARGET.offset(0,0,-4));
        var plan=CombatEngagement.first(first,100).admitted(next,100);
        assertEquals(MeleeSector.Role.FLANK_RIGHT,plan.participants().get(next.owner()).role());
        assertEquals(plan.participants().get(next.owner()).role(),plan.admitted(next.atActorObservation(TARGET.offset(1,0,4)),101).participants().get(next.owner()).role(),
                "a moving target does not swap the assigned side on every observation");
        var left=order(2,Optional.empty()).atActorObservation(TARGET.offset(0,0,4));
        assertEquals(List.of(TARGET.offset(0,0,2)),MeleePositions.rank(left,SWORD,
                List.of(TARGET.offset(2,0,0),TARGET.offset(0,0,2),TARGET.offset(0,0,-2)),List.of(),new MeleeSector(MeleeSector.Role.FLANK_LEFT,1000,0),MeleeRules.initial()));
    }
    @Test void failedFlankRetainsIntentAcrossHydrationAndRetriesADifferentSideWithoutAnOldFailureClock() throws IOException {
        var rules=MeleeRules.initial();var first=order(1,Optional.empty());var other=order(2,Optional.empty());
        var combat=CombatState.empty().enlisted(first,100,rules).positioned(other,Optional.empty(),MeleePosition.Reason.NO_PATH,100,rules);
        var plan=combat.engagements().get(CombatEngagement.Key.of(first));var members=new LinkedHashMap<>(plan.participants());
        var orders=Map.of(first.owner(),first,other.owner(),other);
        assertFalse(CombatFormation.reconcile(members,orders,combat,plan.axisX(),plan.axisZ(),139,rules),"assignment gets time to execute");
        assertTrue(CombatFormation.reconcile(members,orders,combat,plan.axisX(),plan.axisZ(),140,rules));
        assertEquals(MeleeSector.Role.SUPPORT,members.get(other.owner()).role());assertEquals(MeleeSector.Role.FLANK_LEFT,members.get(other.owner()).preferredRole());
        plan=new CombatEngagement(plan.key(),plan.revision()+1,plan.anchor(),plan.axisX(),plan.axisZ(),members,0,Optional.empty());
        combat=combat.withEngagement(plan).reassigned(other.execution());
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),combat);
        var restored=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));assertEquals(combat,restored);
        assertFalse(restored.positions().containsKey(other.owner()),"old failure cannot become immediate UNREACHABLE on a new assignment");
        members=new LinkedHashMap<>(restored.engagements().get(plan.key()).participants());
        assertFalse(CombatFormation.reconcile(members,orders,restored,plan.axisX(),plan.axisZ(),199,rules));
        var waiting=restored.positioned(other,Optional.empty(),MeleePosition.Reason.ROUTE_PENDING,200,rules);
        assertFalse(CombatFormation.reconcile(members,orders,waiting,plan.axisX(),plan.axisZ(),200,rules),"retry cannot discard an outstanding shared route calculation");
        assertTrue(CombatFormation.reconcile(members,orders,restored,plan.axisX(),plan.axisZ(),200,rules));
        assertNotEquals(MeleeSector.Role.FLANK_LEFT,members.get(other.owner()).role());assertNotEquals(MeleeSector.Role.SUPPORT,members.get(other.owner()).role());
        assertEquals(200,members.get(other.owner()).roleSince());
    }
    @Test void routeWaitingAndAnUnsettledAttackCannotBeReassignedAsFailedFlanking(){
        var rules=MeleeRules.initial();var first=order(1,Optional.empty());var other=order(2,Optional.empty());
        var combat=CombatState.empty().enlisted(first,100,rules).positioned(other,Optional.empty(),MeleePosition.Reason.ROUTE_PENDING,100,rules);
        var plan=combat.engagements().get(CombatEngagement.Key.of(first));var members=new LinkedHashMap<>(plan.participants());var orders=Map.of(first.owner(),first,other.owner(),other);
        assertFalse(CombatFormation.reconcile(members,orders,combat,plan.axisX(),plan.axisZ(),150,rules));
        var actor=new ActorActuationId(new ActorBodyId(other.execution().actorId(),1),other.execution());
        combat=combat.positioned(other,Optional.empty(),MeleePosition.Reason.NO_PATH,100,rules)
                .prepared(new MeleeStrike(other.owner(),1,actor,other.target(),other.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.QUICK,1,101,100));
        assertFalse(CombatFormation.reconcile(members,orders,combat,plan.axisX(),plan.axisZ(),150,rules));
        assertEquals(MeleeSector.Role.FLANK_LEFT,members.get(other.owner()).role());
    }
    @Test void pressureFighterKeepsARealContactStrikeWhileItsPartnerIsFlanking(){
        var rules=MeleeRules.initial();var first=order(1,Optional.empty());var other=order(2,Optional.empty());
        var actor=new ActorActuationId(new ActorBodyId(first.execution().actorId(),1),first.execution());
        var memory=MeleeExchange.first(first).observed(OptionalLong.of(100),100,rules.maneuvers().duel())
                .observed(OptionalLong.of(120),120,rules.maneuvers().duel());
        var combat=CombatState.empty().enlisted(first,100,rules).enlisted(other,100,rules)
                .observed(new CombatState.Armament(first.execution(),first.weapon(),SWORD)).withExchange(first.owner(),memory)
                .stance(new MeleeStance(actor,first.target(),true,true,121));
        assertTrue(memory.pressured(121,rules.maneuvers().duel()));
        assertTrue(CombatFormation.pressureNeeded(combat,first,121,rules));
        assertEquals(MeleeDuelPolicy.Action.ATTACK,MeleeDuelPolicy.choose(combat,first,new MeleeManeuvers.Facts(2.8,0,true),121,rules,
                OptionalLong.of(120),EnumSet.allOf(MeleeManeuver.Kind.class),true).action());
    }
    @Test void flankApproachUsesAnOuterArcAndCommittedAimDoesNotTrackThroughTheBack() {
        var actor=order(2,Optional.empty());var sector=new MeleeSector(MeleeSector.Role.FLANK_LEFT,1000,0);var rules=EngagementRules.initial();
        assertTrue(CombatApproach.arc(actor,sector,rules));
        assertTrue(CombatApproach.score(actor,sector,TARGET.offset(4,0,3),rules)<CombatApproach.score(actor,sector,TARGET.offset(0,0,1),rules));
        var overshot=actor.atActorObservation(TARGET.offset(-1,0,5));
        assertTrue(CombatApproach.score(overshot,sector,TARGET.offset(0,0,5),rules)
                <CombatApproach.score(overshot,sector,TARGET.offset(-4,0,3),rules),
                "a flanker just beyond its assigned side returns directly instead of orbiting again");
        assertTrue(MeleeAim.contains(179,0,-1,55));assertTrue(MeleeAim.contains(-179,0,-1,55));
        assertFalse(MeleeAim.contains(0,0,-1,55));assertTrue(MeleeAim.contains(0,1,1,55));
    }
    @Test void lungeAimTurnsGraduallyAndHasABroadButNotRearFacingContactSector(){
        var maximum=EngagementRules.initial().aimTurnDegreesPerTick();
        assertEquals(maximum,MeleeAim.turnToward(0,90,maximum));
        assertEquals(181,MeleeAim.turnToward(179,-179,12),0.001,"wrap-around uses the short turn");
        int sector=EngagementRules.initial().lungeTurnDegrees();
        assertTrue(MeleeAim.contains(0,Math.sin(Math.toRadians(30)),Math.cos(Math.toRadians(30)),sector));
        assertFalse(MeleeAim.contains(0,0,-1,sector),"wide sword arc never hits straight behind the committed facing");
    }
    @Test void contactStrikeUsesFullWeaponReachEvenAgainstRetreatButNeverGrantsExtraDamageReach() throws IOException {
        assertEquals(MeleeAttack.QUICK,MeleeTactics.attack(SWORD,1.2,0,MeleeRules.initial()));
        assertEquals(MeleeAttack.QUICK,MeleeTactics.attack(SWORD,1.2,0.2,MeleeRules.initial()));
        assertEquals(MeleeAttack.QUICK,MeleeTactics.attack(SWORD,2.97,0.2,MeleeRules.initial()));
        assertEquals(MeleeAttack.QUICK,MeleeTactics.attack(SWORD,3.0,0.2,MeleeRules.initial()));
        assertEquals(MeleeAttack.LUNGE,MeleeTactics.attack(SWORD,3.01,0.2,MeleeRules.initial()));
        var shortWeapon=new WeaponProfile("short",2,20,1,1);
        assertEquals(MeleeAttack.LUNGE,MeleeTactics.attack(shortWeapon,1.01,0,MeleeRules.initial()));
        assertEquals(MeleeAttack.LUNGE,MeleeTactics.attack(SWORD,3.4,0,MeleeRules.initial()));
        assertTrue(MeleeTactics.opportunity(SWORD,MeleeAttack.LUNGE,3.4,MeleeRules.initial()));
        assertFalse(MeleeTactics.opportunity(SWORD,MeleeAttack.LUNGE,4.2,MeleeRules.initial()));
        var order=order(1,Optional.empty());var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var strike=new MeleeStrike(order.owner(),1,actor,order.target(),order.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.LUNGE,1,101,100);
        var state=CombatState.empty().prepared(strike);var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        var restored=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(strike,restored.pending().get(order.owner()));assertEquals(108,strike.impactAt(MeleeRules.initial()));
        assertEquals(124L,restored.completed(strike,110,MeleeRules.initial()).nextAttack().get(order.owner()));
        assertThrows(IllegalArgumentException.class,()->MeleeAttack.require(0));
    }
    @Test void missedLungeOffersOneNearbyRecoveryAttackWithExactIdentityAndNormalWeaponCadence() throws IOException {
        var order=order(1,Optional.empty());var rules=MeleeRules.initial();
        var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var lunge=new MeleeStrike(order.owner(),1,actor,order.target(),order.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.LUNGE,17,101,100);
        var pending=CombatState.empty().prepared(lunge);
        assertTrue(pending.completed(lunge,104,rules,true).followups().isEmpty(),"cancelled preparation is not a missed swing");
        assertTrue(pending.completed(lunge,110,rules,false).followups().isEmpty(),"a hit does not grant another attack");
        var missed=pending.completed(lunge,110,rules,true);
        assertEquals(124L,missed.nextAttack().get(order.owner()),"a successful dodge leaves a real recovery window");
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),missed);
        var recovered=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(missed,recovered);
        assertEquals(MeleeAttack.FOLLOWUP,MeleeTactics.attack(recovered,order,actor,17,124,SWORD,3.2,0.2,rules));
        assertEquals(MeleeAttack.LUNGE,MeleeTactics.attack(recovered,order,actor,17,124,SWORD,6,0.2,rules),"far targets get ordinary pursuit");
        assertEquals(MeleeAttack.LUNGE,MeleeTactics.attack(recovered,order,actor,18,124,SWORD,3.2,0.2,rules),"respawn is a different target incarnation");
        var successor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),2),order.execution());
        assertFalse(recovered.followups().get(order.owner()).matches(order,successor,17,124));
        assertEquals(MeleeAttack.LUNGE,MeleeTactics.attack(recovered,order,actor,17,151,SWORD,3.2,0.2,rules),"expired response is not permanent sprint");
        var followup=new MeleeStrike(order.owner(),1,actor,order.target(),order.weapon(),lunge.before(),SWORD,MeleeAttack.FOLLOWUP,17,125,124);
        var response=recovered.prepared(followup);
        assertTrue(response.followups().isEmpty(),"preparation spends the one continuation");
        assertTrue(response.completed(followup,134,rules,true).followups().isEmpty(),"missing again cannot chain recovery attacks");
        assertTrue(recovered.withdrawn(order.execution()).followups().isEmpty());
        assertTrue(recovered.retired(order.owner()).followups().isEmpty());
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.RUN,
                CombatLocomotion.plan(response,order.execution(),CombatEngagement.Key.of(order),TARGET.offset(3,0,0),rules,order.style()).mode());
    }
    @Test void ordinaryCombatApproachDoesNotGrantPermanentSprintBetweenBoundedAttacks(){
        var order=order(1,Optional.empty());var rules=MeleeRules.initial();var key=CombatEngagement.Key.of(order);
        var state=CombatState.empty().enlisted(order,100,rules);
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.WALK,
                CombatLocomotion.plan(state,order.execution(),key,TARGET.offset(8,0,0),rules,order.style()).mode());
        var strike=new MeleeStrike(order.owner(),1,new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution()),
                order.target(),order.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.LUNGE,1,101,100);
        var pending=state.prepared(strike);
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.RUN,
                CombatLocomotion.plan(pending,order.execution(),key,TARGET.offset(3,0,0),rules,order.style()).mode());
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.WALK,
                CombatLocomotion.plan(pending.completed(strike,110,rules),order.execution(),key,TARGET.offset(8,0,0),rules,order.style()).mode());
    }
    @Test void immediateDecisionHonoursVisibilityReadinessPendingEffectsAndExpiry(){
        var order=order(1,Optional.empty());
        assertEquals(MeleeTactics.Decision.ATTACK,MeleeTactics.decide(order,new MeleeTactics.Facts(100,true,true,true,false,100,true)));
        assertEquals(MeleeTactics.Decision.APPROACH,MeleeTactics.decide(order,new MeleeTactics.Facts(100,true,false,true,false,100,true)));
        assertEquals(MeleeTactics.Decision.WAIT,MeleeTactics.decide(order,new MeleeTactics.Facts(100,true,true,true,false,101,true)));
        assertEquals(MeleeTactics.Decision.WAIT,MeleeTactics.decide(order,new MeleeTactics.Facts(100,true,true,true,true,100,true)));
        assertEquals(MeleeTactics.Decision.WAIT,MeleeTactics.decide(order,new MeleeTactics.Facts(100,true,true,true,false,100,false)),
                "unavailable effort or equipment cannot turn valid physical contact into a blocked attack station");
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
        var moved=new CombatOrder(order.execution(),order.revision(),order.weapon(),order.party(),order.engagement(),TARGET.offset(1,0,0),100,2000,order.actorPosition(),order.navigation(),order.station(),MECHANICS);
        assertFalse(state.deferred(moved,1102),"target movement reopens tactical admission");
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        assertEquals(state,CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertThrows(IllegalArgumentException.class,()->MeleePosition.Reason.require(99));
    }
    @Test void holdingUsesTwoStrictWeaponBoundariesAndCannotExpandAttackReach(){
        var rules=MeleeRules.initial();assertEquals(2.2,rules.holdReach(SWORD,false,MECHANICS),0.0001);assertEquals(2.55,rules.holdReach(SWORD,true,MECHANICS),0.0001);
        assertEquals(4,rules.windup(MeleeAttack.QUICK));assertEquals(8,rules.windup(MeleeAttack.LUNGE));
        var order=order(1,Optional.empty());
        assertEquals(MeleeTactics.Decision.APPROACH,MeleeTactics.decide(order,new MeleeTactics.Facts(101,true,true,false,false,100,true)));
    }
    @Test void missingColumnsAreNotEvidenceThatEveryAttackPositionIsImpossible(){
        var unknown=new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry(){
            public WorldBounds bounds(){return new WorldBounds(-16,-16,32,32);}
            public SurfaceAnchor supportAt(int x,int z){return null;}
            public boolean blocked(SurfaceAnchor surface){return false;}
        };
        assertTrue(MeleePositions.knowledgeMissing(unknown,order(1,Optional.empty()),SWORD,3,false,MeleeRules.initial().engagements()));
        var multiLevel=new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry(){
            public WorldBounds bounds(){return new WorldBounds(-16,-16,32,32);}
            public SurfaceAnchor supportAt(int x,int z){return SurfaceAnchor.at(x,63,z);}
            public List<SurfaceAnchor> surfacesAt(int x,int z){return List.of(SurfaceAnchor.at(x,63,z),SurfaceAnchor.at(x,66,z));}
            public boolean blocked(SurfaceAnchor surface){return true;}
        };
        assertFalse(MeleePositions.knowledgeMissing(multiLevel,order(1,Optional.empty()),SWORD,3,false,MeleeRules.initial().engagements()));
        var outerUnknown=new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry(){
            public WorldBounds bounds(){return new WorldBounds(-16,-16,32,32);}
            public SurfaceAnchor supportAt(int x,int z){return SurfaceAnchor.at(x,63,z);}
            public boolean blocked(SurfaceAnchor surface){return false;}
            public boolean clearanceKnown(SurfaceAnchor surface){return CombatEngagement.distance(surface,TARGET)<=3;}
        };
        assertFalse(MeleePositions.knowledgeMissing(outerUnknown,order(1,Optional.empty()),SWORD,6,false,MeleeRules.initial().engagements()));
        assertTrue(MeleePositions.knowledgeMissing(outerUnknown,order(1,Optional.empty()),SWORD,6,true,MeleeRules.initial().engagements()),
                "known close reach cannot classify an unknown outer arc as an impossible flank");
    }
    @Test void sweepAnswersObservedCirclingButIsDistinctFromRetreatPunishment(){
        var order=order(1,Optional.empty());var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var rules=MeleeRules.initial();
        assertEquals(MeleeAttack.SWEEP,MeleeTactics.attack(CombatState.empty(),order,actor,1,100,SWORD,1.8,0,0.2,rules));
        assertEquals(MeleeAttack.QUICK,MeleeTactics.attack(CombatState.empty(),order,actor,1,100,SWORD,1.8,0,0,rules));
        assertEquals(MeleeAttack.QUICK,MeleeTactics.attack(CombatState.empty(),order,actor,1,100,SWORD,3,0.2,0.2,rules));
        assertFalse(MeleeAttack.SWEEP.closesDistance());
        assertTrue(rules.windup(MeleeAttack.SWEEP)>rules.windup(MeleeAttack.QUICK));
        assertFalse(MeleeAim.contains(0,0,-1,rules.techniques().sweepHalfAngleDegrees()));
        assertFalse(MeleeAim.contains(0,1,0,rules.engagements().lungeTurnDegrees()),"a committed lunge can be sidestepped");
    }
    @Test void attacksAndBothManeuversSpendOneRecoverableAccountAndHydrationCannotRefillIt() throws IOException {
        var rules=MeleeRules.initial();var costs=rules.readiness();var order=order(1,Optional.empty());
        var state=CombatState.empty().observed(new CombatState.Armament(order.execution(),order.weapon(),SWORD))
                .spent(order.owner(),100,costs.burstCost(),rules).spent(order.owner(),101,costs.lungeCost(),rules)
                .spent(order.owner(),102,costs.backstepCost(),rules);
        assertFalse(state.affords(order.owner(),103,costs.quickCost(),rules));
        var finalState=state;
        assertThrows(IllegalArgumentException.class,()->finalState.spent(order.owner(),103,costs.quickCost(),rules));
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        var recovered=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(state,recovered);
        assertFalse(recovered.affords(order.owner(),118,costs.quickCost(),rules),"recovery delay survives restart");
        assertTrue(recovered.affords(order.owner(),150,costs.burstCost(),rules));
        assertEquals(costs.capacity(),recovered.readiness().get(order.owner()).available(Long.MAX_VALUE,costs));
        assertThrows(IllegalArgumentException.class,()->finalState.affords(order.owner(),99,1,rules));
        assertTrue(recovered.retired(order.owner()).readiness().isEmpty());
    }
    @Test void consecutiveWavesCannotExceedTheLiveAttackLimit(){
        var a=order(1,Optional.empty());var b=order(2,Optional.empty());var c=order(3,Optional.empty());var rules=MeleeRules.initial();
        var state=CombatState.empty().enlisted(a,100,rules).enlisted(b,100,rules).enlisted(c,100,rules);
        var first=new ActorActuationId(new ActorBodyId(a.execution().actorId(),1),a.execution());
        var second=new ActorActuationId(new ActorBodyId(b.execution().actorId(),1),b.execution());
        var third=new ActorActuationId(new ActorBodyId(c.execution().actorId(),1),c.execution());
        state=state.prepared(new MeleeStrike(a.owner(),1,first,a.target(),a.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.LUNGE,1,101,100));
        state=state.prepared(new MeleeStrike(b.owner(),1,second,b.target(),b.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.QUICK,1,101,100));
        var plan=state.engagements().get(CombatEngagement.Key.of(c));
        plan=new CombatEngagement(plan.key(),plan.revision()+1,plan.anchor(),plan.axisX(),plan.axisZ(),plan.participants(),0,
                Optional.of(new CombatEngagement.Wave(c.targetPosition(),List.of(new CombatEngagement.Slot(third,100,110,false)))));
        state=state.withEngagement(plan);
        assertFalse(CombatEngagementPlanner.permitsStrike(state,c,third,100,rules));
        state=state.completed(state.pending().get(a.owner()),110,rules);
        assertTrue(CombatEngagementPlanner.permitsStrike(state,c,third,110,rules));
    }
    @Test void closeNativeOpportunityDoesNotWaitForAGroupWaveButCannotBorrowAnotherBodyOrExceedItsCap(){
        var rules=MeleeRules.initial();var a=order(1,Optional.empty());var b=order(2,Optional.empty());var c=order(3,Optional.empty());
        var first=new ActorActuationId(new ActorBodyId(a.execution().actorId(),1),a.execution());
        var second=new ActorActuationId(new ActorBodyId(b.execution().actorId(),1),b.execution());
        var third=new ActorActuationId(new ActorBodyId(c.execution().actorId(),1),c.execution());
        var state=CombatState.empty().enlisted(a,100,rules).enlisted(b,100,rules).enlisted(c,100,rules)
                .stance(new MeleeStance(first,a.target(),false,true,100)).stance(new MeleeStance(second,b.target(),true,true,100))
                .stance(new MeleeStance(third,c.target(),true,true,100));
        assertTrue(CombatEngagementPlanner.permitsStrike(state,a,first,MeleeAttack.SWEEP,100,rules));
        assertTrue(CombatEngagementPlanner.permitsStrike(state,a,first,MeleeAttack.QUICK,100,rules));
        assertFalse(CombatEngagementPlanner.permitsStrike(state.stance(new MeleeStance(first,a.target(),false,false,100)),a,first,MeleeAttack.QUICK,100,rules),
                "preferred positioning is optional but real contact readiness remains required");
        assertFalse(CombatEngagementPlanner.permitsStrike(state,a,first,MeleeAttack.LUNGE,100,rules),"long approach still coordinates its commitment");
        assertFalse(CombatEngagementPlanner.permitsStrike(state,a,new ActorActuationId(new ActorBodyId(a.execution().actorId(),2),a.execution()),MeleeAttack.SWEEP,100,rules));
        assertFalse(CombatEngagementPlanner.permitsStrike(state,a,first,MeleeAttack.SWEEP,120,rules),"old native contact is not a fresh opportunity");
        state=CombatEngagementPlanner.committed(state,a,first).prepared(new MeleeStrike(a.owner(),1,first,a.target(),a.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.SWEEP,1,101,100));
        assertFalse(CombatEngagementPlanner.permitsStrike(state,a,first,MeleeAttack.SWEEP,100,rules),"same attempt is never admitted twice");
        state=state.prepared(new MeleeStrike(b.owner(),1,second,b.target(),b.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.QUICK,1,101,100));
        assertFalse(CombatEngagementPlanner.permitsStrike(state,c,third,MeleeAttack.SWEEP,100,rules),"immediate contact shares the same two-strike cap");
    }
    @Test void repeatedPressureSelectsOneLateralResponseAndRetainsItsSideAcrossHydration() throws IOException {
        var order=order(1,Optional.empty());var rules=MeleeRules.initial();var duel=rules.maneuvers().duel();
        var state=CombatState.empty().observed(new CombatState.Armament(order.execution(),order.weapon(),SWORD))
                .recalled(order,OptionalLong.of(100),100,duel).recalled(order,OptionalLong.of(120),120,duel);
        state=state.recalled(order,OptionalLong.of(120),121,duel);
        assertEquals(2,state.exchanges().get(order.owner()).hits(),"the same harm observation is not another blow");
        var allowed=EnumSet.allOf(MeleeManeuver.Kind.class);var facts=new MeleeManeuvers.Facts(2.8,0,true);
        var choice=MeleeDuelPolicy.choose(state,order,facts,121,rules,OptionalLong.of(120),allowed,true);
        assertEquals(MeleeDuelPolicy.Action.MANEUVER,choice.action());var kind=choice.maneuver().orElseThrow();assertTrue(kind.lateral());
        var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),state);
        var recovered=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(state,recovered);assertEquals(choice,MeleeDuelPolicy.choose(recovered,order,facts,121,rules,OptionalLong.of(120),allowed,true));
        var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var m=new MeleeManeuver(actor,order.weapon(),order.target(),kind,122,order.actorPosition(),TARGET.offset(2,0,-1),121,122,130,0,145,133,0);
        recovered=recovered.withExchange(order.owner(),recovered.exchanges().get(order.owner()).repositioned(kind)).maneuver(m);
        assertEquals(MeleeDuelPolicy.Action.ATTACK,MeleeDuelPolicy.choose(recovered,order,facts,134,rules,OptionalLong.of(120),allowed,true).action(),
                "old pressure cannot cause continuous circling after the step ends");
        assertEquals(MeleeDuelPolicy.Action.ATTACK,MeleeDuelPolicy.choose(recovered,order,facts,140,rules,OptionalLong.of(139),allowed,true).action(),
                "another blow cannot bypass the retained defensive cooldown");
        assertEquals(kind,MeleeDuelPolicy.choose(recovered,order,facts,145,rules,OptionalLong.of(139),allowed,true).maneuver().orElseThrow(),
                "a new exchange keeps its chosen side, rather than alternating every observation");
        assertEquals(MeleeDuelPolicy.Action.ATTACK,MeleeDuelPolicy.choose(state,order,facts,121,rules,OptionalLong.of(120),Set.of(),true).action(),
                "an unavailable role capability cannot freeze an otherwise valid contact strike");
    }
    @Test void missedExchangesPermitFootworkDuringWeaponRecoveryButNeverAnotherUnsettledStrike() {
        var order=order(1,Optional.empty());var rules=MeleeRules.initial();
        var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var first=new MeleeStrike(order.owner(),1,actor,order.target(),order.weapon(),new ItemCondition(250,0),SWORD,MeleeAttack.QUICK,1,101,100);
        var second=new MeleeStrike(order.owner(),1,actor,order.target(),order.weapon(),first.before(),SWORD,MeleeAttack.QUICK,1,121,120);
        var state=CombatState.empty().prepared(first).completed(first,110,rules,MeleeOutcome.MISS).prepared(second);
        var facts=new MeleeManeuvers.Facts(2.8,0);var allowed=EnumSet.allOf(MeleeManeuver.Kind.class);
        assertEquals(MeleeDuelPolicy.Action.WAIT,MeleeDuelPolicy.choose(state,order,facts,124,rules,OptionalLong.empty(),allowed,true).action());
        state=state.completed(second,130,rules,MeleeOutcome.MISS);
        assertTrue(state.recovering(order.owner(),131));
        assertTrue(MeleeDuelPolicy.choose(state,order,facts,131,rules,OptionalLong.empty(),allowed,true).maneuver().orElseThrow().lateral());
        assertEquals(136L,state.nextAttack().get(order.owner()),"footwork does not shorten the weapon's cadence");
        var memory=state.exchanges().get(order.owner());
        assertFalse(memory.pressured(130+rules.maneuvers().duel().pressureWindowTicks()+1,rules.maneuvers().duel()));
        assertTrue(state.retired(order.owner()).exchanges().isEmpty(),"retired combat owners release their bounded memory");
    }
    @Test void lateralCandidatesBreakTheFrontalLineAndRespectHeightAndEngagementBounds() {
        var order=order(1,Optional.empty());var rules=ManeuverRules.initial();
        var candidates=List.of(TARGET.offset(2,0,-1),TARGET.offset(3,1,-1),TARGET.offset(2,0,1),TARGET.offset(1,0,0),TARGET.offset(3,3,-1));
        var left=MeleeManeuvers.rank(order,MeleeManeuver.Kind.SIDESTEP_LEFT,candidates,rules);
        assertFalse(left.isEmpty());assertTrue(left.stream().allMatch(point->point.z()>order.actorPosition().z()));
        assertFalse(left.contains(TARGET.offset(3,3,-1)),"side selection cannot invent a climb outside body capability");
        var right=MeleeManeuvers.rank(order,MeleeManeuver.Kind.SIDESTEP_RIGHT,candidates,rules);
        assertEquals(List.of(TARGET.offset(2,0,1)),left);
        assertTrue(right.stream().allMatch(point->point.z()<order.actorPosition().z()));
    }
    @Test void pursuitUsesBoundedObservedLeadAndHandsContactToTheWeaponBeforeItsEndpoint() throws IOException {
        var order=order(1,Optional.empty());var rules=MeleeRules.initial();var policy=rules.maneuvers().pursuit();
        var combat=CombatState.empty().observed(new CombatState.Armament(order.execution(),order.weapon(),SWORD));
        var far=order.atActorObservation(TARGET.offset(8,0,0));
        var facts=new MeleeManeuvers.Facts(7.5,.2,false,0,.25);
        assertEquals(Optional.of(MeleeManeuver.Kind.PURSUIT_RUN),MeleeDuelPolicy.choose(combat,far,facts,100,rules,
                OptionalLong.empty(),EnumSet.allOf(MeleeManeuver.Kind.class),false).maneuver());
        var lead=MeleePursuit.lead(facts,policy);assertEquals(0,lead.x());assertEquals(1.5,lead.z());
        var excessive=MeleePursuit.lead(new MeleeManeuvers.Facts(8,0,false,100,100),policy);
        assertTrue(Math.hypot(excessive.x(),excessive.z())<=policy.maximumLeadMicros()/1_000_000.0+1e-9);
        var candidates=List.of(TARGET.offset(3,0,-1),TARGET.offset(3,0,1),TARGET.offset(9,0,0),TARGET.offset(3,5,0));
        var ranked=MeleePursuit.rank(combat,far,candidates,facts,100,rules);
        assertEquals(TARGET.offset(3,0,1),ranked.getFirst(),"observed travel favours a real reachable-side candidate");
        assertFalse(ranked.contains(TARGET.offset(9,0,0)),"running cannot increase the pressure gap");
        var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var run=new MeleeManeuver(actor,order.weapon(),order.target(),MeleeManeuver.Kind.PURSUIT_RUN,101,
                far.actorPosition(),TARGET.offset(3,0,1),100,102,0,0,0,112,170);
        combat=combat.maneuver(run).stance(new MeleeStance(actor,order.target(),true,true,104));
        assertTrue(CombatEngagementPlanner.contactHandoff(combat,order,actor,MeleeAttack.QUICK,104,rules));
        assertEquals(MeleeDuelPolicy.Action.ATTACK,MeleeDuelPolicy.choose(combat,order,new MeleeManeuvers.Facts(2,0),104,rules,
                OptionalLong.empty(),EnumSet.allOf(MeleeManeuver.Kind.class),true).action());
        combat=combat.maneuver(run.closed(104));var bytes=new ByteArrayOutputStream();CombatStateCodec.write(new DataOutputStream(bytes),combat);
        var recovered=CombatStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertFalse(recovered.maneuvers().get(order.owner()).ready(MeleeManeuver.Kind.PURSUIT_RUN,150));
        assertFalse(recovered.maneuverBlocksStrike(order.owner(),104,rules.maneuvers()),"run recovery does not lock attacks");
        assertTrue(recovered.maneuvers().get(order.owner()).ready(MeleeManeuver.Kind.PURSUIT_RUN,170));
    }
    @Test void contactHandoffCannotBorrowAnEvasionStaleStanceOrBody(){
        var rules=MeleeRules.initial();var order=order(1,Optional.empty());
        var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var m=new MeleeManeuver(actor,order.weapon(),order.target(),MeleeManeuver.Kind.BURST,101,order.actorPosition(),TARGET.offset(1,0,0),100,104,0,200,0,140,0);
        var state=CombatState.empty().enlisted(order,105,rules).maneuver(m).stance(new MeleeStance(actor,order.target(),true,true,105));
        assertTrue(CombatEngagementPlanner.contactHandoff(state,order,actor,MeleeAttack.QUICK,105,rules));
        assertFalse(CombatEngagementPlanner.contactHandoff(state,order,actor,MeleeAttack.LUNGE,105,rules));
        assertFalse(CombatEngagementPlanner.contactHandoff(state,order,actor,MeleeAttack.QUICK,125,rules));
        assertFalse(CombatEngagementPlanner.contactHandoff(state,order,new ActorActuationId(new ActorBodyId(order.execution().actorId(),2),order.execution()),MeleeAttack.QUICK,105,rules));
        assertFalse(CombatEngagementPlanner.contactHandoff(state,order,actor,MeleeAttack.QUICK,103,rules));
        var evasion=new MeleeManeuver(actor,m.weapon(),m.target(),MeleeManeuver.Kind.BACKSTEP,101,m.origin(),m.station(),100,104,0,200,0,140,0);
        assertFalse(CombatEngagementPlanner.contactHandoff(state.maneuver(evasion),order,actor,MeleeAttack.QUICK,105,rules));
        assertTrue(evasion.closed(105).blocksStrike(105,rules.maneuvers()));
        assertFalse(m.closed(105).blocksStrike(105,rules.maneuvers()));
        assertTrue(m.closed(105).busy(105,rules.maneuvers()),"attack handoff does not grant a second maneuver");
    }
    @Test void allMechanicalIngressHasAnExactCodecAndRejectsTruncation(){
        var order=order(1,Optional.of(TARGET.offset(1,0,0)));var actor=new ActorActuationId(new ActorBodyId(order.execution().actorId(),1),order.execution());
        var events=List.<CombatEvent>of(new CombatEvent.WeaponDefined(order.execution(),order.weapon(),SWORD),new CombatEvent.WeaponObserved(actor,order.weapon(),SWORD),
                new CombatEvent.PositionBlocked(actor,order.target(),order.station().orElseThrow()),
                new CombatEvent.ManeuverRequested(actor,order.target(),order.actorPosition(),new MeleeManeuvers.Facts(5,0.2,true)),
                new CombatEvent.ManeuverSelected(new MeleeManeuver(actor,order.weapon(),order.target(),MeleeManeuver.Kind.BURST,101,order.actorPosition(),order.actorPosition().offset(1,0,0),100,0,0,200,0,140,0)),
                new CombatEvent.ManeuverLaunched(actor,101),new CombatEvent.ManeuverClosed(order.owner(),101),
                new CombatEvent.StanceObserved(new MeleeStance(actor,order.target(),true,true,100)),
                new CombatEvent.PositionPlanned(order.execution(),order.target(),order.targetPosition(),order.station(),MeleePosition.Reason.RESERVED));
        var codecs=CombatPayloadCodecs.create();
        for(var event:events){var bytes=codecs.encode(event);assertEquals(event,codecs.decode(event.type(),bytes));
            assertThrows(IllegalArgumentException.class,()->codecs.decode(event.type(),Arrays.copyOf(bytes,bytes.length-1)));}
    }
}
