package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SettlementSecurityTest {
    private static final class Fixture {static final FrontierWorldState ARMED=GuardDutyTest.armed();}
    private record Ready(FrontierWorldState state,GuardDuty duty,ActorActuationId body,UUID offender){}
    private Ready ready(){
        var state=Fixture.ARMED;var duty=state.strategicPlans().guards().values().iterator().next();
        for(var other:GuardEquipment.in(state,duty.home()))if(!other.id().equals(duty.id()))
            state=state.withActorBody(other.execution().actorId(),state.actorLocations().get(other.execution().actorId()).body().offset(40,0,0));
        assertFalse(state.actorMovements().containsKey(duty.execution().actorId()));
        state=ActorBodyAuthority.demand(state,duty.execution().actorId());var body=ActorBodyAuthority.current(state,duty.execution().actorId());
        state=ActorBodyAuthority.running(state,body);
        return new Ready(state,duty,new ActorActuationId(body,duty.execution()),new UUID(42,17));
    }
    private static FrontierWorldState apply(FrontierWorldState state,SubjectId owner,FrontierPayload payload,long tick){
        return payload instanceof CombatEvent combat?CombatTransitions.apply(state,owner,combat,tick,CombatComposition.CAPABILITIES)
                :SecurityTransitions.apply(state,owner,(SecurityEvent)payload,tick);
    }
    private static FrontierWorldState offence(Ready ready){return apply(ready.state(),ready.duty().home(),
            new SecurityEvent.Incident(ready.duty().home(),ready.body().body(),contact(ready).target(),contact(ready).position(),Optional.empty(),1),21000);}
    private static SecurityState.Contact contact(Ready ready){return new SecurityState.Contact(ready.duty().home(),
            new CombatTarget(CombatTarget.Kind.PLAYER,ready.offender(),"pale_mirror:frontier_graybox"),
            ready.state().actorLocations().get(ready.body().body().actorId()).supportingSurface().support().offset(1,0,0),21001,1);}
    private static SecurityEvent.Seen seen(Ready ready,SecurityState.Contact contact) {
        return new SecurityEvent.Seen(ready.body(),contact,ready.state().actorLocations().get(ready.body().body().actorId()).supportingSurface().support());
    }
    private static FrontierWorldState allocated(FrontierWorldState state,long tick) {
        var home=state.bootstrap().settlements().getFirst().id();
        state=new SecurityResponsesAllocated(home,SecurityDispatch.plan(state,home,tick)).apply(state,home,tick);
        // Canonical fixture supplies the actual sword attributes; native resolution has its adapter tests.
        var combat=state.combat();
        for(var duty:GuardEquipment.in(state,home))if(GuardEquipment.held(state,duty))combat=combat.observed(new CombatState.Armament(
                duty.execution(),duty.weapon(),new WeaponProfile(state.inventory().items().get(duty.weapon()).itemKind(),6,13,3,1)));
        return state.withChanges(FrontierWorldStateUpdate.begin().combat(combat));
    }
    @Test void confirmedIncidentPersistsByPlayerUuidButDoesNotCreateAnEternalPhysicalContact(){
        var r=ready();var state=offence(r);assertTrue(state.security().hostile(r.duty().home(),r.offender()));
        var seen=seen(r,contact(r));
        state=apply(state,r.duty().home(),seen,21001);
        state=allocated(state,21001);
        var codec=new FrontierWorldStateCodec();var beforeSnapshot=state.security();state=codec.decode(codec.encode(state));
        assertEquals(beforeSnapshot,state.security(),"awareness and tactical envelopes survive snapshot recovery");
        assertTrue(state.security().hostile(r.duty().home(),r.offender()));
        assertTrue(GuardResponse.contact(state,r.duty(),21002).isPresent());
        assertTrue(GuardResponse.contact(state,r.duty(),21100).isEmpty());
        assertTrue(state.security().hostile(r.duty().home(),r.offender()));
        assertThrows(IllegalArgumentException.class,()->new SecurityEvent.Incident(r.duty().home(),r.body().body(),contact(r).target(),contact(r).position(),Optional.empty(),0));
        assertThrows(IllegalArgumentException.class,()->apply(r.state(),r.duty().home(),seen,21001));
    }
    @Test void nativePerceptionDoesNotMeasureRangeFromAnOlderCanonicalTravelPose() {
        var r=ready();var state=offence(r);var actor=r.body().body().actorId();
        var oldOrigin=state.actorLocations().get(actor).body().offset(100,0,0);
        state=state.withActorBody(actor,oldOrigin);
        var observed=apply(state,r.duty().home(),seen(r,contact(r)),21001);
        assertEquals(contact(r),observed.security().contact(r.duty().home(),contact(r).target()));
        assertEquals(oldOrigin,observed.actorLocations().get(actor).body(),"perception cannot install a body pose or invent movement arrival");
        assertTrue(observed.security().contact(r.duty().home(),contact(r).target()).current(21001,60));
        var beyondHome=new SecurityState.Contact(r.duty().home(),contact(r).target(),contact(r).position().offset(1000,0,0),21002,2);
        var distant=apply(observed,r.duty().home(),seen(r,beyondHome),21002);
        assertTrue(SecurityDispatch.plan(distant,r.duty().home(),21002).isEmpty(),"native perception does not relax the domain pursuit envelope");
    }
    @Test void oneGuardObservationWakesEveryAppointmentThroughTheRealRegisteredCommandBoundary(){
        var r=ready();var state=offence(r);var payload=seen(r,contact(r));
        var registry=FrontierWorldRuntimeDefinition.processRegistry();
        var command=new FrontierCommand(FrontierCommand.SCHEMA_VERSION,new CommandId("test:security-shared"),state.bootstrap().worldId(),Revision.ZERO,
                new SimInstant(21001),FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,CauseChain.root(new CommandId("test:security-shared")),payload);
        var accepted=assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                FrontierWorldProcessCatalog.planCommand("security",state,command));
        var events=registry.validateEmissions("security",accepted.events());
        assertEquals(2,events.size(),"a new contact wakes its settlement allocator, not every guard");
    }
    @Test void strikeRetainsExactBodyWeaponAndOutcomeAcrossRecoveryAndRejectsForgedWear(){
        var r=ready();var state=offence(r);state=apply(state,r.duty().home(),seen(r,contact(r)),21001);
        state=allocated(state,21001);
        var item=state.inventory().items().get(r.duty().weapon());
        var strike=new MeleeStrike(r.duty().id(),r.duty().revision(),r.body(),contact(r).target(),item.id(),item.condition(),
                new WeaponProfile(item.itemKind(),6,13,3,1),MeleeAttack.QUICK,17,21002,21001);
        state=apply(state,r.duty().id(),new CombatEvent.Prepared(strike),21001);
        state=apply(state,r.duty().home(),new SecurityEvent.ContactEnded(r.body(),r.duty().home(),strike.target(),1),21002);
        assertEquals(strike,state.combat().pending().get(r.duty().id()),"ending contact cannot discard an unsettled native effect");
        assertEquals(Optional.of(r.duty().id()),ActorInventoryInteractionFences.pendingOwner(state,r.body().body().actorId()));
        assertFalse(ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.GUARD).checkpoint(state,r.duty().execution()).ready());
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));var pending=state;
        var forged=new MeleeStrike(strike.owner(),strike.ownerRevision()+1,strike.attacker(),strike.target(),strike.weapon(),strike.before(),strike.profile(),MeleeAttack.QUICK,
                strike.targetIncarnation(),strike.attemptOrdinal(),strike.preparedAt());
        assertThrows(IllegalArgumentException.class,()->pending.withChanges(FrontierWorldStateUpdate.begin().combat(CombatState.empty().prepared(forged))));
        assertThrows(IllegalArgumentException.class,()->apply(pending,r.duty().id(),new CombatEvent.Completed(r.duty().id(),strike.attemptOrdinal(),true,item.condition()),21002));
        state=apply(state,r.duty().id(),new CombatEvent.Completed(r.duty().id(),strike.attemptOrdinal(),true,item.condition().wear(1)),21009);
        assertTrue(state.combat().pending().isEmpty());assertEquals(item.condition().wear(1),state.inventory().items().get(item.id()).condition());
        assertEquals(21015L,state.combat().nextAttack().get(r.duty().id()));
    }
    @Test void endedOpportunityWakesTheWholeCohortWithoutErasingHostilityOrANewerContact() {
        var r=ready();var state=offence(r);
        state=apply(state,r.duty().home(),seen(r,contact(r)),21001);
        state=allocated(state,21001);
        var ended=new SecurityEvent.ContactEnded(r.body(),r.duty().home(),contact(r).target(),1);
        var codecs=FrontierWorldRuntimeDefinition.payloadCodecs();assertEquals(ended,codecs.decode(ended.type(),codecs.encode(ended)));
        var command=new FrontierCommand(FrontierCommand.SCHEMA_VERSION,new CommandId("test:contact-ended"),state.bootstrap().worldId(),Revision.ZERO,
                new SimInstant(21002),FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,CauseChain.root(new CommandId("test:contact-ended")),ended);
        var plan=assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                FrontierWorldProcessCatalog.planCommand("security",state,command));
        assertEquals(2+state.security().responses().size(),plan.events().size(),"only allocated responders are woken directly");
        var current=apply(state,r.duty().home(),ended,21002);
        assertTrue(current.security().contacts().isEmpty());assertTrue(current.security().hostile(r.duty().home(),r.offender()));
        var newer=new SecurityState.Contact(contact(r).home(),contact(r).target(),contact(r).position(),21002,2);
        var refreshed=apply(state,r.duty().home(),seen(r,newer),21002);
        var superseded=apply(refreshed,r.duty().home(),ended,21003);
        assertEquals(newer,superseded.security().contact(r.duty().home(),newer.target()));
        assertEquals(current,apply(current,r.duty().home(),ended,21003));
        assertTrue(ended.requiresDurableBeforeEffect());
        var retired=ActorBodyAuthority.released(current,r.body().body());
        assertEquals(retired,apply(retired,r.duty().home(),ended,21004),"observed end remains valid after exact body departure");
    }
    @Test void aRetainedPostOutsideTheCurrentProtectedAreaIsReplacedBeforeWatchingOrResuming() {
        var state = Fixture.ARMED;
        var duty = state.strategicPlans().guards().values().iterator().next();
        var target = SecurityCoverage.posts(state,duty.home()).getFirst();
        // An existing target may move as its worksite develops; identity survives.
        var formerPost = target.observationAnchor().offset(40,0,0);
        var mission = new GuardMission(target.kind(),target.id(),formerPost,5,0, GuardMission.Stage.PATROL);
        var stale = duty.withMission(mission);
        state = state.withStrategicPlans(state.strategicPlans().withGuard(stale))
                .withActorBody(duty.execution().actorId(),formerPost.standingBody());
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertFalse(GuardResponse.targetRetained(state,stale));
        assertTrue(GuardResponse.redirect(state,stale,21001));
        var responding = stale.withMission(mission.responding(formerPost.offset(1,0,0),
                new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(7,9),"pale_mirror:frontier_graybox")));
        assertFalse(GuardResponse.targetRetained(state,responding),"response must also revalidate its retained return post");
        var events = GuardWorkProcess.progress(state,GuardContinuation.progress(stale.id(),21001),21001);
        var selected = events.stream().map(ProposedEvent::payload).filter(GuardMissionSelected.class::isInstance)
                .map(GuardMissionSelected.class::cast).findFirst().orElseThrow();
        assertEquals(0,selected.mission().watchUntil(),"a stale arrival cannot begin watching the old post");
        var updated = selected.apply(state,stale.id(),21001);
        assertTrue(GuardResponse.targetRetained(updated,updated.strategicPlans().guards().get(stale.id())));
    }
    @Test void patrolTravelsAndWatchesThroughTheRealQueueWithRetainedPostsAcrossSnapshot() {
        var state=Fixture.ARMED;
        var base=FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var schedules=state.strategicPlans().guards().values().stream().map(duty->GuardContinuation.progress(duty.id(),21001)).toList();
        var engine=io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(
                new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(),state,new SimInstant(21000),
                        base.commandPlanner(),base.scheduledPlanner(),base.reducer(),base.stateCodec(),base.projectionMapper(),base.limits(),
                        schedules,base.transactionCommitter(),base.stateValidator(),base.executionMetrics(),base.kernelQuarantineReporter()));
        for(long tick=21200;tick<=41000;tick+=200) {
            var result=engine.advanceTo(new SimInstant(tick),new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1000,1000));
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE,result.status().kind(),result.status().toString());
        }
        var end=engine.canonicalState().state();
        assertTrue(end.strategicPlans().guards().values().stream().allMatch(duty->duty.mission().isPresent()
                && duty.mission().orElseThrow().visitOrdinal()>1),"both patrols must actually arrive, watch, and select successive posts");
        var codec=new FrontierWorldStateCodec();assertEquals(end,codec.decode(codec.encode(end)));
    }
    @Test void responsePreservesTheExactPostAndCannotInventAWatchArrivalOrDistantGoal() {
        var r=ready();var state=offence(r);
        state=apply(state,r.duty().home(),seen(r,contact(r)),21001);
        state=allocated(state,21001);
        var target=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var station=target.observationAnchor().offset(3,0,0);
        var mission=new GuardMission(target.kind(),target.id(),station,5,0, GuardMission.Stage.PATROL);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),mission).apply(state,r.duty().id(),21001);
        var owner=state.strategicPlans().guards().get(r.duty().id());
        var response=mission.responding(new SurfaceAnchor(contact(r).position()).offset(1,0,0),contact(r).target());
        state=new GuardMissionSelected(owner.id(),owner.revision(),response).apply(state,owner.id(),21001);
        var blocked=new CombatEvent.PositionBlocked(r.body(),contact(r).target(),response.station());
        var beforeBody=state.actorLocations().get(owner.execution().actorId()).body();
        state=apply(state,owner.id(),blocked,21001);
        assertTrue(GuardResponse.redirect(state,owner,21001),"failed physical melee position must trigger common reselection");
        assertEquals(beforeBody,state.actorLocations().get(owner.execution().actorId()).body(),"feedback cannot relocate the actor or award arrival");
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));owner=state.strategicPlans().guards().get(owner.id());
        assertTrue(state.combat().rejected(CombatComposition.CAPABILITIES.current(state,owner.execution(),21001).orElseThrow(),response.station(),21001,state.bootstrap().ruleset().melee()));
        assertEquals(mission.post(),owner.mission().orElseThrow().post());
        var movedContact = new SecurityState.Contact(owner.home(),contact(r).target(),response.station().offset(4,0,0).support(),21002,2);
        var moved = apply(state,owner.home(),seen(r,movedContact),21002);
        assertTrue(GuardResponse.redirect(moved,owner,21002),"a target outside the admitted adjacent contact must trigger a new approach");
        assertTrue(GuardResponse.contact(state,owner,21100).isEmpty());
        assertTrue(GuardResponse.investigating(state,owner,21100));
        var unseenWeapon=state.inventory().items().get(owner.weapon());
        var unseenStrike=new MeleeStrike(owner.id(),owner.revision(),r.body(),contact(r).target(),owner.weapon(),
                unseenWeapon.condition(),new WeaponProfile(unseenWeapon.itemKind(),6,13,3,1),MeleeAttack.QUICK,17,21101,21100);
        var investigatingState=state;var investigatingDuty=owner;
        assertThrows(IllegalArgumentException.class,()->apply(investigatingState,investigatingDuty.id(),
                new CombatEvent.Prepared(unseenStrike),21100),"an investigation cannot authorize an attack without a live contact");
        assertFalse(GuardResponse.redirect(state,owner,21100),"loss of sight must not cancel the retained detour before its investigation deadline");
        long investigationEnd=21001+state.bootstrap().ruleset().security().investigationTicks()+1;
        assertFalse(GuardResponse.investigating(state,owner,investigationEnd));
        assertTrue(GuardResponse.redirect(state,owner,investigationEnd));
        state=new GuardMissionSelected(owner.id(),owner.revision(),owner.mission().orElseThrow().post().mission()).apply(state,owner.id(),investigationEnd);
        var current=state;var duty=state.strategicPlans().guards().get(owner.id());
        assertThrows(IllegalArgumentException.class,()->new GuardMissionSelected(duty.id(),duty.revision(),mission.watching(21260)).apply(current,duty.id(),21100));
        var distant=new GuardMission(target.kind(),target.id(),station.offset(100,0,0),6,0, GuardMission.Stage.PATROL);
        assertThrows(IllegalArgumentException.class,()->new GuardMissionSelected(duty.id(),duty.revision(),distant).apply(current,duty.id(),21100));
        assertTrue(GuardResponse.contact(state,duty,21100).isEmpty());
        assertTrue(state.security().hostile(duty.home(),r.offender()));
    }

    @Test void alarmWaitsForTheSharedHotMovementCheckpointBeforeRedirecting() {
        var r=ready();var state=offence(r);
        state=apply(state,r.duty().home(),seen(r,contact(r)),21001);
        state=allocated(state,21001);
        var target=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var mission=new GuardMission(target.kind(),target.id(),target.observationAnchor().offset(3,0,0),0,0, GuardMission.Stage.PATROL);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),mission).apply(state,r.duty().id(),21001);
        var duty=state.strategicPlans().guards().get(r.duty().id());var actor=duty.execution().actorId();
        var move=new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(duty.missionOrder(),21001,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.GuardMissionLeg(duty.id(),duty.revision()),duty.execution());
        var body=state.actorLocations().get(actor).body();
        var lease=new AmbientActorLease(actor,body,new SimInstant(21001),1,AmbientLeaseStatus.HOT,AmbientGoalKind.GUARD,body);
        state=state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(Map.of(actor,move)).ambientLeases(Map.of(actor,lease)));
        var action=GuardContinuation.progress(duty.id(),21002);
        var weapon=state.inventory().items().get(duty.weapon());
        var strike=new MeleeStrike(duty.id(),duty.revision(),r.body(),contact(r).target(),duty.weapon(),weapon.condition(),
                new WeaponProfile(weapon.itemKind(),6,13,3,1),MeleeAttack.QUICK,17,21003,21002);
        var command=new FrontierCommand(FrontierCommand.SCHEMA_VERSION,new CommandId("test:approach-melee"),state.bootstrap().worldId(),Revision.ZERO,
                new SimInstant(21002),FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,CauseChain.root(new CommandId("test:approach-melee")),new CombatEvent.Prepared(strike));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Rejected.class,
                FrontierWorldProcessCatalog.planCommand("combat",state,command));
        var waiting=GuardWorkProcess.progress(state,action,21002);
        assertFalse(waiting.stream().anyMatch(event->event.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementInterrupted));
        assertTrue(waiting.stream().anyMatch(event->event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled));
        state=state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(actor,
                lease.withGoal(AmbientGoalKind.ACTOR_MOVEMENT,move.order().legalStations().getFirst().standingBody()))));
        var readyEvents=GuardWorkProcess.progress(state,action,21002);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementInterrupted.class,readyEvents.getFirst().payload());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Rejected.class,
                FrontierWorldProcessCatalog.planCommand("combat",state,command),"a patrol leg must be replaced by its owner before combat can reuse motion");
    }

    @Test void meleeRecoveryAllowsPursuitWhileTheWeaponCannotAttackAgain() {
        var r=ready();var state=offence(r);
        state=apply(state,r.duty().home(),seen(r,contact(r)),21001);
        state=allocated(state,21001);
        var post=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var patrol=new GuardMission(post.kind(),post.id(),post.observationAnchor().offset(3,0,0),0,0, GuardMission.Stage.PATROL);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),patrol).apply(state,r.duty().id(),21001);
        var duty=state.strategicPlans().guards().get(r.duty().id());
        var response=patrol.responding(new SurfaceAnchor(contact(r).position()).offset(1,0,0),contact(r).target());
        state=new GuardMissionSelected(duty.id(),duty.revision(),response).apply(state,duty.id(),21001);
        duty=state.strategicPlans().guards().get(duty.id());var item=state.inventory().items().get(duty.weapon());
        var strike=new MeleeStrike(duty.id(),duty.revision(),r.body(),contact(r).target(),duty.weapon(),item.condition(),
                new WeaponProfile(item.itemKind(),6,13,3,1),MeleeAttack.QUICK,17,21002,21001);
        state=apply(state,duty.id(),new CombatEvent.Prepared(strike),21001);
        state=apply(state,duty.id(),new CombatEvent.Completed(duty.id(),strike.attemptOrdinal(),true,item.condition().wear(1)),21009);
        assertFalse(duty.missionOrder().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface()));
        var result=GuardWorkProcess.progress(state,GuardContinuation.progress(duty.id(),21010),21010);
        assertTrue(result.stream().anyMatch(e->e.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled));
        assertTrue(result.stream().anyMatch(e->e.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted),"weapon recovery must not prevent lawful pursuit");
    }

    @Test void lostSightSearchRetainsPausesAndProbesAcrossSnapshotThenReturnsThroughTheRealQueue() {
        var r=ready();var state=offence(r);long observedAt=21001, lostAt=21062;
        state=apply(state,r.duty().home(),seen(r,contact(r)),observedAt);
        state=allocated(state,observedAt);
        var target=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var patrol=new GuardMission(target.kind(),target.id(),target.observationAnchor().offset(3,0,0),0,0,GuardMission.Stage.PATROL);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),patrol).apply(state,r.duty().id(),observedAt);
        var duty=state.strategicPlans().guards().get(r.duty().id());
        var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(state,duty.home(),List.of()).forActor(state,duty.execution().actorId());
        var approach=io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianInteractionStations.within(
                knowledge.geometry(),new SurfaceAnchor(contact(r).position()),2,Set.of()).getFirst();
        state=new GuardMissionSelected(duty.id(),duty.revision(),patrol.responding(approach,contact(r).target())).apply(state,duty.id(),observedAt);
        state=state.withActorBody(duty.execution().actorId(),approach.standingBody());
        state=selectMission(state,r.duty().id(),lostAt);
        assertEquals(GuardMission.Stage.SEARCH,state.strategicPlans().guards().get(duty.id()).mission().orElseThrow().stage());
        assertEquals("Search last known area",ResidentPresentation.from(state,duty.execution().actorId()).task());
        state=selectMission(state,r.duty().id(),lostAt+1);
        var searching=state.strategicPlans().guards().get(duty.id());
        assertEquals(lostAt+1+40,searching.mission().orElseThrow().search().orElseThrow().pauseUntil());
        var paused=state;
        assertThrows(IllegalArgumentException.class,()->new GuardMissionSelected(searching.id(),searching.revision(),
                searching.mission().orElseThrow().searching(approach.offset(1,0,0),1,0,1)).apply(paused,searching.id(),lostAt+2));
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));
        assertEquals(searching.mission(),state.strategicPlans().guards().get(duty.id()).mission());
        var later=new SecurityState.Contact(duty.home(),contact(r).target(),contact(r).position().offset(8,0,0),lostAt+2,2);
        var missedFreshWindow=apply(state,duty.home(),seen(r,later),lostAt+2);
        assertTrue(GuardResponse.redirect(missedFreshWindow,searching,lostAt+63),"a newer but already stale sighting invalidates the old search route");
        missedFreshWindow=selectMission(missedFreshWindow,duty.id(),lostAt+63);
        var reset=missedFreshWindow.strategicPlans().guards().get(duty.id()).mission().orElseThrow();
        assertEquals(2,reset.search().orElseThrow().sightingRevision());
        assertEquals(0,reset.search().orElseThrow().probe());
        assertTrue(reset.station().manhattanDistance(new SurfaceAnchor(later.position()))<=2);
        var refreshed=new SecurityState.Contact(duty.home(),contact(r).target(),contact(r).position(),lostAt+2,2);
        var reacquired=apply(state,duty.home(),seen(r,refreshed),lostAt+2);
        assertTrue(GuardResponse.redirect(reacquired,searching,lostAt+2));
        reacquired=selectMission(reacquired,duty.id(),lostAt+2);
        assertEquals(GuardMission.Stage.INTERCEPT,reacquired.strategicPlans().guards().get(duty.id()).mission().orElseThrow().stage());
        assertEquals(patrol.post(),reacquired.strategicPlans().guards().get(duty.id()).mission().orElseThrow().post());
        state=ActorBodyAuthority.released(state,r.body().body());
        var base=FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var engine=io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(
                new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(),state,new SimInstant(lostAt+1),
                        base.commandPlanner(),base.scheduledPlanner(),base.reducer(),base.stateCodec(),base.projectionMapper(),base.limits(),
                        List.of(GuardContinuation.progress(duty.id(),lostAt+41),SecurityReview.at(duty.home(),lostAt+2)),
                        base.transactionCommitter(),base.stateValidator(),base.executionMetrics(),base.kernelQuarantineReporter()));
        boolean probed=false;
        for(long tick=lostAt+100;tick<=lostAt+1200;tick+=100) {
            var result=engine.advanceTo(new SimInstant(tick),new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1000,1000));
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE,result.status().kind(),result.status().toString());
            probed|=engine.canonicalState().state().strategicPlans().guards().get(duty.id()).mission()
                    .flatMap(GuardMission::search).filter(search -> search.probe()>0).isPresent();
        }
        var end=engine.canonicalState().state();
        assertTrue(probed,"search must actually visit a further known point, not merely keep an active label");
        assertTrue(end.strategicPlans().guards().get(duty.id()).mission().orElseThrow().response().isEmpty());
        assertTrue(end.security().contacts().isEmpty());assertTrue(end.security().responses().isEmpty());
    }
    private static FrontierWorldState selectMission(FrontierWorldState state,SubjectId duty,long tick) {
        var selected=GuardWorkProcess.progress(state,GuardContinuation.progress(duty,tick),tick).stream()
                .map(ProposedEvent::payload).filter(GuardMissionSelected.class::isInstance).map(GuardMissionSelected.class::cast).findFirst().orElseThrow();
        return selected.apply(state,duty,tick);
    }
    @Test void allocationPublicationRejectsOverstaffingAndForeignDutyBeforeAnyMissionChanges() {
        var r=ready();var state=offence(r);
        state=apply(state,r.duty().home(),seen(r,contact(r)),21001);
        var overstaffed=new HashMap<SubjectId,SecurityState.Response>();
        for(var duty:GuardEquipment.in(state,r.duty().home()))overstaffed.put(duty.id(),
                new SecurityState.Response(duty.id(),new SecurityState.ContactKey(duty.home(),contact(r).target()),21001,
                        new CombatEngagement(contact(r).target(),CombatEngagement.Kind.REINFORCEMENT,
                                new SurfaceAnchor(FrontierWorldStateSupport.settlement(state.bootstrap(),duty.home()).anchor()),256)));
        var before=state;
        assertThrows(IllegalArgumentException.class,()->new SecurityResponsesAllocated(r.duty().home(),overstaffed).apply(before,r.duty().home(),21001));
        var allocated=allocated(state,21001);var response=allocated.security().responses().values().iterator().next();
        assertThrows(IllegalArgumentException.class,()->new SecurityResponsesAllocated(new SubjectId("settlement:foreign"),Map.of(response.duty(),response)));
        var codecs=FrontierWorldRuntimeDefinition.payloadCodecs();var event=new SecurityResponsesAllocated(r.duty().home(),allocated.security().responses());
        assertEquals(event,codecs.decode(event.type(),codecs.encode(event)));
        assertFalse(allocated.security().retired(response.duty()).responses().containsKey(response.duty()));
    }

    @Test void confirmedVisibleHarmAllocatesSelfDefenceBeforeTheNextSensorPass() {
        var r=ready();var point=r.state().actorLocations().get(r.body().body().actorId()).supportingSurface().support();
        var incident=new SecurityEvent.Incident(r.duty().home(),r.body().body(),contact(r).target(),point,Optional.of(point.offset(1,0,0)),1);
        var state=apply(r.state(),r.duty().home(),incident,21001);
        state=allocated(state,21001);
        assertEquals(CombatEngagement.Kind.SELF_DEFENCE,state.security().response(r.duty().id()).orElseThrow().engagement().kind());
        var codec=new FrontierWorldStateCodec();assertEquals(state.security(),codec.decode(codec.encode(state)).security());
        var code=FrontierWorldRuntimeDefinition.payloadCodecs();assertEquals(incident,code.decode(incident.type(),code.encode(incident)));
        var expired=allocated(state,21402);
        assertTrue(expired.security().awareness().sights().isEmpty());assertTrue(expired.security().awareness().harms().isEmpty());
        assertTrue(expired.security().hostile(r.duty().home(),r.offender()));
    }

    @Test void windupRejectsEarlyDamageAndCancellationReleasesTheActorWithoutWear(){
        var r=ready();var state=offence(r);state=apply(state,r.duty().home(),seen(r,contact(r)),21001);state=allocated(state,21001);
        var item=state.inventory().items().get(r.duty().weapon());
        var strike=new MeleeStrike(r.duty().id(),r.duty().revision(),r.body(),contact(r).target(),item.id(),item.condition(),new WeaponProfile(item.itemKind(),6,13,3,1),MeleeAttack.QUICK,17,21002,21001);
        var pending=apply(state,r.duty().id(),new CombatEvent.Prepared(strike),21001);
        assertEquals(CombatStatus.WINDING_UP,CombatStatus.read(pending,CombatComposition.CAPABILITIES,r.duty().execution(),21002));
        assertThrows(IllegalArgumentException.class,()->apply(pending,r.duty().id(),new CombatEvent.Completed(r.duty().id(),strike.attemptOrdinal(),true,item.condition().wear(1)),21002));
        var cancelled=apply(pending,r.duty().id(),new CombatEvent.Completed(r.duty().id(),strike.attemptOrdinal(),false,item.condition()),21002);
        assertEquals(item,cancelled.inventory().items().get(item.id()));assertTrue(cancelled.combat().pending().isEmpty());
        assertEquals(21014L,cancelled.combat().nextAttack().get(r.duty().id()));
    }
    @Test void nativeHoldingWithdrawsAnApproachWithoutInventingArrivalAndRejectsAForeignBody(){
        var r=ready();var state=offence(r);state=apply(state,r.duty().home(),seen(r,contact(r)),21001);state=allocated(state,21001);
        var post=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var patrol=new GuardMission(post.kind(),post.id(),post.observationAnchor().offset(3,0,0),0,0,GuardMission.Stage.PATROL);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),patrol).apply(state,r.duty().id(),21001);
        var duty=state.strategicPlans().guards().get(r.duty().id());
        state=new GuardMissionSelected(duty.id(),duty.revision(),patrol.responding(new SurfaceAnchor(contact(r).position()).offset(1,0,0),contact(r).target())).apply(state,duty.id(),21001);
        duty=state.strategicPlans().guards().get(duty.id());var body=state.actorLocations().get(duty.execution().actorId()).body();
        var before=state;
        var foreign=new ActorActuationId(new ActorBodyId(duty.execution().actorId(),999),duty.execution());
        assertThrows(IllegalArgumentException.class,()->apply(before,r.duty().id(),new CombatEvent.StanceObserved(new MeleeStance(foreign,contact(r).target(),true,21002)),21002));
        state=apply(state,duty.id(),new CombatEvent.StanceObserved(new MeleeStance(r.body(),contact(r).target(),true,21002)),21002);
        assertEquals(body,state.actorLocations().get(duty.execution().actorId()).body());
        assertTrue(state.combat().positions().containsKey(duty.id()),"native holding retains its bounded tactical sector rather than reassigning it to a peer");
        var events=GuardWorkProcess.progress(state,GuardContinuation.progress(duty.id(),21003),21003);
        assertTrue(events.stream().noneMatch(e->e.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted));
        assertFalse(duty.missionOrder().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface()));
    }
    @Test void preparedStrikeKeepsItsExactMissionMoveAndBlocksRoleReplacement() {
        var r=ready();var state=offence(r);state=apply(state,r.duty().home(),seen(r,contact(r)),21001);state=allocated(state,21001);
        var post=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var patrol=new GuardMission(post.kind(),post.id(),post.observationAnchor().offset(3,0,0),0,0,GuardMission.Stage.PATROL);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),patrol).apply(state,r.duty().id(),21001);
        var duty=state.strategicPlans().guards().get(r.duty().id());
        state=new GuardMissionSelected(duty.id(),duty.revision(),patrol.responding(new SurfaceAnchor(contact(r).position()).offset(1,0,0),contact(r).target())).apply(state,duty.id(),21001);
        duty=state.strategicPlans().guards().get(duty.id());
        var move=new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(duty.missionOrder(),21001,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.GuardMissionLeg(duty.id(),duty.revision()),duty.execution());
        state=io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.reduceStarted(state,duty.execution().actorId(),
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted(move));
        var item=state.inventory().items().get(duty.weapon());
        var strike=new MeleeStrike(duty.id(),duty.revision(),r.body(),contact(r).target(),item.id(),item.condition(),
                new WeaponProfile(item.itemKind(),6,13,3,1),MeleeAttack.LUNGE,1,21002,21001);
        state=apply(state,duty.id(),new CombatEvent.Prepared(strike),21001);
        var pending=state;var owner=duty;
        assertEquals(move,pending.actorMovements().get(duty.execution().actorId()));
        new io.farfrontier.palemirror.frontier.v3.process.GuardMissionMovementProvider().validate(pending,move);
        var codec=new FrontierWorldStateCodec();assertEquals(move,codec.decode(codec.encode(pending)).actorMovements().get(duty.execution().actorId()));
        assertFalse(ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.GUARD).checkpoint(pending,duty.execution()).ready());
        assertThrows(IllegalArgumentException.class,()->new GuardMissionSelected(owner.id(),owner.revision(),patrol).apply(pending,owner.id(),21002));
        assertTrue(GuardWorkProcess.progress(pending,GuardContinuation.progress(duty.id(),21002),21002).stream()
                .noneMatch(e->e.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementInterrupted));
    }
    @Test void deadGuardWithAnUnsettledAttemptCannotStartAnotherMove(){
        var r=ready();var state=offence(r);state=apply(state,r.duty().home(),seen(r,contact(r)),21001);state=allocated(state,21001);
        var item=state.inventory().items().get(r.duty().weapon());
        var strike=new MeleeStrike(r.duty().id(),r.duty().revision(),r.body(),contact(r).target(),item.id(),item.condition(),
                new WeaponProfile(item.itemKind(),6,13,3,1),MeleeAttack.LUNGE,1,21002,21001);
        state=apply(state,r.duty().id(),new CombatEvent.Prepared(strike),21001);
        var actor=r.duty().execution().actorId();var location=state.actorLocations().get(actor);
        state=ActorBodyAuthority.died(state,new ActorBodyDied(r.body().body(),location.body(),location.condition().health(),
                Optional.empty(),Optional.of(r.duty().execution()),"fixture:melee-death"),FrontierActorDeathConsequences.INSTANCE,21002);
        var events=GuardWorkProcess.progress(state,GuardContinuation.progress(r.duty().id(),21003),21003);
        assertTrue(events.stream().noneMatch(e->e.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted));
        assertEquals(strike,state.combat().pending().get(r.duty().id()),"native settlement still owns the possibly applied effect");
    }
    @Test void confirmedUnavailabilityReturnsToThePostWithoutCancellingItsReturnForTheSameDeferredTarget(){
        var r=ready();var state=offence(r);state=apply(state,r.duty().home(),seen(r,contact(r)),21001);state=allocated(state,21001);
        var post=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var patrol=new GuardMission(post.kind(),post.id(),post.observationAnchor().offset(3,0,0),0,0,GuardMission.Stage.PATROL);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),patrol).apply(state,r.duty().id(),21001);
        var duty=state.strategicPlans().guards().get(r.duty().id());
        state=new GuardMissionSelected(duty.id(),duty.revision(),patrol.responding(new SurfaceAnchor(contact(r).position()).offset(1,0,0),contact(r).target())).apply(state,duty.id(),21001);
        duty=state.strategicPlans().guards().get(duty.id());var order=CombatComposition.CAPABILITIES.current(state,duty.execution(),21001).orElseThrow();
        var noPath=new CombatEvent.PositionPlanned(order.execution(),order.target(),order.targetPosition(),Optional.empty(),MeleePosition.Reason.NO_PATH);
        state=apply(state,duty.id(),noPath,21001);
        state=apply(state,duty.home(),seen(r,new SecurityState.Contact(duty.home(),order.target(),contact(r).position(),21101,2)),21101);
        state=apply(state,duty.id(),noPath,21101);
        assertTrue(state.combat().deferred(CombatComposition.CAPABILITIES.current(state,duty.execution(),21101).orElseThrow(),21101));
        state=selectMission(state,duty.id(),21102);duty=state.strategicPlans().guards().get(duty.id());
        assertTrue(duty.mission().orElseThrow().response().isEmpty());
        assertFalse(GuardResponse.redirect(state,duty,21103),"the same deferred target must not immediately cancel return travel");
        var move=new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(duty.missionOrder(),21103,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.GuardMissionLeg(duty.id(),duty.revision()),duty.execution());
        state=state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(Map.of(duty.execution().actorId(),move)));
        var returning=GuardWorkProcess.progress(state,GuardContinuation.progress(duty.id(),21104),21104);
        assertTrue(returning.stream().map(ProposedEvent::payload).filter(GuardMissionSelected.class::isInstance)
                .map(GuardMissionSelected.class::cast).noneMatch(value->value.mission().response().isPresent()),"return travel is not another melee approach claim");
    }
    @Test void anotherCurrentFighterCannotClaimTheSameSpaceUntilTheFirstLeaseExpires(){
        var r=ready();var state=offence(r);state=apply(state,r.duty().home(),seen(r,contact(r)),21001);state=allocated(state,21001);
        var first=CombatComposition.CAPABILITIES.current(state,r.duty().execution(),21001).orElseThrow();
        var other=GuardEquipment.in(state,r.duty().home()).stream().filter(duty->!duty.id().equals(r.duty().id()) && GuardEquipment.held(r.state(),duty)).findFirst().orElseThrow();
        var station=new SurfaceAnchor(contact(r).position()).offset(1,0,0);
        var contender=new CombatOrder(other.execution(),other.revision(),other.weapon(),first.engagement(),first.targetPosition(),21001,1000,
                state.actorLocations().get(other.execution().actorId()).supportingSurface(),state.actorLocations().get(other.execution().actorId()).navigationProfile(),Optional.of(station));
        var occupied=apply(state,first.owner(),new CombatEvent.PositionPlanned(first.execution(),first.target(),first.targetPosition(),Optional.of(station),MeleePosition.Reason.RESERVED),21001);
        assertThrows(IllegalArgumentException.class,()->MeleePositions.requireClaim(occupied,contender,station,21002));
        MeleePositions.requireClaim(occupied,contender,station,21082);
        assertTrue(occupied.combat().retired(first.owner()).positions().isEmpty());
    }
    @Test void queuedApproachPublishesItsTypedWaitThroughTheDeclaredGuardProducer() throws Exception {
        var r=ready();var state=offence(r);state=apply(state,r.duty().home(),seen(r,contact(r)),21001);state=allocated(state,21001);
        var post=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var patrol=new GuardMission(post.kind(),post.id(),post.observationAnchor().offset(3,0,0),0,0,GuardMission.Stage.PATROL);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),patrol).apply(state,r.duty().id(),21001);
        var duty=state.strategicPlans().guards().get(r.duty().id());
        state=new GuardMissionSelected(duty.id(),duty.revision(),patrol.responding(new SurfaceAnchor(contact(r).position()).offset(1,0,0),contact(r).target())).apply(state,duty.id(),21001);
        duty=state.strategicPlans().guards().get(duty.id());
        try(var ignored=io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry,from,to)->{
            throw new io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,"queued shared dependency");
        })){
            var events=GuardWorkProcess.progress(state,GuardContinuation.progress(duty.id(),21002),21002);
            FrontierWorldRuntimeDefinition.processRegistry().validateEmissions("guard",events);
            var wait=events.stream().map(ProposedEvent::payload).filter(CombatEvent.PositionPlanned.class::isInstance)
                    .map(CombatEvent.PositionPlanned.class::cast).findFirst().orElseThrow();
            assertEquals(MeleePosition.Reason.ROUTE_PENDING,wait.reason());
            assertTrue(events.stream().noneMatch(e->e.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted));
        }
    }
    @Test void coldWeaponDefinitionEnablesPlanningButCannotAuthorizeANativeHit(){
        var r=ready();var state=offence(r);state=apply(state,r.duty().home(),seen(r,contact(r)),21001);state=allocated(state,21001);
        state=ActorBodyAuthority.released(state,r.body().body());state=state.withChanges(FrontierWorldStateUpdate.begin().combat(CombatState.empty()));
        var item=state.inventory().items().get(r.duty().weapon());var profile=new WeaponProfile(item.itemKind(),6,13,3,1);
        state=CombatTransitions.apply(state,r.duty().id(),new CombatEvent.WeaponDefined(r.duty().execution(),item.id(),profile),21002,CombatComposition.CAPABILITIES);
        assertEquals(profile,state.combat().armaments().get(r.duty().id()).profile());
        var strike=new MeleeStrike(r.duty().id(),r.duty().revision(),r.body(),contact(r).target(),item.id(),item.condition(),profile,MeleeAttack.QUICK,1,21003,21002);
        var cold=state;
        assertThrows(IllegalArgumentException.class,()->CombatTransitions.apply(cold,r.duty().id(),new CombatEvent.Prepared(strike),21002,CombatComposition.CAPABILITIES));
    }

    @Test void harmObservesTheBodyEvenBeforeItsActuatorRunsAndRejectsAFalseEpoch() {
        var base=Fixture.ARMED;var duty=base.strategicPlans().guards().values().iterator().next();
        var state=ActorBodyAuthority.demand(base,duty.execution().actorId());var body=ActorBodyAuthority.current(state,duty.execution().actorId());
        assertThrows(IllegalArgumentException.class,()->ActorBodyAuthority.requireActuation(state,new ActorActuationId(body,duty.execution())));
        var fact=new SecurityEvent.Incident(duty.home(),body,new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(17,45),"pale_mirror:frontier_graybox"),
                state.actorLocations().get(body.actorId()).supportingSurface().support(),Optional.empty(),1);
        var codecs=FrontierWorldRuntimeDefinition.payloadCodecs();assertEquals(fact,codecs.decode(fact.type(),codecs.encode(fact)));
        var harmed=apply(state,duty.home(),fact,21001);assertTrue(harmed.security().hostile(duty.home(),fact.attacker().entity()));
        var falseBody=new ActorBodyId(body.actorId(),body.physicalEpoch()+1);
        assertThrows(IllegalArgumentException.class,()->apply(state,duty.home(),
                new SecurityEvent.Incident(duty.home(),falseBody,fact.attacker(),fact.victimPosition(),Optional.empty(),1),21001));
    }

}
