package io.farfrontier.palemirror.frontier.v3.model;

import static org.junit.jupiter.api.Assertions.*;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Canonical lifecycle/ownership evidence; physical gestures and walking need the native pilot. */
class ResidentRoutineProcessTest {
  static final long NOW = 1000;

  static FrontierWorldState initial() {
    return FrontierWorldState.initial(
        FrontierBootstrapper.create(
            new WorldId("frontier:resident-routines"),
            139,
            FrontierRulesets.installed("frontier-v3-living-residents-r36")));
  }

  static FrontierWorldState hotPresence(FrontierWorldState s, SubjectId actor) {
    var presence = s.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
    s =
        s.withChanges(
            FrontierWorldStateUpdate.begin()
                .actorExecutions(
                    s.actorExecutions().begin(presence, s.actorExecutions().generation(actor))));
    var lease = AmbientActorProcess.nextLease(s, actor, new SimInstant(NOW));
    return AmbientLeaseStateProcess.transition(
        ModeledActorBodyFacts.present(AmbientLeaseStateProcess.prepare(s, lease), actor),
        actor,
        AmbientLeaseStatus.HOT);
  }

  static ActorActuationId actuation(FrontierWorldState s, SubjectId actor) {
    return new ActorActuationId(
        ActorBodyAuthority.current(s, actor),
        s.actorExecutions().actors().get(actor).current().orElseThrow());
  }

  private static long sequence;

  static FrontierWorldState apply(FrontierWorldState state, List<ProposedEvent> events, long tick) {
    var result =
        FrontierWorldState.duringUnpublishedTransition(
            () -> {
              var next = state;
              for (var event : events) {
                if (event.payload() instanceof ScheduleEffect) continue;
                var id = Long.toString(++sequence);
                next =
                    FrontierWorldRuntimeDefinition.reduce(
                        next,
                        new FrontierEvent(
                            FrontierEvent.SCHEMA_VERSION,
                            new EventId("event:routine-" + id),
                            new TransactionId("transaction:routine-" + id),
                            state.bootstrap().worldId(),
                            new Revision(sequence),
                            new SimInstant(tick),
                            event.subject(),
                            CauseChain.root(new CommandId("test:routine")),
                            event.payload()));
              }
              return next;
            });
    result.validateTransitionFrom(state);
    return result;
  }

  static CommandPlan plan(FrontierWorldState s, RoutineEvent.Requested request, long tick) {
    var id = new CommandId("test:routine-start-" + (++sequence));
    return FrontierWorldRuntimeDefinition.planCommand(
        s,
        new FrontierCommand(
            FrontierCommand.SCHEMA_VERSION,
            id,
            s.bootstrap().worldId(),
            Revision.ZERO,
            new SimInstant(tick),
            FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
            CauseChain.root(id),
            request));
  }

