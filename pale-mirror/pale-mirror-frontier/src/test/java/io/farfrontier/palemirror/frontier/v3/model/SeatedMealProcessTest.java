package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Complete canonical meal/resource stories over the actual maximum-level kitchen; not native pose evidence. */
class SeatedMealProcessTest {
    private static final long START = 48_000;
    private static FrontierWorldState prepared() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:seated-meal-unit"), 139,
                FrontierRulesets.installed("frontier-v3-clinical-care-r35")));
        var home = state.bootstrap().settlements().getFirst();
        var pantry = CommunalDiningPlan.initialFoodStorage(state.bootstrap(), home.id());
        return AutomaticStationProductionTest.supply(state, state.inventory().containers().get(pantry), "minecraft:bread", 64, "seated-meal");
    }
    @Test void reservedMealWakesItsIdleObstructionAndOrdinaryMovementVacatesAccessWithoutInventingFood() { verifySeatCourtesy(false); }
    @Test void reservedMealCanPassAnotherMealsStandingBodyWithoutRetiringEitherPortionOrClaim() { verifySeatCourtesy(true); }
    private static void verifySeatCourtesy(boolean incumbentEating) {
        var state = prepared(); var home = state.bootstrap().settlements().getFirst();
        var eater = home.residents().getFirst().id(); var blocker = home.residents().get(1).id();
        state = state.withHumanPopulation(state.humanPopulation().accrueHunger(eater, START));
        var started = ResidentMealProcess.selectSourceAtYield(state, eater, START).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, eater, started);
        var driver = new ModelEventDriver(); long tick = START;
        for (int step = 0; state.humanPopulation().meals().get(eater).phase() != ResidentMeal.Phase.MOVE_TO_DINING && step < 100; step++) {
            var meal = state.humanPopulation().meals().get(eater);
            tick = Math.max(tick + 1, meal.coldTravel().map(t -> t.arrivalTick()).orElse(tick + 1));
            state = driver.apply(state, FrontierWorldRuntimeDefinition.planScheduled(state, ResidentMealProcess.progress(meal, tick), new SimInstant(tick)), tick);
        }
        var meal = state.humanPopulation().meals().get(eater); assertEquals(ResidentMeal.Phase.MOVE_TO_DINING, meal.phase());
        var use = ResidentMealSeating.use(state, meal).orElseThrow();
        // Explicit fixture: either an idle fed person or another unstarted meal occupies this approach.
        if (incumbentEating) {
            state = state.withHumanPopulation(state.humanPopulation().accrueHunger(blocker, tick));
            var nextMeal = ResidentMealProcess.selectSourceAtYield(state, blocker, tick).orElseThrow();
            state = ResidentMealProcess.reduceStarted(state, blocker, nextMeal);
        } else state = state.withHumanPopulation(state.humanPopulation().consumeResidentFood(blocker, tick, 1000, state.bootstrap().ruleset().residentLife()));
        state = state.withActorBody(blocker, use.place().approach().standingBody());
        assertEquals(incumbentEating ? ResidentActivityChoice.Kind.EAT : ResidentActivityChoice.Kind.IDLE, ResidentActivityCoordinator.assess(state, blocker, tick).kind());
        var incumbentMeal = state.humanPopulation().meals().get(blocker);
        var executions = state.actorExecutions();
        var inventory = state.inventory(); var health = state.actorLocations().get(blocker).condition().health();
        assertTrue(ResidentMealProcess.planColdStep(state, eater, tick).isEmpty());
        var waits = FrontierWorldRuntimeDefinition.planScheduled(state, ResidentMealProcess.progress(meal, ++tick), new SimInstant(tick));
        assertTrue(waits.stream().anyMatch(e -> e.subject().equals(blocker) && e.payload() instanceof ScheduleEffect.Rescheduled));
        var review = incumbentEating ? ResidentMealProcess.progress(incumbentMeal, tick) : ResidentActivityProcess.review(blocker, tick);
        var clearing = FrontierWorldRuntimeDefinition.planScheduled(state, review, new SimInstant(tick));
        state = driver.apply(state, clearing, tick);
        var movement = state.actorMovements().get(blocker); assertNotNull(movement);
        if (incumbentEating) {
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.MealSeatClearance.class, movement.context());
            assertEquals(incumbentMeal, state.humanPopulation().meals().get(blocker));
            assertEquals(executions, state.actorExecutions());
            assertTrue(ActorExecutionComposition.CAPABILITIES.require(movement.executionId().activityKind()).permitsNavigationOrder(state, movement.executionId(), movement.order()));
        } else assertInstanceOf(io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.SeatClearance.class, movement.context());
        var admitted = state;
        assertTrue(movement.order().legalStations().stream().noneMatch(p -> SeatAccessClearance.furnitureContact(admitted, p)));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        var provider = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProviders.require(movement);
        var route = provider.route(state, movement, state.actorLocations().get(blocker).supportingSurface());
        var damaged = state.recordPhysicalDelta(new PhysicalDelta(route.getLast().support().offset(0, 1, 0), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:blocked-seat-clearance"));
        assertThrows(RuntimeException.class, () -> provider.requireRoute(damaged, movement, route));
        assertEquals(inventory, state.inventory()); assertEquals(health, state.actorLocations().get(blocker).condition().health());
        var forged = new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(movement.order(), movement.issuedAtTick(),
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.SeatClearance(new SubjectId("settlement:missing"), use.place().id()), movement.executionId());
        var running = state; assertThrows(IllegalArgumentException.class, () -> provider.validate(running, forged));
        for (int step = 0; state.actorMovements().containsKey(blocker) && step < 20; step++) {
            var current = state.actorMovements().get(blocker);
            tick = Math.max(tick + 1, current.coldTravel().map(t -> t.arrivalTick()).orElse(tick + 1));
            state = driver.apply(state, FrontierWorldRuntimeDefinition.planScheduled(state, ActorMovementProcess.progress(current, tick), new SimInstant(tick)), tick);
        }
        assertFalse(state.actorMovements().containsKey(blocker));
        assertNotEquals(use.place().approach(), state.actorLocations().get(blocker).supportingSurface());
        assertEquals(inventory, state.inventory(), "yielding grants no food transfer, wear or meal outcome");
        if (incumbentEating) {
            assertEquals(incumbentMeal, state.humanPopulation().meals().get(blocker));
            assertEquals(executions, state.actorExecutions());
        }
        assertEquals(health, state.actorLocations().get(blocker).condition().health());
        for (int step = 0; state.humanPopulation().meals().containsKey(eater) && step < 30; step++) {
            var current = state.humanPopulation().meals().get(eater);
            tick = Math.max(tick + 200, current.coldTravel().map(t -> t.arrivalTick()).orElse(tick + 200));
            state = driver.apply(state, FrontierWorldRuntimeDefinition.planScheduled(state, ResidentMealProcess.progress(current, tick), new SimInstant(tick)), tick);
        }
        assertFalse(state.humanPopulation().meals().containsKey(eater));
        assertEquals(inventory.fungibleResources().totalQuantity(home.id(), "minecraft:bread") - meal.portion().quantity(),
                state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:bread"));
        assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void allSixAuthoredPlacesHaveExclusiveReservationsAndFailedAdmissionsDoNotPoisonTheOccupancyIndex() {
        var state = prepared(); var home = state.bootstrap().settlements().getFirst();
        var area = ResidentDiningPlaces.in(state, home.id()).getFirst(); var building = ResidentDiningPlaces.facility(state, home.id(), area);
        var declarations = building.definition().seats().values().stream().filter(seat -> seat.zoneId().equals(area.zoneId())).sorted(Comparator.comparing(
                io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSeat::id)).toList();
        assertEquals(6, declarations.size());
        var board = SeatState.empty();
        for (int index = 0; index < declarations.size(); index++) {
            var actor = home.residents().get(index).id();
            var execution = state.actorExecutions().next(actor, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL,
                    new SubjectId("claim:seat-capacity-" + index));
            board = board.reserve(SeatUse.reserve(execution, SeatPlace.authored(home.id(), building, declarations.get(index)), START));
        }
        var full = board; var first = full.uses().get(home.residents().getFirst().id()); var outsider = home.residents().get(6).id();
        var outsiderExecution = state.actorExecutions().next(outsider, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL,
                new SubjectId("claim:seat-capacity-outsider"));
        assertThrows(IllegalArgumentException.class, () -> full.reserve(SeatUse.reserve(outsiderExecution, first.place(), START)));
        var malformed = new LinkedHashMap<>(full.uses()); malformed.put(outsider, first);
        assertThrows(IllegalArgumentException.class, () -> new SeatState(malformed));
        for (var use : full.uses().values()) assertFalse(full.free(use.place().id()));
        var released = full.without(first.execution().actorId()); assertTrue(released.free(first.place().id()));
        assertFalse(full.free(first.place().id()), "the old immutable image stays occupied after a different image releases its seat");
        assertEquals(6, full.uses().size());
    }
    @Test void grantedHotEntranceSurvivesACloserVisitorAndRecoveryAndUsesTheCommonColdJourneyAfterDeparture() {
        var state = prepared(); var home = state.bootstrap().settlements().getFirst();
        var actors = home.residents().stream().limit(2).map(Resident::id).toList();
        for (var actor : actors) {
            state = state.withHumanPopulation(state.humanPopulation().accrueHunger(actor, START));
            var started = ResidentMealProcess.selectSourceAtYield(state, actor, START).orElseThrow();
            state = ResidentMealProcess.reduceStarted(state, actor, started);
            var lease = AmbientActorProcess.nextLease(state, actor, new SimInstant(START));
            state = AmbientLeaseStateProcess.transition(ModeledActorBodyFacts.present(AmbientLeaseStateProcess.prepare(state, lease), actor), actor, AmbientLeaseStatus.HOT);
        }
        var source = state.humanPopulation().meals().get(actors.getFirst()).depotId(); var selected = state;
        var winner = actors.stream().filter(actor -> ResidentMealServiceAccess.available(selected, source, actor)).findFirst().orElseThrow();
        var loser = actors.stream().filter(actor -> !actor.equals(winner)).findFirst().orElseThrow();
        var meal = state.humanPopulation().meals().get(winner);
        var authority = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(ActorBodyAuthority.current(state, winner), meal.executionId());
        var request = new ResidentMealEntranceRequested(authority);
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(request, payloads.decode(request.type(), payloads.encode(request)));
        var movement = MealEntranceMovementProvider.requested(state, request, START + 1);
        state = ActorMovementProcess.reduceStarted(state, winner, new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted(movement));
        var reserved = state; assertThrows(IllegalArgumentException.class, () -> MealEntranceMovementProvider.requested(reserved, request, START + 2));
        var boundary = ServiceAccessCoordinator.boundary(state, source);
        var near = ServiceBoundaryComposition.geometry(state, ServiceBoundaryComposition.declaration(state, source).identity()).waitingSurfaces()
                .stream().filter(surface -> boundary.cleared(surface.standingBody())).min(Comparator.comparingLong(surface -> boundary.distanceFrom(surface.standingBody()))).orElseThrow();
        state = ModeledActorBodyFacts.inspected(state, loser, near.standingBody());
        assertTrue(boundary.distanceFrom(state.actorLocations().get(loser).body()) < boundary.distanceFrom(state.actorLocations().get(winner).body()));
        assertTrue(ResidentMealServiceAccess.available(state, source, winner), "a nearby newcomer cannot revoke a committed native entrance");
        assertFalse(ResidentMealServiceAccess.available(state, source, loser));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertEquals(movement, state.actorMovements().get(winner));
        assertTrue(ResidentMealServiceAccess.available(state, source, winner), "the retained turn is a journey fact, not an ephemeral cache");
        var dead = ModeledActorBodyFacts.died(state, winner, state.actorLocations().get(winner).body(), "unit-entrance-death", START + 2);
        assertFalse(dead.actorMovements().containsKey(winner));
        assertFalse(dead.humanPopulation().meals().containsKey(winner));
        assertFalse(dead.humanPopulation().seats().uses().containsKey(winner));
        assertFalse(dead.inventory().fungibleResources().claims().containsKey(meal.claimId()));
        state = AmbientLeaseStateProcess.transition(state, winner, AmbientLeaseStatus.DRAINING);
        state = AmbientLeaseStateProcess.release(state, new AmbientLeaseReleased(winner, state.actorLocations().get(winner).body(), state.actorLocations().get(winner).condition().health()));
        state = ModeledActorBodyFacts.unloaded(state, winner);
        assertTrue(ResidentMealProcess.held(state, ResidentMealProcess.progress(meal, START + 2)), "only the common movement owns this approach");
        var driver = new ModelEventDriver(); var due = ActorMovementProcess.progress(movement, START + 2); long now = START + 2;
        int bread = state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:bread");
        for (int step = 0; state.actorMovements().containsKey(winner) && step < 100; step++) {
            now = due.dueAt().ticks(); var events = ActorMovementProcess.plan(state, due, now);
            state = driver.apply(state, events, now);
            for (var event : events) if (event.payload() instanceof ScheduleEffect.Rescheduled schedule && schedule.replacement().kind().equals(ActorMovementProcess.PROGRESS)) due = schedule.replacement();
        }
        assertFalse(state.actorMovements().containsKey(winner));
        assertEquals(ResourceStorageAccess.at(state, source).interactionStation(state), state.actorLocations().get(winner).supportingSurface());
        assertEquals(bread, state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:bread"), "arrival grants no food outcome");
        assertEquals(meal.executionId(), state.actorExecutions().actors().get(winner).current().orElseThrow());
        assertEquals(SeatUse.Phase.RESERVED, state.humanPopulation().seats().uses().get(winner).phase());
        var returned = AmbientActorProcess.nextLease(state, winner, new SimInstant(now));
        state = AmbientLeaseStateProcess.transition(ModeledActorBodyFacts.present(AmbientLeaseStateProcess.prepare(state, returned), winner), winner, AmbientLeaseStatus.HOT);
        var atSource = state; long after = now + 1;
        var again = new ResidentMealEntranceRequested(new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(ActorBodyAuthority.current(state, winner), meal.executionId()));
        assertThrows(IllegalArgumentException.class, () -> MealEntranceMovementProvider.requested(atSource, again, after), "an observed source contact cannot restart a zero-distance journey forever");
        state = ResidentMealProcess.reduceHotArrived(state, winner, new ResidentMealHotArrived(winner, returned.revision(),
                state.actorLocations().get(winner).body(), ModeledActorBodyFacts.hotObservation(state, meal.executionId(), returned.revision())));
        assertEquals(ResidentMeal.Phase.TAKE, state.humanPopulation().meals().get(winner).phase());
    }
    @Test void seatReplacementRetainsThePortionAndClaimRejectsStaleOrPreparedChangesAndSurvivesRecovery() {
        var state = prepared(); var actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        state = state.withHumanPopulation(state.humanPopulation().accrueHunger(actor, START));
        var started = ResidentMealProcess.selectSourceAtYield(state, actor, START).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, actor, started);
        var driver = new ModelEventDriver(); var due = ResidentMealProcess.progress(started.meal(), START + 1); long now = START;
        for (int step = 0; state.humanPopulation().meals().get(actor).phase() != ResidentMeal.Phase.CONSUME && step < 100; step++) {
            now = due.dueAt().ticks(); var events = ResidentMealProcess.planProgress(state, due, now);
            state = driver.apply(state, events, now);
            for (var event : events) if (event.payload() instanceof ScheduleEffect.Rescheduled schedule) due = schedule.replacement();
        }
        var meal = state.humanPopulation().meals().get(actor); assertEquals(ResidentMeal.Phase.CONSUME, meal.phase());
        var lease = AmbientActorProcess.nextLease(state, actor, new SimInstant(now));
        state = AmbientLeaseStateProcess.transition(ModeledActorBodyFacts.present(AmbientLeaseStateProcess.prepare(state, lease), actor), actor, AmbientLeaseStatus.HOT);
        var use = state.humanPopulation().seats().uses().get(actor);
        var authority = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(ActorBodyAuthority.current(state, actor), meal.executionId());
        var alternative = ResidentMealSeating.alternatives(state, use).getFirst();
        var change = new ResidentMealSeatReselected(authority, use.revision(), alternative.id());
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs(); assertEquals(change, payloads.decode(change.type(), payloads.encode(change)));
        var preparedSeat = SeatContacts.apply(state, actor, new SeatEvent.Prepared(actor, use.revision(), authority, SeatUse.Action.ENTER, SeatUse.ExecutionMode.LOCAL), now);
        long at = now; assertThrows(IllegalArgumentException.class, () -> ResidentMealSeating.reselect(preparedSeat, change, at), "pending native effects retain their original seat");
        var before = state.inventory(); long oldGoal = ResidentMealSeating.goalRevision(state, meal);
        state = ResidentMealSeating.reselect(state, change, now + 1);
        assertEquals(before, state.inventory(), "changing furniture cannot retake, consume, release or recreate food");
        var replacement = state.humanPopulation().meals().get(actor);
        assertEquals(meal.claimId(), replacement.claimId()); assertEquals(meal.portion(), replacement.portion());
        assertEquals(meal.actorAccountId(), replacement.actorAccountId()); assertEquals(ResidentMeal.Phase.MOVE_TO_DINING, replacement.phase());
        assertTrue(ResidentMealSeating.goalRevision(state, replacement) > oldGoal);
        assertTrue(state.humanPopulation().seats().free(use.place().id())); assertFalse(state.humanPopulation().seats().free(alternative.id()));
        var changed = state; assertThrows(IllegalArgumentException.class, () -> ResidentMealSeating.reselect(changed, change, at + 2));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertEquals(replacement, state.humanPopulation().meals().get(actor));
        assertEquals(alternative, state.humanPopulation().seats().uses().get(actor).place());
        assertEquals(before, state.inventory());
        state = AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.DRAINING);
        state = AmbientLeaseStateProcess.release(state, new AmbientLeaseReleased(actor, state.actorLocations().get(actor).body(), state.actorLocations().get(actor).condition().health()));
        state = ModeledActorBodyFacts.unloaded(state, actor);
        due = ResidentMealProcess.progress(replacement, now + 2);
        for (int step = 0; state.humanPopulation().meals().containsKey(actor) && step < 100; step++) {
            now = due.dueAt().ticks(); var events = ResidentMealProcess.planProgress(state, due, now);
            state = driver.apply(state, events, now);
            for (var event : events) if (event.payload() instanceof ScheduleEffect.Rescheduled schedule) due = schedule.replacement();
        }
        assertFalse(state.humanPopulation().meals().containsKey(actor)); assertFalse(state.humanPopulation().seats().uses().containsKey(actor));
        assertEquals(before.fungibleResources().totalQuantity(meal.settlementId(), "minecraft:bread") - meal.portion().quantity(),
                state.inventory().fungibleResources().totalQuantity(meal.settlementId(), "minecraft:bread"));
    }
    @Test void hotSeatCheckpointSurvivesRecoveryAndDoesNotReinstallAHistoricalBodyWhenConfirmationIsDelayed() {
        var state = prepared(); var actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        state = state.withHumanPopulation(state.humanPopulation().accrueHunger(actor, START));
        var started = ResidentMealProcess.selectSourceAtYield(state, actor, START).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, actor, started);
        var driver = new ModelEventDriver(); var due = ResidentMealProcess.progress(started.meal(), START + 1); long now = START;
        for (int step = 0; state.humanPopulation().meals().get(actor).phase() != ResidentMeal.Phase.CONSUME && step < 100; step++) {
            now = due.dueAt().ticks(); var events = ResidentMealProcess.planProgress(state, due, now);
            state = driver.apply(state, events, now);
            for (var event : events) if (event.payload() instanceof ScheduleEffect.Rescheduled schedule) due = schedule.replacement();
        }
        assertEquals(ResidentMeal.Phase.CONSUME, state.humanPopulation().meals().get(actor).phase());
        var lease = AmbientActorProcess.nextLease(state, actor, new SimInstant(now));
        state = AmbientLeaseStateProcess.transition(ModeledActorBodyFacts.present(AmbientLeaseStateProcess.prepare(state, lease), actor), actor, AmbientLeaseStatus.HOT);
        var use = state.humanPopulation().seats().uses().get(actor);
        var body = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(ActorBodyAuthority.current(state, actor), use.execution());
        state = SeatContacts.apply(state, actor, new SeatEvent.Prepared(actor, use.revision(), body, SeatUse.Action.ENTER, SeatUse.ExecutionMode.LOCAL), now);
        var prepared = state; long at = now;
        var arrived = new SeatEvent.Changed(actor, use.revision(), Optional.of(body), SeatUse.Action.ENTER,
                SeatUse.ExecutionMode.LOCAL, true, use.place().contact().standingBody());
        assertThrows(IllegalArgumentException.class, () -> SeatContacts.apply(prepared, actor, arrived, at), "an attempt is not a pose observation");
        var checkpoint = new SeatEvent.Checkpointed(actor, use.revision(), body, SeatUse.Action.ENTER,
                SeatUse.ExecutionMode.LOCAL, use.place().contact().standingBody());
        assertThrows(IllegalArgumentException.class, () -> SeatContacts.apply(prepared, actor, checkpoint, at), "checkpoint must match independently inspected common body");
        // Application and its native marker can precede the common checkpoint. A later
        // supported physical position must survive confirmation of that historical fact.
        var movedBeforeCheckpoint = ModeledActorBodyFacts.inspected(prepared, actor, checkpoint.observed());
        movedBeforeCheckpoint = ModeledActorBodyFacts.inspected(movedBeforeCheckpoint, actor, use.place().egress().standingBody());
        var asOf = new SeatEvent.ApplicationCheckpointed(actor, use.revision(), body, SeatUse.Action.ENTER,
                SeatUse.ExecutionMode.LOCAL, java.util.UUID.fromString("a9a40587-1ed7-44b1-9ccd-38120231b4b7"), checkpoint.observed());
        var afterMovement = movedBeforeCheckpoint;
        assertThrows(IllegalArgumentException.class, () -> SeatContacts.apply(afterMovement, actor, checkpoint, at));
        var historical = SeatContacts.apply(afterMovement, actor, asOf, now);
        historical = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(historical));
        historical = SeatContacts.apply(historical, actor, arrived, now + 1);
        assertEquals(afterMovement.actorLocations().get(actor).body(), historical.actorLocations().get(actor).body());
        assertEquals(SeatUse.Phase.SEATED, historical.humanPopulation().seats().uses().get(actor).phase());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(asOf, codecs.decode(asOf.type(), codecs.encode(asOf)));
        var foreign = new SeatEvent.ApplicationCheckpointed(actor, use.revision() + 1, body, SeatUse.Action.ENTER,
                SeatUse.ExecutionMode.LOCAL, asOf.operation(), checkpoint.observed());
        assertThrows(IllegalArgumentException.class, () -> SeatContacts.apply(afterMovement, actor, foreign, at));
        var wrongPose = new SeatEvent.ApplicationCheckpointed(actor, use.revision(), body, SeatUse.Action.ENTER,
                SeatUse.ExecutionMode.LOCAL, asOf.operation(), afterMovement.actorLocations().get(actor).body());
        assertThrows(IllegalArgumentException.class, () -> SeatContacts.apply(afterMovement, actor, wrongPose, at));
        state = ModeledActorBodyFacts.inspected(state, actor, checkpoint.observed());
        state = SeatContacts.apply(state, actor, checkpoint, now);
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertEquals(Optional.of(checkpoint.observed()), state.humanPopulation().seats().uses().get(actor).pendingObservation());
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(checkpoint, payloads.decode(checkpoint.type(), payloads.encode(checkpoint)));
        state = ModeledActorBodyFacts.inspected(state, actor, use.place().egress().standingBody());
        var displaced = state.actorLocations().get(actor).body();
        state = SeatContacts.apply(state, actor, arrived, now + 1);
        assertEquals(displaced, state.actorLocations().get(actor).body(), "receipt cannot put the actor back onto its old seat");
        assertEquals(SeatUse.Phase.SEATED, state.humanPopulation().seats().uses().get(actor).phase());
        var confirmed = state;
        assertThrows(IllegalArgumentException.class, () -> SeatContacts.apply(confirmed, actor, arrived, at + 2), "receipt closes once");
        assertEquals(Optional.of(SeatUse.Action.EXIT), ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind())
                .seatAction(state, use.execution(), state.humanPopulation().seats().uses().get(actor), now + 2));
    }
    @Test void queuedResidentsReserveDistinctPlacesSitForTheIntervalConsumeOnceAndFreeTheirSeatsAcrossRecovery() {
        var state = prepared(); var home = state.bootstrap().settlements().getFirst();
        var residents = home.residents().stream().limit(4).map(Resident::id).toList();
        var people = state.humanPopulation();
        for (var actor : residents) people = people.accrueHunger(actor, START);
        state = state.withHumanPopulation(people);
        int beforeBread = state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:bread");
        var due = new HashMap<SubjectId, ScheduledAction>();
        var claims = new HashSet<SubjectId>(); int consumedQuantity = 0;
        for (var actor : residents.subList(0, 3)) {
            var basis = state; var selected = ResidentMealProcess.selectSourceAtYield(state, actor, START);
            assertTrue(selected.isPresent(), () -> "missing meal for " + actor + ": " + ResidentActivityCoordinator.assess(basis, actor, START)
                    + "; " + ResidentMealOpportunity.candidateAdmission(basis, actor, START));
            var started = selected.orElseThrow();
            assertInstanceOf(MealDestination.Seat.class, started.meal().destination());
            state = ResidentMealProcess.reduceStarted(state, actor, started);
            due.put(actor, ResidentMealProcess.progress(started.meal(), START + 1));
            claims.add(started.meal().claimId()); consumedQuantity += started.meal().portion().quantity();
        }
        assertEquals(3, state.humanPopulation().seats().uses().size());
        assertEquals(3, state.humanPopulation().seats().uses().values().stream().map(use -> use.place().id()).distinct().count());
        var driver = new ModelEventDriver(); var codec = new FrontierWorldStateCodec(); boolean recoveredWhileSeated = false;
        var observedSeated = new HashSet<SubjectId>();
        for (int step = 0; !due.isEmpty() && step < 400; step++) {
            var action = due.values().stream().min(Comparator.comparing(ScheduledAction::dueAt).thenComparing(ScheduledAction::subject)).orElseThrow();
            long now = action.dueAt().ticks();
            var meal = state.humanPopulation().meals().get(action.subject()); assertNotNull(meal);
            var events = ResidentMealProcess.planProgress(state, action, now);
            state = driver.apply(state, events, now);
            for (var event : events) if (event.payload() instanceof ScheduleEffect.Rescheduled schedule) due.put(action.subject(), schedule.replacement());
            if (!state.humanPopulation().meals().containsKey(action.subject())) due.remove(action.subject());
            var use = state.humanPopulation().seats().uses().get(action.subject());
            if (use != null && use.phase() == SeatUse.Phase.SEATED) {
                observedSeated.add(action.subject());
                assertEquals(use.place().contact().standingBody(), state.actorLocations().get(action.subject()).body());
                assertFalse(ActorExecutionComposition.CAPABILITIES.require(meal.executionId().activityKind()).checkpoint(state, meal.executionId()).ready(),
                        "a seated body must stand before another activity can own its movement");
                var early = state; var currentMeal = state.humanPopulation().meals().get(action.subject());
                if (now < use.changedAt() + ResidentMealSeating.EATING_TICKS) assertThrows(IllegalArgumentException.class,
                        () -> ResidentMealProcess.reduceColdStep(early, action.subject(),
                                new ResidentMealColdStep(action.subject(), ResidentMeal.Phase.CONSUME, now, currentMeal.executionId())));
                if (!recoveredWhileSeated) {
                    var restored = codec.decode(codec.encode(state));
                    assertEquals(state.humanPopulation().seats(), restored.humanPopulation().seats());
                    assertEquals(state.humanPopulation().meals(), restored.humanPopulation().meals());
                    assertEquals(state.inventory().fungibleResources(), restored.inventory().fungibleResources());
                    state = restored; recoveredWhileSeated = true;
                }
            }
        }
        assertTrue(due.isEmpty(), "every exact meal must reach terminal consumption");
        assertEquals(new HashSet<>(residents.subList(0, 3)), observedSeated);
        assertTrue(recoveredWhileSeated);
        assertTrue(state.humanPopulation().seats().uses().isEmpty());
        assertEquals(beforeBread - consumedQuantity, state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:bread"));
        assertTrue(Collections.disjoint(claims, state.inventory().fungibleResources().claims().keySet()));
        for (var actor : residents.subList(0, 3)) assertTrue(state.actorExecutions().actors().get(actor).current().isEmpty());
        assertTrue(ResidentMealProcess.selectSourceAtYield(state, residents.get(3), START + 10_000).isPresent(),
                "a released table place admits the next hungry resident using the remaining real food");
    }
}
