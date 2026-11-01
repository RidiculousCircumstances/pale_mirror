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
            new SecurityEvent.Incident(ready.duty().home(),ready.body(),ready.offender(),1),21000);}
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
        assertThrows(IllegalArgumentException.class,()->new SecurityEvent.Incident(r.duty().home(),r.body(),r.offender(),0));
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
        assertEquals(Optional.of(r.duty().id()),ActorInventoryInteractionFences.pendingOwner(state,r.body().body().actorId()));
        assertFalse(ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.GUARD).checkpoint(state,r.duty().execution()).ready());
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));var pending=state;
        assertThrows(IllegalArgumentException.class,()->SecurityTransitions.apply(pending,r.duty().id(),new SecurityEvent.Completed(r.duty().id(),strike.attemptOrdinal(),true,item.condition()),21002));
        state=SecurityTransitions.apply(state,r.duty().id(),new SecurityEvent.Completed(r.duty().id(),strike.attemptOrdinal(),true,item.condition().wear(1)),21002);
        assertTrue(state.security().pending().isEmpty());assertEquals(item.condition().wear(1),state.inventory().items().get(item.id()).condition());
        assertEquals(21015L,state.security().nextAttack().get(r.duty().id()));
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
        state=new GuardMissionSelected(owner.id(),owner.revision(),owner.mission().orElseThrow().post().mission()).apply(state,owner.id(),21100);
        var current=state;var duty=state.strategicPlans().guards().get(owner.id());
        assertThrows(IllegalArgumentException.class,()->new GuardMissionSelected(duty.id(),duty.revision(),mission.watching(21260)).apply(current,duty.id(),21100));
        var distant=new GuardMission(target.kind(),target.id(),station.offset(100,0,0),6,0);
        assertThrows(IllegalArgumentException.class,()->new GuardMissionSelected(duty.id(),duty.revision(),distant).apply(current,duty.id(),21100));
        assertTrue(GuardResponse.contact(state,duty,21100).isEmpty());
        assertTrue(state.security().hostile(duty.home(),r.offender()));
    }

}
