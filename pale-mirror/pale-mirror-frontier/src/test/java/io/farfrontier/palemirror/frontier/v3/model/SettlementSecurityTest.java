package io.farfrontier.palemirror.frontier.v3.model;
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
        assertFalse(state.actorMovements().containsKey(duty.execution().actorId()));
        state=ActorBodyAuthority.demand(state,duty.execution().actorId());var body=ActorBodyAuthority.current(state,duty.execution().actorId());
        state=ActorBodyAuthority.running(state,body);
        return new Ready(state,duty,new ActorActuationId(body,duty.execution()),new UUID(42,17));
    }
    private static FrontierWorldState offence(Ready ready){return SecurityTransitions.apply(ready.state(),ready.duty().home(),
            new SecurityEvent.Incident(ready.duty().home(),ready.body().body(),ready.offender(),1),21000);}
    private static SecurityState.Contact contact(Ready ready){return new SecurityState.Contact(ready.duty().home(),
            new CombatTarget(CombatTarget.Kind.PLAYER,ready.offender(),"pale_mirror:frontier_graybox"),
            ready.state().actorLocations().get(ready.body().body().actorId()).supportingSurface().support().offset(1,0,0),21001,1);}
    @Test void confirmedIncidentPersistsByPlayerUuidButDoesNotCreateAnEternalPhysicalContact(){
        var r=ready();var state=offence(r);assertTrue(state.security().hostile(r.duty().home(),r.offender()));
        var seen=new SecurityEvent.Seen(r.body(),contact(r));
        state=SecurityTransitions.apply(state,r.duty().home(),seen,21001);
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));
        assertTrue(state.security().hostile(r.duty().home(),r.offender()));
        assertTrue(GuardResponse.contact(state,r.duty(),21002).isPresent());
        assertTrue(GuardResponse.contact(state,r.duty(),21100).isEmpty());
        assertTrue(state.security().hostile(r.duty().home(),r.offender()));
        assertThrows(IllegalArgumentException.class,()->new SecurityEvent.Incident(r.duty().home(),r.body().body(),r.offender(),0));
        assertThrows(IllegalArgumentException.class,()->SecurityTransitions.apply(r.state(),r.duty().home(),seen,21001));
    }
    @Test void oneGuardObservationWakesEveryAppointmentThroughTheRealRegisteredCommandBoundary(){
        var r=ready();var state=offence(r);var payload=new SecurityEvent.Seen(r.body(),contact(r));
        var registry=FrontierWorldRuntimeDefinition.processRegistry();
        var command=new FrontierCommand(FrontierCommand.SCHEMA_VERSION,new CommandId("test:security-shared"),state.bootstrap().worldId(),Revision.ZERO,
                new SimInstant(21001),FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,CauseChain.root(new CommandId("test:security-shared")),payload);
        var accepted=assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                FrontierWorldProcessCatalog.planCommand("security",state,command));
        var events=registry.validateEmissions("security",accepted.events());
        assertEquals(1+GuardEquipment.in(state,r.duty().home()).size(),events.size());
    }
    @Test void strikeRetainsExactBodyWeaponAndOutcomeAcrossRecoveryAndRejectsForgedWear(){
        var r=ready();var state=offence(r);state=SecurityTransitions.apply(state,r.duty().home(),new SecurityEvent.Seen(r.body(),contact(r)),21001);
        var item=state.inventory().items().get(r.duty().weapon());
        var strike=new SecurityState.Strike(r.duty().id(),r.duty().revision(),r.body(),contact(r).target(),item.id(),item.condition(),
                new WeaponProfile(item.itemKind(),6,13,3,1),17,21002,21001);
        state=SecurityTransitions.apply(state,r.duty().id(),new SecurityEvent.Prepared(strike),21001);
        state=SecurityTransitions.apply(state,r.duty().home(),new SecurityEvent.ContactEnded(r.body(),r.duty().home(),strike.target(),1),21002);
        assertEquals(strike,state.security().pending().get(r.duty().id()),"ending contact cannot discard an unsettled native effect");
        assertEquals(Optional.of(r.duty().id()),ActorInventoryInteractionFences.pendingOwner(state,r.body().body().actorId()));
        assertFalse(ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.GUARD).checkpoint(state,r.duty().execution()).ready());
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));var pending=state;
        assertThrows(IllegalArgumentException.class,()->SecurityTransitions.apply(pending,r.duty().id(),new SecurityEvent.Completed(r.duty().id(),strike.attemptOrdinal(),true,item.condition()),21002));
        state=SecurityTransitions.apply(state,r.duty().id(),new SecurityEvent.Completed(r.duty().id(),strike.attemptOrdinal(),true,item.condition().wear(1)),21002);
        assertTrue(state.security().pending().isEmpty());assertEquals(item.condition().wear(1),state.inventory().items().get(item.id()).condition());
        assertEquals(21015L,state.security().nextAttack().get(r.duty().id()));
    }
    @Test void endedOpportunityWakesTheWholeCohortWithoutErasingHostilityOrANewerContact() {
        var r=ready();var state=offence(r);
        state=SecurityTransitions.apply(state,r.duty().home(),new SecurityEvent.Seen(r.body(),contact(r)),21001);
        var ended=new SecurityEvent.ContactEnded(r.body(),r.duty().home(),contact(r).target(),1);
        var codecs=FrontierWorldRuntimeDefinition.payloadCodecs();assertEquals(ended,codecs.decode(ended.type(),codecs.encode(ended)));
        var command=new FrontierCommand(FrontierCommand.SCHEMA_VERSION,new CommandId("test:contact-ended"),state.bootstrap().worldId(),Revision.ZERO,
                new SimInstant(21002),FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,CauseChain.root(new CommandId("test:contact-ended")),ended);
        var plan=assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                FrontierWorldProcessCatalog.planCommand("security",state,command));
        assertEquals(1+GuardEquipment.in(state,r.duty().home()).size(),plan.events().size());
        var current=SecurityTransitions.apply(state,r.duty().home(),ended,21002);
        assertTrue(current.security().contacts().isEmpty());assertTrue(current.security().hostile(r.duty().home(),r.offender()));
        var newer=new SecurityState.Contact(contact(r).home(),contact(r).target(),contact(r).position(),21002,2);
        var refreshed=SecurityTransitions.apply(state,r.duty().home(),new SecurityEvent.Seen(r.body(),newer),21002);
        assertThrows(IllegalArgumentException.class,()->SecurityTransitions.apply(refreshed,r.duty().home(),ended,21003));
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
        state=SecurityTransitions.apply(state,r.duty().home(),new SecurityEvent.Seen(r.body(),contact(r)),21001);
        var target=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var station=target.observationAnchor().offset(3,0,0);
        var mission=new GuardMission(target.kind(),target.id(),station,5,0);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),mission).apply(state,r.duty().id(),21001);
        var owner=state.strategicPlans().guards().get(r.duty().id());
        var response=mission.responding(new SurfaceAnchor(contact(r).position()).offset(1,0,0),contact(r).target());
        state=new GuardMissionSelected(owner.id(),owner.revision(),response).apply(state,owner.id(),21001);
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));owner=state.strategicPlans().guards().get(owner.id());
        assertEquals(mission.post(),owner.mission().orElseThrow().post());
        var movedContact = new SecurityState.Contact(owner.home(),contact(r).target(),response.station().offset(4,0,0).support(),21002,2);
        var moved = SecurityTransitions.apply(state,owner.home(),new SecurityEvent.Seen(r.body(),movedContact),21002);
        assertTrue(GuardResponse.redirect(moved,owner,21002),"a target outside the admitted adjacent contact must trigger a new approach");
        assertTrue(GuardResponse.contact(state,owner,21100).isEmpty());
        assertTrue(GuardResponse.investigating(state,owner,21100));
        var unseenWeapon=state.inventory().items().get(owner.weapon());
        var unseenStrike=new SecurityState.Strike(owner.id(),owner.revision(),r.body(),contact(r).target(),owner.weapon(),
                unseenWeapon.condition(),new WeaponProfile(unseenWeapon.itemKind(),6,13,3,1),17,21101,21100);
        var investigatingState=state;var investigatingDuty=owner;
        assertThrows(IllegalArgumentException.class,()->SecurityTransitions.apply(investigatingState,investigatingDuty.id(),
                new SecurityEvent.Prepared(unseenStrike),21100),"an investigation cannot authorize an attack without a live contact");
        assertFalse(GuardResponse.redirect(state,owner,21100),"loss of sight must not cancel the retained detour before its investigation deadline");
        long investigationEnd=21001+state.bootstrap().ruleset().security().investigationTicks()+1;
        assertFalse(GuardResponse.investigating(state,owner,investigationEnd));
        assertTrue(GuardResponse.redirect(state,owner,investigationEnd));
        state=new GuardMissionSelected(owner.id(),owner.revision(),owner.mission().orElseThrow().post().mission()).apply(state,owner.id(),investigationEnd);
        var current=state;var duty=state.strategicPlans().guards().get(owner.id());
        assertThrows(IllegalArgumentException.class,()->new GuardMissionSelected(duty.id(),duty.revision(),mission.watching(21260)).apply(current,duty.id(),21100));
        var distant=new GuardMission(target.kind(),target.id(),station.offset(100,0,0),6,0);
        assertThrows(IllegalArgumentException.class,()->new GuardMissionSelected(duty.id(),duty.revision(),distant).apply(current,duty.id(),21100));
        assertTrue(GuardResponse.contact(state,duty,21100).isEmpty());
        assertTrue(state.security().hostile(duty.home(),r.offender()));
    }

    @Test void alarmWaitsForTheSharedHotMovementCheckpointBeforeRedirecting() {
        var r=ready();var state=offence(r);
        state=SecurityTransitions.apply(state,r.duty().home(),new SecurityEvent.Seen(r.body(),contact(r)),21001);
        var target=SecurityCoverage.posts(state,r.duty().home()).getFirst();
        var mission=new GuardMission(target.kind(),target.id(),target.observationAnchor().offset(3,0,0),0,0);
        state=new GuardMissionSelected(r.duty().id(),r.duty().revision(),mission).apply(state,r.duty().id(),21001);
        var duty=state.strategicPlans().guards().get(r.duty().id());var actor=duty.execution().actorId();
        var move=new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(duty.missionOrder(),21001,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.GuardMissionLeg(duty.id(),duty.revision()),duty.execution());
        var body=state.actorLocations().get(actor).body();
        var lease=new AmbientActorLease(actor,body,new SimInstant(21001),1,AmbientLeaseStatus.HOT,AmbientGoalKind.GUARD,body);
        state=state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(Map.of(actor,move)).ambientLeases(Map.of(actor,lease)));
        var action=GuardContinuation.progress(duty.id(),21002);
        var waiting=GuardWorkProcess.progress(state,action,21002);
        assertFalse(waiting.stream().anyMatch(event->event.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementInterrupted));
        assertTrue(waiting.stream().anyMatch(event->event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled));
        state=state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(actor,
                lease.withGoal(AmbientGoalKind.ACTOR_MOVEMENT,move.order().legalStations().getFirst().standingBody()))));
        var readyEvents=GuardWorkProcess.progress(state,action,21002);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementInterrupted.class,readyEvents.getFirst().payload());
    }

    @Test void harmObservesTheBodyEvenBeforeItsActuatorRunsAndRejectsAFalseEpoch() {
        var base=Fixture.ARMED;var duty=base.strategicPlans().guards().values().iterator().next();
        var state=ActorBodyAuthority.demand(base,duty.execution().actorId());var body=ActorBodyAuthority.current(state,duty.execution().actorId());
        assertThrows(IllegalArgumentException.class,()->ActorBodyAuthority.requireActuation(state,new ActorActuationId(body,duty.execution())));
        var fact=new SecurityEvent.Incident(duty.home(),body,new UUID(17,45),1);
        var codecs=FrontierWorldRuntimeDefinition.payloadCodecs();assertEquals(fact,codecs.decode(fact.type(),codecs.encode(fact)));
        var harmed=SecurityTransitions.apply(state,duty.home(),fact,21001);assertTrue(harmed.security().hostile(duty.home(),fact.attacker()));
        var falseBody=new ActorBodyId(body.actorId(),body.physicalEpoch()+1);
        assertThrows(IllegalArgumentException.class,()->SecurityTransitions.apply(state,duty.home(),
                new SecurityEvent.Incident(duty.home(),falseBody,fact.attacker(),1),21001));
    }

}