  @Test
  void incompleteForeignStaleOrOrphanedRoutineDeclarationsFailClosedBeforePublication() {
    var state = initial();
    var home = state.bootstrap().settlements().getFirst();
    var basis = state;
    var actor =
        home.residents().stream()
            .map(Resident::id)
            .filter(id -> ResidentRoutines.nextKind(basis, id) != ResidentRoutine.Kind.STROLL)
            .findFirst()
            .orElseThrow();
    state = hotPresence(state, actor);
    var exact = actuation(state, actor);
    var kind = ResidentRoutines.nextKind(state, actor);
    var goal = state.actorLocations().get(actor).supportingSurface();
    assertThrows(NullPointerException.class, () -> new RoutineEvent.Requested(exact, null, goal));
    assertThrows(
        NullPointerException.class,
        () ->
            new ActorExecutionId(
                actor, ActorActivityKind.LEISURE, null, exact.execution().generation() + 1));
    assertThrows(
        IllegalArgumentException.class, () -> new RoutineEvent.Arrived(exact.execution(), 1));
    var wrongBody =
        new ActorActuationId(
            new ActorBodyId(actor, exact.body().physicalEpoch() + 1), exact.execution());
    assertInstanceOf(
        CommandPlan.Rejected.class,
        plan(state, new RoutineEvent.Requested(wrongBody, kind, goal), NOW));
    var wrongKind =
        new ActorActuationId(
            exact.body(),
            new ActorExecutionId(
                actor, ActorActivityKind.MEAL, actor, exact.execution().generation()));
    assertInstanceOf(
        CommandPlan.Rejected.class,
        plan(state, new RoutineEvent.Requested(wrongKind, kind, goal), NOW));
    var request = new RoutineEvent.Requested(exact, kind, goal);
    state =
        apply(
            state,
            assertInstanceOf(CommandPlan.Accepted.class, plan(state, request, NOW)).events(),
            NOW);
    var row = state.routines().rows().get(actor);
    var use = row.execution().orElseThrow();
    var codec = new FrontierWorldStateCodec();
    var missingOwner =
        state.withChanges(
            FrontierWorldStateUpdate.begin().routines(state.routines().without(actor)));
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.decode(codec.encode(missingOwner)),
        "a current execution cannot hydrate without its declared routine");
    var missingExecution =
        FrontierWorldState.duringUnpublishedTransition(
            () ->
                missingOwner.withChanges(
                    FrontierWorldStateUpdate.begin()
                        .routines(new ResidentRoutineState(Map.of(actor, row)))
                        .actorExecutions(missingOwner.actorExecutions().finish(use))));
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.decode(codec.encode(missingExecution)),
        "a retained routine cannot hydrate without its current execution");
    var other = home.residents().get(10).id();
    if (other.equals(actor)) other = home.residents().get(11).id();
    var wrongKey =
        state.withChanges(
            FrontierWorldStateUpdate.begin()
                .routines(new ResidentRoutineState(Map.of(other, row))));
    assertThrows(IllegalArgumentException.class, () -> codec.decode(codec.encode(wrongKey)));
    var current = state;
    assertThrows(
        IllegalArgumentException.class,
        () -> ResidentRoutines.ended(current, new RoutineEvent.Ended(use, row.sequence()), NOW + 1),
        "an interval cannot manufacture an early completion");
  }

  @Test
  void expiredPauseWaitsForExactNativeHealingReceiptAcrossRecovery() {
    var actor = new SubjectId("resident:1-14");
    var state = hotPresence(SeatedLeisureProcessTest.initial(), actor);
    var kind = ResidentRoutines.nextKind(state, actor);
    assertEquals(ResidentRoutine.Kind.LOOK_AROUND, kind);
    var request = new RoutineEvent.Requested(actuation(state, actor), kind,
        state.actorLocations().get(actor).supportingSurface());
    state = apply(state, assertInstanceOf(CommandPlan.Accepted.class, plan(state, request, NOW)).events(), NOW);
    var row = state.routines().rows().get(actor);
    long due = row.phaseAt() + row.pauseTicks();
    var location = state.actorLocations().get(actor);
    state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(
        ImmutableLookupMap.withEntry(state.actorLocations(), actor,
            location.withObserved(location.body(), location.condition().withHealth(FixedScalar.whole(19))))));
    state = io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalTransitions.apply(state, actor,
        new io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalEvent.Vitality(actor, Optional.empty(), NOW + 1), NOW + 1);
    var wound = state.humanPopulation().clinical().wounds().get(actor);
    var healing = new io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalHealing(actuation(state, actor),
        location.condition().health().raw() - FixedScalar.SCALE,
        io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalAuthority.recovery(state, wound, due),
        due, wound.harmRevision(), due + 1);
    state = io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalTransitions.apply(state, actor,
        new io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalEvent.HealingPrepared(healing), due);
    var codec = new FrontierWorldStateCodec();
    state = codec.decode(codec.encode(state));
    var pending = state;
    var capability = ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.LEISURE);
    assertFalse(capability.checkpoint(state, row.execution().orElseThrow()).ready());
    assertThrows(IllegalArgumentException.class, () -> capability.release(pending, row.execution().orElseThrow()));
    var action = ResidentRoutineProcess.progress(row, due);
    var wait = ResidentRoutineProcess.plan(state, action, due);
    assertEquals(1, wait.size());
    assertInstanceOf(ScheduleEffect.Rescheduled.class, wait.getFirst().payload());
    assertEquals(healing, state.humanPopulation().clinical().healing().get(actor));
    assertEquals(row, state.routines().rows().get(actor));
    state = io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalTransitions.apply(state, actor,
        new io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalEvent.Recovered(actor, Optional.of(healing), due, healing.amount(), false), due + 1);
    var resumed = ResidentRoutineProcess.progress(row, due + 20);
    state = apply(state, ResidentRoutineProcess.plan(state, resumed, due + 20), due + 20);
    assertEquals(ResidentRoutine.Phase.CLOSED, state.routines().rows().get(actor).phase());
    assertTrue(state.actorExecutions().actors().get(actor).current().isEmpty());
    assertFalse(state.humanPopulation().clinical().healing().containsKey(actor));
    assertEquals(FixedScalar.whole(19), state.actorLocations().get(actor).condition().health());
  }

  @Test
  void nativeFixtureDeclaresFourDistinctBodiesWithoutPreseedingActivitiesOrOutcomes() {
    var config =
        FrontierV3FixtureCatalog.configuration(
            "northwatch-routines", new WorldId("frontier:routine-fixture"), 139);
    var state = config.initialState();
    var bodies = new HashSet<BodyPosition>();
    for (var id : List.of("resident:1-11", "resident:1-12", "resident:1-14", "resident:1-17")) {
      var actor = new SubjectId(id);
      assertTrue(bodies.add(state.actorLocations().get(actor).body()));
      assertTrue(HumanAssignmentProjection.compile(state).idle(actor));
      assertFalse(state.actorMovements().containsKey(actor));
      assertFalse(state.humanPopulation().meals().containsKey(actor));
      assertFalse(state.routines().rows().containsKey(actor));
      assertEquals(768, state.humanPopulation().nutrition(actor).satietyUnits());
    }
  }

  @Test
  void variedFinitePausesRetainTheirClockAcrossSnapshotAndCannotRestartBeforeCooldown() {
    var state = initial();
    var home = state.bootstrap().settlements().getFirst();
    var seen = EnumSet.noneOf(ResidentRoutine.Kind.class);
    int completed = 0;
    for (var resident : home.residents()) {
      var actor = resident.id();
      if (!HumanAssignmentProjection.compile(state).idle(actor)) continue;
      var kind = ResidentRoutines.nextKind(state, actor);
      seen.add(kind);
      if (kind == ResidentRoutine.Kind.STROLL) continue;
      state = hotPresence(state, actor);
      assertTrue(ResidentRoutines.eligible(state, actor, NOW));
      var request =
          new RoutineEvent.Requested(
              actuation(state, actor), kind, state.actorLocations().get(actor).supportingSurface());
      var payloads = FrontierWorldRuntimeDefinition.payloadCodecs();
      assertEquals(request, payloads.decode(request.type(), payloads.encode(request)));
      var accepted = assertInstanceOf(CommandPlan.Accepted.class, plan(state, request, NOW));
      state = apply(state, accepted.events(), NOW);
      var row = state.routines().rows().get(actor);
      assertEquals(ResidentRoutine.Phase.PAUSING, row.phase());
      assertFalse(ResidentRoutines.eligible(state, actor, NOW));
      var stale = state;
      assertInstanceOf(CommandPlan.Rejected.class, plan(stale, request, NOW + 1));
      var codec = new FrontierWorldStateCodec();
      state = codec.decode(codec.encode(state));
      assertEquals(row, state.routines().rows().get(actor));
      var due = ResidentRoutineProcess.progress(row, NOW + row.pauseTicks());
      state =
          apply(
              state,
              ResidentRoutineProcess.plan(state, due, due.dueAt().ticks()),
              due.dueAt().ticks());
      assertEquals(ResidentRoutine.Phase.CLOSED, state.routines().rows().get(actor).phase());
      assertTrue(state.actorExecutions().actors().get(actor).current().isEmpty());
      assertFalse(ResidentRoutines.eligible(state, actor, NOW + row.pauseTicks() + 1));
      completed++;
      if (completed == 3 && seen.size() == 4) break;
    }
    assertEquals(4, seen.size());
    assertTrue(completed >= 3);
    assertTrue(state.routines().active().isEmpty());
  }

  @Test
  void hungerReleasesOnlyTheRoutineAndPreservesTheNewMealAndItsResources() {
    var state = initial();
    var home = state.bootstrap().settlements().getFirst();
    var basis = state;
    var actor =
        home.residents().stream()
            .map(Resident::id)
            .filter(a -> ResidentRoutines.nextKind(basis, a) != ResidentRoutine.Kind.STROLL)
            .findFirst()
            .orElseThrow();
    var pantry = CommunalDiningPlan.initialFoodStorage(state.bootstrap(), home.id());
    state =
        AutomaticStationProductionTest.supply(
            state,
            state.inventory().containers().get(pantry),
            "minecraft:bread",
            64,
            "routine-food-yield");
    var people = state.humanPopulation();
    var nutrition =
        ImmutableLookupMap.withEntry(
            people.nutrition(),
            actor,
            new ResidentNutrition(ResidentNutritionStatus.NOURISHED, 668, NOW, 0));
    state =
        state.withHumanPopulation(
            new HumanPopulation(
                people.households(),
                people.residents(),
                people.birthJobs(),
                people.health(),
                people.quarantines(),
                people.migrations(),
                people.provisions(),
                nutrition,
                people.medicalOperations(),
                people.schedules(),
                people.meals(),
                people.mealResourceObligations(),
                people.clinical(),
                people.restPlaces(),
                people.seats()));
    state = hotPresence(state, actor);
    var request =
        new RoutineEvent.Requested(
            actuation(state, actor),
            ResidentRoutines.nextKind(state, actor),
            state.actorLocations().get(actor).supportingSurface());
    state =
        apply(
            state,
            assertInstanceOf(CommandPlan.Accepted.class, plan(state, request, NOW)).events(),
            NOW);
    var row = state.routines().rows().get(actor);
    var body = state.actorLocations().get(actor).body();
    long hungryAt = NOW + 100;
    state = state.withHumanPopulation(state.humanPopulation().accrueHunger(actor, hungryAt));
    assertTrue(
        state
            .humanPopulation()
            .nutrition(actor)
            .wantsFood(state.bootstrap().ruleset().residentLife()));
    var meal = ResidentMealProcess.selectSourceAtYield(state, actor, hungryAt).orElseThrow();
    state = ResidentMealProcess.reduceStarted(state, actor, meal);
    RoutineRelationships.validate(state);
    assertEquals(ResidentRoutine.Phase.CLOSED, state.routines().rows().get(actor).phase());
    assertEquals(body, state.actorLocations().get(actor).body());
    assertEquals(meal.meal(), state.humanPopulation().meals().get(actor));
    assertEquals(
        meal.meal().executionId(),
        state.actorExecutions().actors().get(actor).current().orElseThrow());
    assertEquals(
        meal.meal().portion().lotQuantities(),
        state.inventory().fungibleResources().claims().get(meal.meal().claimId()).lotQuantities());
    assertEquals(row.sequence(), state.routines().rows().get(actor).sequence());
  }

  @Test
  void commonColdStrollActuallyArrivesPausesAndClosesAcrossRecovery() {
    var state = initial();
    var home = state.bootstrap().settlements().getFirst();
    SubjectId actor = null;
    for (var p : home.residents())
      if (ResidentRoutines.nextKind(state, p.id()) == ResidentRoutine.Kind.STROLL
          && !ResidentRoutines.targets(state, p.id(), ResidentRoutine.Kind.STROLL).isEmpty()) {
        actor = p.id();
        break;
      }
    assertNotNull(actor);
    state = hotPresence(state, actor);
    var request =
        new RoutineEvent.Requested(
            actuation(state, actor),
            ResidentRoutine.Kind.STROLL,
            ResidentRoutines.targets(state, actor, ResidentRoutine.Kind.STROLL).getFirst());
    state =
        apply(
            state,
            assertInstanceOf(CommandPlan.Accepted.class, plan(state, request, NOW)).events(),
            NOW);
    var row = state.routines().rows().get(actor);
    var movement = state.actorMovements().get(actor);
    var inventory = state.inventory();
    state = AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.DRAINING);
    state =
        AmbientLeaseStateProcess.release(
            state,
            new AmbientLeaseReleased(
                actor,
                state.actorLocations().get(actor).body(),
                state.actorLocations().get(actor).condition().health()));
    state = ModeledActorBodyFacts.unloaded(state, actor);
    var codec = new FrontierWorldStateCodec();
    state = codec.decode(codec.encode(state));
    var due = ActorMovementProcess.progress(movement, NOW + 1);
    long now = NOW;
    for (int step = 0; state.actorMovements().containsKey(actor) && step < 100; step++) {
      now = due.dueAt().ticks();
      var events = ActorMovementProcess.plan(state, due, now);
      state = apply(state, events, now);
      for (var event : events)
        if (event.payload() instanceof ScheduleEffect.Rescheduled schedule
            && schedule.replacement().kind().equals(ActorMovementProcess.PROGRESS))
          due = schedule.replacement();
    }
    assertFalse(state.actorMovements().containsKey(actor));
    assertEquals(row.goal(), state.actorLocations().get(actor).supportingSurface());
    row = state.routines().rows().get(actor);
    assertEquals(ResidentRoutine.Phase.PAUSING, row.phase());
    assertTrue(row.phaseAt() > row.startedAt());
    state = codec.decode(codec.encode(state));
    var end = ResidentRoutineProcess.progress(row, row.phaseAt() + row.pauseTicks());
    state =
        apply(
            state,
            ResidentRoutineProcess.plan(state, end, end.dueAt().ticks()),
            end.dueAt().ticks());
    assertEquals(ResidentRoutine.Phase.CLOSED, state.routines().rows().get(actor).phase());
    assertEquals(inventory, state.inventory());
    assertTrue(state.actorExecutions().actors().get(actor).current().isEmpty());
  }

  @Test
  void walkingUsesOnlyTheCommonMovementOwnerAndDeathClosesItsExactReferences() {
    var state = initial();
    var home = state.bootstrap().settlements().getFirst();
    SubjectId actor = null;
    for (var person : home.residents())
      if (ResidentRoutines.nextKind(state, person.id()) == ResidentRoutine.Kind.STROLL
          && !ResidentRoutines.targets(state, person.id(), ResidentRoutine.Kind.STROLL).isEmpty()) {
        actor = person.id();
        break;
      }
    assertNotNull(actor, "the real authored public plan must offer a bounded stroll destination");
    state = hotPresence(state, actor);
    var target = ResidentRoutines.targets(state, actor, ResidentRoutine.Kind.STROLL).getFirst();
    var request =
        new RoutineEvent.Requested(actuation(state, actor), ResidentRoutine.Kind.STROLL, target);
    state =
        apply(
            state,
            assertInstanceOf(CommandPlan.Accepted.class, plan(state, request, NOW)).events(),
            NOW);
    var row = state.routines().rows().get(actor);
    var e = row.execution().orElseThrow();
    assertEquals(e, state.actorMovements().get(actor).executionId());
    assertFalse(
        ActorExecutionComposition.CAPABILITIES
            .require(ActorActivityKind.LEISURE)
            .checkpoint(state, e)
            .ready());
    var codec = new FrontierWorldStateCodec();
    state = codec.decode(codec.encode(state));
    assertEquals(row, state.routines().rows().get(actor));
    var dead =
        ModeledActorBodyFacts.died(
            state, actor, state.actorLocations().get(actor).body(), "unit-routine-death", NOW + 1);
    assertFalse(dead.routines().rows().containsKey(actor));
    assertFalse(dead.actorMovements().containsKey(actor));
    assertTrue(dead.actorExecutions().actors().get(actor).current().isEmpty());
  }
}
