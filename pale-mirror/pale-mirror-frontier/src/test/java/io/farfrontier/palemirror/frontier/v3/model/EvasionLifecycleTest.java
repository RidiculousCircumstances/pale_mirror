package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** The actual resource-owning farmer -> meal -> safety -> meal chain, not mocked owner checkpoints. */
class EvasionLifecycleTest {
    @Test void nestedSafetyPreservesTheExactMealWorkCargoBodyAndRestartReferences(){
        var fixture=ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);var state=fixture.state();var job=fixture.job();
        state=HarvestObservationFixture.inspectGoal(state,fixture.site(),job,fixture.lease().id());
        state=ResourceSiteHarvestProcessTest.completeLabourHot(state,fixture.site(),fixture.lease().id());
        var actual=state.actorLocations().get(job.workerId());
        state=state.transitionSceneLease(fixture.lease().id(),SceneLeaseStatus.DRAINING);
        state=state.releaseSceneLease(fixture.lease().id(),List.of(new SceneMemberPosition(job.workerId(),actual.body(),actual.condition().health())));
        var home=new SubjectId("settlement:1");var stock=state.inventory().fungibleResources().transformCold(
                ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(home)),Map.of(new SubjectId("lot:bootstrap-1-wheat"),64),Map.of(),
                new ResourceLot(new SubjectId("lot:safety-bread"),home,"minecraft:bread",64,"test",List.of()));
        state=state.withInventory(state.inventory().withFungibleResources(stock));
        var workExecution=state.actorExecutions().current(ActorActivityKind.FIELD_HARVEST).get(job.workerId());
        var selected=ResidentMealProcess.selectSourceAtYield(state,job.workerId(),27000).orElseThrow();
        state=ResidentMealProcess.reduceStarted(state,job.workerId(),selected);
        var actor=job.workerId();var meal=state.humanPopulation().meals().get(actor);var inventory=state.inventory();
        var body=ActorBodyAuthority.current(state,actor);var threat=new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(23,45),"pale_mirror:frontier_graybox");
        var position=state.actorLocations().get(actor).supportingSurface();
        state=EvasionTransitions.apply(state,actor,new EvasionEvent.Threatened(home,body,threat,position,Optional.of(position.offset(1,0,0))),27001);
        var episode=state.combat().evasions().get(actor);var escape=state.actorExecutions().next(actor,ActorActivityKind.SAFETY,episode.id());
        state=EvasionTransitions.apply(state,actor,new EvasionEvent.Started(actor,episode.id(),escape),27001);
        assertEquals(List.of(meal.executionId(),workExecution),state.actorExecutions().actors().get(actor).continuations());
        assertEquals(inventory,state.inventory());assertEquals(body,ActorBodyAuthority.current(state,actor));
        // External loss of the reserved source retires the paused meal, never its safety successor or underlying work.
        var claim=state.inventory().fungibleResources().claims().get(meal.claimId());
        var forfeiture=new ResidentMealForfeitureOwner();forfeiture.validate(state,claim,meal.sourceAccountId());
        var lost=forfeiture.settle(state,List.of(claim),FungibleForfeitureSettlement.from(state,state.inventory()));
        lost.executions().requireCurrent(escape);
        assertEquals(List.of(workExecution),lost.executions().actors().get(actor).continuations());
        assertFalse(lost.population().meals().containsKey(actor));
        assertEquals(ResidentActivityChoice.Kind.SAFETY,ResidentActivityCoordinator.assess(state,actor,27002).kind());
        // Paused food is retained custody, not a second current movement owner.
        var destination=position.offset(-2,0,0);
        var travelling=EvasionTransitions.apply(state,actor,new EvasionEvent.Destination(escape,episode.revision(),destination),27002);
        var escapeLeg=travelling.combat().evasions().get(actor);
        var movement=new ActorMovement(escapeLeg.order(),27002,new ActorMovementContext.EvasionLeg(escapeLeg.id(),escapeLeg.revision()),escape);
        travelling=io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.reduceStarted(travelling,actor,new ActorMovementStarted(movement));
        assertEquals(movement,travelling.actorMovements().get(actor));assertEquals(meal,travelling.humanPopulation().meals().get(actor));
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));
        assertEquals(List.of(meal.executionId(),workExecution),state.actorExecutions().actors().get(actor).continuations());
        var waiting=state;assertThrows(IllegalArgumentException.class,()->EvasionTransitions.apply(waiting,actor,new EvasionEvent.Ended(actor,episode.id(),episode.revision()),27002));
        state=EvasionTransitions.apply(state,actor,new EvasionEvent.Ended(actor,episode.id(),episode.revision()),27102);
        assertEquals(ResidentActivityChoice.Kind.EAT,ResidentActivityCoordinator.assess(state,actor,27102).kind());
        var successor=state.actorExecutions().next(actor,ActorActivityKind.MEAL,meal.claimId());
        var planned=io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess.review(actor,27102),27102);
        var continuations=new ArrayList<io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect>();
        int eventNumber=0;
        for(var proposed:planned){
            if(proposed.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect effect){
                FrontierWorldStateTransitionValidator.INSTANCE.validateScheduleEffect(state,proposed.subject(),effect);
                continuations.add(effect);
            }else{
                var id=new CommandId("command:resume-meal-"+(++eventNumber));
                state=io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.reduce(state,
                    new FrontierEvent(1,new EventId("event:resume-meal-"+eventNumber),new TransactionId("transaction:resume-meal"),
                        state.bootstrap().worldId(),new Revision(eventNumber),new SimInstant(27102),proposed.subject(),CauseChain.root(id),proposed.payload()));
            }
        }
        var continuation=continuations.stream().filter(effect->effect instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled rescheduled
                && rescheduled.scheduleId().equals(meal.progressScheduleId())).findFirst().orElseThrow();
        var resumed=assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,continuation);
        assertEquals(ResidentMealProcess.progress(state.humanPopulation().meals().get(actor),27103),resumed.replacement());
        assertThrows(IllegalArgumentException.class,()->io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog.requireReconsideration(resumed.replacement()),
                "an exact meal continuation must remain forbidden as a coalescible policy hint");
        assertEquals(successor,state.humanPopulation().meals().get(actor).executionId());
        assertEquals(meal.progressScheduleId(),state.humanPopulation().meals().get(actor).progressScheduleId());
        assertEquals(meal.portion(),state.humanPopulation().meals().get(actor).portion());assertEquals(inventory,state.inventory());
        assertTrue(state.actorExecutions().retainsSuspended(actor,workExecution.activityKind(),job.id()));
        assertEquals(state,codec.decode(codec.encode(state)));
    }
    @Test void requestedSafetyWaitsForTheExactPhysicalSceneToReleaseTheBody(){
        var fixture=ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);var state=fixture.state();var actor=fixture.job().workerId();
        var point=state.actorLocations().get(actor).supportingSurface();
        state=EvasionTransitions.apply(state,actor,new EvasionEvent.Threatened(new SubjectId("settlement:1"),ActorBodyAuthority.current(state,actor),
                new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(23,45),"pale_mirror:frontier_graybox"),point,Optional.of(point.offset(1,0,0))),27001);
        assertTrue(ActorExecutionCoordinator.sceneOwns(state,actor));
        var held=state;var episode=state.combat().evasions().get(actor);
        var successor=state.actorExecutions().next(actor,ActorActivityKind.SAFETY,episode.id());
        assertThrows(IllegalArgumentException.class,()->EvasionTransitions.apply(held,actor,new EvasionEvent.Started(actor,episode.id(),successor),27002));
        var events=io.farfrontier.palemirror.frontier.v3.process.EvasionProcess.review(state,
                io.farfrontier.palemirror.frontier.v3.process.EvasionProcess.at(actor,27002),27002);
        assertTrue(events.stream().noneMatch(e->e.payload() instanceof EvasionEvent.Started));
        assertTrue(ResidentActivityCoordinator.shouldYieldAtOwnerCheckpoint(state,actor,27002));
    }
    @Test void saferDestinationsIncreaseThreatSeparationAndStillPreferShortReachableShelter(){
        var origin=SurfaceAnchor.at(0,63,0);var threat=origin.offset(2,0,0);
        var away=origin.offset(-12,0,0);var toward=origin.offset(8,0,0);var near=origin.offset(-2,0,0);
        var ranked=EvasionDestinations.rank(origin,Optional.of(threat),List.of(toward,near,away,origin),List.of(away),EvasionRules.initial());
        assertEquals(away,ranked.getFirst());assertFalse(ranked.contains(origin));assertFalse(ranked.contains(toward));
        // Moving through the threat to an equally exposed point is not an escape merely because it is farther from home.
        assertFalse(EvasionDestinations.rank(origin,Optional.of(threat),List.of(origin.offset(1,0,0)),List.of(),EvasionRules.initial()).contains(origin.offset(1,0,0)));
    }
    @Test void perResidentFactorsAndGaitsUseTheSamePaceForNativeRequestsAndColdClocks(){
        var state=ResourceSiteHarvestProcessTest.initial();var actor=state.humanPopulation().residents().keySet().iterator().next();
        var walk=ResidentWalkingPace.read(state,actor,LocomotionMode.WALK);var run=ResidentWalkingPace.read(state,actor,LocomotionMode.RUN);
        assertEquals(walk.blocksPerTick()*1.5,run.blocksPerTick(),0.000001);
        var modifiers=ResidentMovementModifiers.initial().with(new SubjectId("effect:wound"),500);
        assertEquals(0.5,modifiers.multiplier());assertEquals(1.0,modifiers.without(new SubjectId("effect:wound")).multiplier());
    }
}
