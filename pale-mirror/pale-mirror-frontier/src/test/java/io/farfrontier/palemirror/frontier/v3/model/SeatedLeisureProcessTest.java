package io.farfrontier.palemirror.frontier.v3.model;

import static org.junit.jupiter.api.Assertions.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Canonical continuity and ownership, independently of native pose evidence. */
class SeatedLeisureProcessTest {
  private static final SubjectId ACTOR = new SubjectId("resident:1-12");
  private static final long NOW = 1000;
  static FrontierWorldState initial() {
    return FrontierV3FixtureCatalog.configuration("northwatch-leisure", new WorldId("frontier:leisure-test"),139).initialState();
  }
  static FrontierWorldState started() {
    var s = ResidentRoutineProcessTest.hotPresence(initial(), ACTOR);
    assertEquals(ResidentRoutine.Kind.SIT, ResidentRoutines.nextKind(s, ACTOR));
    assertTrue(ResidentRoutines.eligible(s, ACTOR, NOW));
    var place = ResidentRoutines.seats(s, ACTOR).getFirst();
    var request = new RoutineEvent.Requested(ResidentRoutineProcessTest.actuation(s, ACTOR), ResidentRoutine.Kind.SIT, place.approach(),Optional.of(place.id()));
    var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
    assertEquals(request,codecs.decode(request.type(),codecs.encode(request)));
    var plan = ResidentRoutineProcessTest.plan(s,request,NOW);
    var accepted = assertInstanceOf(CommandPlan.Accepted.class, plan, plan.toString());
    return ResidentRoutineProcessTest.apply(s,accepted.events(),NOW);
  }
  static FrontierWorldState departed(FrontierWorldState s) {
    s = AmbientLeaseStateProcess.transition(s, ACTOR, AmbientLeaseStatus.DRAINING);
    s = AmbientLeaseStateProcess.release(s,new AmbientLeaseReleased(ACTOR,s.actorLocations().get(ACTOR).body(),s.actorLocations().get(ACTOR).condition().health()));
    return ModeledActorBodyFacts.unloaded(s,ACTOR);
  }
  static FrontierWorldState arrived(FrontierWorldState s) {
    var m = s.actorMovements().get(ACTOR);var due = ActorMovementProcess.progress(m,NOW+1);
    for(int i=0;i<100 && s.actorMovements().containsKey(ACTOR);i++) {
      long at = due.dueAt().ticks();var events=ActorMovementProcess.plan(s,due,at);
      s=ResidentRoutineProcessTest.apply(s,events,at);
      for(var event:events) if(event.payload() instanceof ScheduleEffect.Rescheduled e && e.replacement().kind().equals(ActorMovementProcess.PROGRESS)) due=e.replacement();
    }
    assertFalse(s.actorMovements().containsKey(ACTOR));
    assertEquals(ResidentRoutine.Phase.SEATING,s.routines().rows().get(ACTOR).phase());
    return s;
  }
  static FrontierWorldState step(FrontierWorldState s,long tick) {
    var due=ResidentRoutineProcess.progress(s.routines().rows().get(ACTOR),tick);
    return ResidentRoutineProcessTest.apply(s,ResidentRoutineProcess.plan(s,due,tick),tick);
  }
  @Test void completeApproachActualSeatingTimedRestExitAndReleaseSurviveRecovery() {
    var s=started();var originalInventory=s.inventory();
    var exact=s.routines().rows().get(ACTOR).execution().orElseThrow();var selected=ResidentRoutines.use(s,s.routines().rows().get(ACTOR)).place();
    assertFalse(s.humanPopulation().seats().free(selected.id()));
    s=arrived(departed(s));long tick=s.routines().rows().get(ACTOR).phaseAt()+1;
    s=step(s,tick);assertEquals(SeatUse.Phase.SEATED,s.humanPopulation().seats().uses().get(ACTOR).phase());
    s=step(s,++tick);var row=s.routines().rows().get(ACTOR);
    assertEquals(ResidentRoutine.Phase.PAUSING,row.phase());assertEquals(tick,row.phaseAt());
    assertFalse(ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.LEISURE).checkpoint(s,exact).ready());
    s=new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(s));
    assertEquals(row,s.routines().rows().get(ACTOR));
    var before=s;assertThrows(IllegalArgumentException.class,()->ResidentRoutines.ended(before,new RoutineEvent.Ended(exact,row.sequence()),row.phaseAt()+1));
    long end=row.phaseAt()+row.pauseTicks();s=step(s,end);
    assertEquals(SeatUse.Phase.RESERVED,s.humanPopulation().seats().uses().get(ACTOR).phase());
    s=step(s,end+20);assertEquals(ResidentRoutine.Phase.CLOSED,s.routines().rows().get(ACTOR).phase());
    assertTrue(s.humanPopulation().seats().free(selected.id()));assertFalse(s.humanPopulation().seats().uses().containsKey(ACTOR));
    assertEquals(originalInventory,s.inventory());assertTrue(s.actorExecutions().actors().get(ACTOR).current().isEmpty());
  }
  @Test void incompleteForeignAndMissingSeatDeclarationsFailBeforeAdmissionOrRecovery() {
    var s=ResidentRoutineProcessTest.hotPresence(initial(),ACTOR);var body=ResidentRoutineProcessTest.actuation(s,ACTOR);var place=ResidentRoutines.seats(s,ACTOR).getFirst();
    assertThrows(IllegalArgumentException.class,()->new RoutineEvent.Requested(body,ResidentRoutine.Kind.SIT,place.approach()));
    var wrong=new RoutineEvent.Requested(body,ResidentRoutine.Kind.SIT,place.approach(),Optional.of(new SeatPlace.Id(place.id().buildingId(),"unknown")));
    assertInstanceOf(CommandPlan.Rejected.class,ResidentRoutineProcessTest.plan(s,wrong,NOW));
    var active=started();var corrupt=active.withHumanPopulation(active.humanPopulation().withSeats(SeatState.empty()));
    var codec=new FrontierWorldStateCodec();assertThrows(IllegalArgumentException.class,()->codec.decode(codec.encode(corrupt)));
    var row=active.routines().rows().get(ACTOR);var seat=ResidentRoutines.use(active,row);
    var other=active.actorExecutions().next(new SubjectId("resident:1-17"),ActorActivityKind.LEISURE,new SubjectId("resident:1-17"));
    assertThrows(IllegalArgumentException.class,()->active.humanPopulation().seats().reserve(SeatUse.reserve(other,seat.place(),NOW)));
  }
  @Test void hungerStandsBeforeRetiringLeisureAndOnlyThenMayBeginTheRealMeal() {
    var s=arrived(departed(started()));long tick=s.routines().rows().get(ACTOR).phaseAt()+1;
    s=step(s,tick);s=step(s,++tick);var row=s.routines().rows().get(ACTOR);var use=ResidentRoutines.use(s,row);
    var p=s.humanPopulation();
    var nutrition=ImmutableLookupMap.withEntry(p.nutrition(),ACTOR,new ResidentNutrition(ResidentNutritionStatus.HUNGRY,
        s.bootstrap().ruleset().residentLife().eatBelowUnits()-1,tick,0));
    s=s.withHumanPopulation(new HumanPopulation(p.households(),p.residents(),p.birthJobs(),p.health(),p.quarantines(),p.migrations(),p.provisions(),
        nutrition,p.medicalOperations(),p.schedules(),p.meals(),p.mealResourceObligations(),p.clinical(),p.restPlaces(),p.seats()));
    var capability=ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.LEISURE);
    assertTrue(tick<row.phaseAt()+row.pauseTicks());
    assertEquals(Optional.of(SeatUse.Action.EXIT),capability.seatAction(s,row.execution().orElseThrow(),use,tick));
    assertFalse(capability.checkpoint(s,row.execution().orElseThrow()).ready());
    s=step(s,++tick);assertEquals(SeatUse.Phase.RESERVED,ResidentRoutines.use(s,row).phase());
    s=step(s,++tick);assertEquals(ResidentRoutine.Phase.CLOSED,s.routines().rows().get(ACTOR).phase());
    assertEquals(use.place().egress(),s.actorLocations().get(ACTOR).supportingSurface());
    var meal=ResidentMealProcess.selectSourceAtYield(s,ACTOR,tick).orElseThrow();
    s=ResidentMealProcess.reduceStarted(s,ACTOR,meal);
    assertEquals(ActorActivityKind.MEAL,s.actorExecutions().actors().get(ACTOR).current().orElseThrow().activityKind());
    assertNotEquals(use.place().id(),s.humanPopulation().seats().uses().get(ACTOR).place().id());
  }
  @Test void displacementBeforeTheRestTimerCannotFabricateSeatedProgressOrRemountAnExitedSeat() {
    var s=arrived(departed(started()));long tick=s.routines().rows().get(ACTOR).phaseAt()+1;
    s=step(s,tick);var row=s.routines().rows().get(ACTOR);var use=ResidentRoutines.use(s,row);
    assertEquals(ResidentRoutine.Phase.SEATING,row.phase());
    var lease=AmbientActorProcess.nextLease(s,ACTOR,new SimInstant(++tick));
    s=AmbientLeaseStateProcess.transition(ModeledActorBodyFacts.present(AmbientLeaseStateProcess.prepare(s,lease),ACTOR),ACTOR,AmbientLeaseStatus.HOT);
    s=ModeledActorBodyFacts.inspected(s,ACTOR,use.place().egress().standingBody());
    s=step(s,++tick);assertEquals(ResidentRoutine.Phase.SEATING,s.routines().rows().get(ACTOR).phase());
    var authority=new ActorActuationId(ActorBodyAuthority.current(s,ACTOR),row.execution().orElseThrow());
    var capability=ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.LEISURE);
    assertEquals(Optional.of(SeatUse.Action.EXIT),capability.seatAction(s,row.execution().orElseThrow(),use,tick));
    s=SeatContacts.apply(s,ACTOR,new SeatEvent.Prepared(ACTOR,use.revision(),authority,SeatUse.Action.EXIT,SeatUse.ExecutionMode.LOCAL),tick);
    s=SeatContacts.apply(s,ACTOR,new SeatEvent.Checkpointed(ACTOR,use.revision(),authority,SeatUse.Action.EXIT,SeatUse.ExecutionMode.LOCAL,use.place().egress().standingBody()),tick);
    s=SeatContacts.apply(s,ACTOR,new SeatEvent.Changed(ACTOR,use.revision(),Optional.of(authority),SeatUse.Action.EXIT,SeatUse.ExecutionMode.LOCAL,true,use.place().egress().standingBody()),tick);
    s=step(s,++tick);assertEquals(ResidentRoutine.Phase.CLOSED,s.routines().rows().get(ACTOR).phase());
    assertFalse(s.humanPopulation().seats().uses().containsKey(ACTOR));
  }
  @Test void coldRestDoesNotInventFurnitureOverUnknownOrObservedRemovedBlocks() {
    var s=arrived(departed(started()));var row=s.routines().rows().get(ACTOR);var use=ResidentRoutines.use(s,row);long tick=row.phaseAt()+1;
    var unknown=io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation.unavailable(
        io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation.Kind.CHUNK_UNAVAILABLE);
    s=s.withChanges(FrontierWorldStateUpdate.begin().navigationGeometry(s.navigationGeometry().observe(s.navigationGeometry().revision(),Map.of(use.place().chair(),unknown))));
    s=step(s,tick);assertEquals(SeatUse.Phase.RESERVED,ResidentRoutines.use(s,row).phase());
    var air=new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState("minecraft:air",Map.of());
    var absent=io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation.known(
        new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell(io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationShape.EMPTY,true,Optional.of(air)));
    s=s.withChanges(FrontierWorldStateUpdate.begin().navigationGeometry(s.navigationGeometry().observe(s.navigationGeometry().revision(),Map.of(use.place().chair(),absent))));
    s=step(s,++tick);assertEquals(ResidentRoutine.Phase.CLOSED,s.routines().rows().get(ACTOR).phase());
    assertFalse(s.humanPopulation().seats().uses().containsKey(ACTOR));
    assertEquals(absent,s.navigationGeometry().cells().get(use.place().chair()));
  }
  @Test void pendingNativeEntryFencesYieldAndRetainsItsExactDeathObligationAcrossRecovery() {
    var s=arrived(departed(started()));var row=s.routines().rows().get(ACTOR);long tick=row.phaseAt()+1;
    var lease=AmbientActorProcess.nextLease(s,ACTOR,new SimInstant(tick));
    s=AmbientLeaseStateProcess.transition(ModeledActorBodyFacts.present(AmbientLeaseStateProcess.prepare(s,lease),ACTOR),ACTOR,AmbientLeaseStatus.HOT);
    var use=ResidentRoutines.use(s,row);var authority=new ActorActuationId(ActorBodyAuthority.current(s,ACTOR),row.execution().orElseThrow());
    s=SeatContacts.apply(s,ACTOR,new SeatEvent.Prepared(ACTOR,use.revision(),authority,SeatUse.Action.ENTER,SeatUse.ExecutionMode.LOCAL),tick);
    var pending=s;var capability=ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.LEISURE);
    assertFalse(capability.checkpoint(pending,row.execution().orElseThrow()).ready());
    assertThrows(IllegalArgumentException.class,()->capability.release(pending,row.execution().orElseThrow()));
    s=ModeledActorBodyFacts.died(s,ACTOR,s.actorLocations().get(ACTOR).body(),"pending-seat-death",tick+1);
    assertFalse(s.routines().rows().containsKey(ACTOR));
    s=new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(s));
    assertEquals(Optional.of(authority),s.humanPopulation().seats().uses().get(ACTOR).pendingBody());
    s=SeatContacts.apply(s,ACTOR,new SeatEvent.Changed(ACTOR,use.revision(),Optional.of(authority),SeatUse.Action.ENTER,SeatUse.ExecutionMode.LOCAL,false,s.actorLocations().get(ACTOR).body()),tick+2);
    assertFalse(s.humanPopulation().seats().uses().containsKey(ACTOR));
    assertNotEquals(ActorLifeStatus.ALIVE,s.actorLocations().get(ACTOR).condition().status());
  }
  @Test void unusedReservationTimesOutAndDeathRetiresExactRoutineSeatAndMovement() {
    var s=started();var row=s.routines().rows().get(ACTOR);var use=ResidentRoutines.use(s,row);
    var dead=ModeledActorBodyFacts.died(s,ACTOR,s.actorLocations().get(ACTOR).body(),"leisure-test",NOW+1);
    assertFalse(dead.routines().rows().containsKey(ACTOR));assertFalse(dead.humanPopulation().seats().uses().containsKey(ACTOR));assertFalse(dead.actorMovements().containsKey(ACTOR));
    s=arrived(departed(s));s=step(s,NOW+s.bootstrap().ruleset().residentLife().routines().walkingBudgetTicks()+1);
    assertEquals(ResidentRoutine.Phase.CLOSED,s.routines().rows().get(ACTOR).phase());assertTrue(s.humanPopulation().seats().free(use.place().id()));
  }
}
