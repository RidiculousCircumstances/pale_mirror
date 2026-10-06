package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResidentMealProcessTest {
    private static long nextColdTick(FrontierWorldState state, SubjectId resident, long earliest) {
        ResidentMeal meal = state.humanPopulation().meals().get(resident);
        return meal.coldTravel().map(route -> Math.max(earliest, route.arrivalTick())).orElse(earliest);
    }

    @Test void timedColdMealTravelHandoffsTheAsOfBodyAndResumesAfterHotRelease() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-timed-handoff"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        var stock = initial.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:timed-handoff-bread"), settlement.id(),
                        ResidentMeal.BREAD_KIND, 64, "test", List.of()));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(initial.inventory().withFungibleResources(stock))
                .humanPopulation(initial.humanPopulation().accrueHunger(resident, 27_000L)));
        ResidentMealStarted started = ResidentMealProcess.selectSourceAtYield(state, resident, 27_000L).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, resident, started);
        assertTrue(ActorSpatialCourtesy.assess(state, started.meal().executionId()).ready(),
                "approaching food may yield without abandoning the meal claim");
        assertFalse(ActorExecutionComposition.CAPABILITIES.require(started.meal().executionId().activityKind())
                .checkpoint(state, started.meal().executionId()).ready(),
                "spatial courtesy is not terminal meal interruption");
        BodyPosition departure = state.actorLocations().get(resident).body();
        var events = ResidentMealProcess.planProgress(state, ResidentMealProcess.progress(started.meal(), 27_001L));
        assertEquals(2, events.size(), "travel starts and replaces one due action, not one action per support");
        state = ResidentMealProcess.reduceColdStep(state, resident, (ResidentMealColdStep) events.getFirst().payload());
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var travel = state.humanPopulation().meals().get(resident).coldTravel().orElseThrow();
        assertEquals(travel.arrivalTick(), ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled)
                events.getLast().payload()).replacement().dueAt().ticks());
        var premature = ResidentMealProcess.progress(state.humanPopulation().meals().get(resident),
                travel.departedAtTick() + 1L);
        assertFalse(ResidentMealProcess.held(state, premature));
        var corrected = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                ResidentMealProcess.planProgress(state, premature).getFirst().payload());
        assertEquals(travel.arrivalTick(), corrected.replacement().dueAt().ticks());
        long middle = travel.departedAtTick() + travel.ticksPerEdge();
        BodyPosition middleBody = ResidentMealProcess.bodyAt(state, resident, middle);
        assertFalse(departure.equals(middleBody));
        assertTrue(travel.route().size() > 2);
        var changedCells = new LinkedHashMap<>(state.physicalDeltas());
        PhysicalDelta obstruction = new PhysicalDelta(
                travel.route().get(2).support(), PhysicalDeltaKind.UNKNOWN_SCAR,
                java.util.Optional.empty(), java.util.Optional.empty(), "test:known-route-obstruction");
        changedCells.put(obstruction.position(), obstruction);
        var obstructionPlan = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                FrontierWorldPhysicalObservationProcess.plan(state, new PhysicalDeltaObserved(obstruction), middle));
        var routeWake = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                obstructionPlan.events().stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                        .filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class::isInstance)
                        .findFirst().orElseThrow());
        assertEquals(middle + 1L, routeWake.replacement().dueAt().ticks());
        ResidentMealColdStep immediateInterruption = assertInstanceOf(ResidentMealColdStep.class,
                obstructionPlan.events().get(1).payload());
        assertEquals(middle, immediateInterruption.atTick());
        assertTrue(immediateInterruption.nextSurface().isEmpty());
        FrontierWorldState obstructed = state.withChanges(FrontierWorldStateUpdate.begin()
                .physicalDeltas(changedCells));
        obstructed = ResidentMealProcess.reduceColdStep(obstructed, resident, immediateInterruption);
        assertEquals(middleBody, obstructed.actorLocations().get(resident).body());
        assertTrue(obstructed.humanPopulation().meals().get(resident).coldTravel().isEmpty());
        var runtime = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 421L);
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                state.bootstrap().worldId(), state, new SimInstant(middle), runtime.commandPlanner(),
                runtime.scheduledPlanner(), runtime.reducer(), runtime.stateCodec(), runtime.projectionMapper(),
                runtime.limits(), List.of(ResidentMealProcess.progress(state.humanPopulation().meals().get(resident),
                        travel.arrivalTick())), runtime.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:timed-route-obstruction");
        var accepted = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1,
                commandId, state.bootstrap().worldId(), engine.checkpoint().revision(), new SimInstant(middle),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId),
                new PhysicalDeltaObserved(obstruction)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, accepted,
                accepted::toString);
        FrontierWorldState afterObservation = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(middleBody, afterObservation.actorLocations().get(resident).body());
        assertTrue(afterObservation.humanPopulation().meals().get(resident).coldTravel().isEmpty());
        PhysicalDelta changedBehind = new PhysicalDelta(travel.route().get(1).support(),
                PhysicalDeltaKind.UNKNOWN_SCAR, java.util.Optional.empty(), java.util.Optional.empty(),
                "test:passed-route-cell");
        FrontierWorldState changedBehindState = state.recordPhysicalDelta(changedBehind);
        var behindPlan = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                FrontierWorldPhysicalObservationProcess.plan(state, new PhysicalDeltaObserved(changedBehind), middle + 1L));
        ResidentMealColdStep behindInterruption = assertInstanceOf(ResidentMealColdStep.class,
                behindPlan.events().get(1).payload());
        changedBehindState = ResidentMealProcess.reduceColdStep(changedBehindState, resident, behindInterruption);
        assertEquals(middleBody, changedBehindState.actorLocations().get(resident).body(),
                "a changed cell already passed must not rewind the as-of resident body");
        assertEquals(departure, state.actorLocations().get(resident).body(),
                "the intermediate support is derived, not independently WAL-written");
        assertFalse(ResidentMealProcess.bodyAt(state, resident, travel.arrivalTick() + 100L)
                .equals(travel.route().getLast().standingBody()),
                "an uncommitted arrival cannot be materialized as a completed interaction");
        AmbientActorLease lease = AmbientActorProcess.nextLease(state, resident, new SimInstant(middle));
        assertEquals(middleBody, lease.handoffBody());
        state = AmbientLeaseStateProcess.prepare(state, lease);
        assertEquals(middleBody, state.actorLocations().get(resident).body());
        assertTrue(state.humanPopulation().meals().get(resident).coldTravel().isEmpty());
        state = ModeledActorBodyFacts.present(state, resident);
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.HOT);
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.DRAINING);
        AmbientLeaseReleased release = new AmbientLeaseReleased(resident, middleBody,
                state.actorLocations().get(resident).condition().health());
        var resumed = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                AmbientActorProcess.plan(state, release, middle + 1L));
        assertEquals(2, resumed.events().size());
        var wake = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                resumed.events().getLast().payload());
        assertEquals(middle + 2L, wake.replacement().dueAt().ticks());
        state = AmbientLeaseStateProcess.release(state, release);
        state = ModeledActorBodyFacts.unloaded(state, resident);
        assertTrue(ResidentMealProcess.planColdStep(state, resident, middle + 2L).isPresent(),
                "COLD must restart from the witnessed HOT release, not wait for the old route deadline");
    }

    @Test void severalHungryResidentsCompleteDistinctColdMealsUnderOrdinaryDueBudget() {
        WorldId world = new WorldId("frontier:resident-meal-cohort-budget");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 421L);
        FrontierWorldState initial = base.initialState();
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        List<SubjectId> residents = settlement.residents().stream().limit(4).map(Resident::id).toList();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        var stock = initial.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:cohort-bread"), settlement.id(),
                        ResidentMeal.BREAD_KIND, 64, "test", List.of()));
        HumanPopulation people = initial.humanPopulation();
        for (SubjectId resident : residents) people = people.accrueHunger(resident, 27_000L);
        FrontierWorldState stocked = initial.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(initial.inventory().withFungibleResources(stock)).humanPopulation(people));
        var scheduled = residents.stream().flatMap(resident -> java.util.stream.Stream.of(
                ResidentActivityProcess.review(resident, 27_001L),
                ResidentNeedProcess.review(resident, stocked.humanPopulation().nutrition(resident).nextThresholdTick(
                        stocked.bootstrap().ruleset().residentLife(), stocked.humanPopulation().resident(resident)
                                .characteristics().effectiveMetabolismPermille(27_000L))))).toList();
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                world, stocked, new SimInstant(27_000L), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), scheduled,
                base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        for (long tick = 27_001L; tick <= 30_000L; tick++) {
            var result = engine.advanceTo(new SimInstant(tick),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(4, 512));
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE,
                    result.status().kind(), () -> result.status().failureDetail().orElse("quarantined"));
        }
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        long fed = residents.stream().filter(resident -> after.humanPopulation().nutrition(resident)
                .status() == ResidentNutritionStatus.NOURISHED).count();
        assertTrue(fed >= 2, "a single resident must not monopolize every depot visit: "
                + after.humanPopulation().meals());
        assertTrue(after.inventory().fungibleResources().totalQuantity(settlement.id(), ResidentMeal.BREAD_KIND) <= 62,
                "at least two distinct bread portions must be consumed");
    }

    @Test void twoHungryResidentsCanTravelTogetherButOnlyOneMayEnterDepotService() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-depot-service-queue"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId first = settlement.residents().get(0).id();
        SubjectId second = settlement.residents().get(1).id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        var stock = initial.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:resident-queue-bread"), settlement.id(),
                        ResidentMeal.BREAD_KIND, 64, "test", List.of()));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(initial.inventory().withFungibleResources(stock))
                .humanPopulation(initial.humanPopulation().accrueHunger(first, 27_000L)
                        .accrueHunger(second, 27_000L)));
        ResidentMealStarted started = ResidentMealProcess.selectSourceAtYield(state, first, 27_000L).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, first, started);
        ResidentMealStarted next = ResidentMealProcess.selectSourceAtYield(state, second, 27_000L).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, second, next);
        assertEquals(2, state.inventory().fungibleResources().claims().size());
        assertTrue(ResidentMealServiceAccess.available(state, depot, second),
                "a travelling resident reserves bread but not the physical service turn");
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        ServiceAccessBoundary boundary = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).accessBoundary();
        // Both residents can hold a timed journey before either consumes the
        // shared depot turn. No per-support event is required for the approach.
        state = ResidentMealProcess.reduceColdStep(state, first,
                ResidentMealProcess.planColdStep(state, first, 27_001L).orElseThrow());
        state = ResidentMealProcess.reduceColdStep(state, second,
                ResidentMealProcess.planColdStep(state, second, 27_002L).orElseThrow());
        assertTrue(state.humanPopulation().meals().get(first).coldTravel().isPresent());
        assertTrue(state.humanPopulation().meals().get(second).coldTravel().isPresent());
        long firstTick = 27_003L;
        boolean releasedDuringReturn = false;
        for (int turn = 0; turn < 16 && boundary.cleared(state.actorLocations().get(first).body()); turn++) {
            firstTick = nextColdTick(state, first, firstTick);
            ResidentMealColdStep step = ResidentMealProcess.planColdStep(state, first, firstTick).orElseThrow();
            state = ResidentMealProcess.reduceColdStep(state, first, step);
            firstTick++;
            if (state.humanPopulation().meals().containsKey(first)
                    && state.humanPopulation().meals().get(first).carriesFood()
                    && ResidentMealServiceAccess.available(state, depot, second))
                releasedDuringReturn = true;
        }
        assertFalse(boundary.cleared(state.actorLocations().get(first).body()));
        assertFalse(ResidentMealServiceAccess.available(state, depot, second));
        long secondTick = nextColdTick(state, second, 27_003L);
        ResidentMealColdStep approach = ResidentMealProcess.planColdStep(state, second, secondTick).orElseThrow();
        assertTrue(approach.nextSurface().isPresent());
        assertTrue(boundary.cleared(approach.nextSurface().orElseThrow().standingBody()),
                "a waiting resident must not cross the physical depot boundary");
        state = ResidentMealProcess.reduceColdStep(state, second, approach);
        assertEquals(ResidentMeal.Phase.MOVE, state.humanPopulation().meals().get(second).phase());
        assertTrue(ResidentMealProcess.held(state,
                ResidentMealProcess.progress(state.humanPopulation().meals().get(second), secondTick + 1L)),
                "a side-pocket waiter must retain its due action without a periodic no-op WAL transaction");
        for (int turn = 0; state.humanPopulation().meals().containsKey(first) && turn < 32; turn++) {
            FrontierWorldState current = state;
            firstTick = nextColdTick(state, first, firstTick);
            ResidentMealColdStep step = ResidentMealProcess.planColdStep(state, first, firstTick)
                    .orElseThrow(() -> new AssertionError("first body=" + current.actorLocations().get(first).body()
                            + " second body=" + current.actorLocations().get(second).body()
                            + " first phase=" + current.humanPopulation().meals().get(first).phase()
                            + " first clear=" + current.humanPopulation().meals().get(first).clearingSurface()
                            + " second clear=" + current.humanPopulation().meals().get(second).clearingSurface()
                            + " port=" + SettlementDepotServicePort.forDepot(settlement.structures().stream()
                                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow())));
            state = ResidentMealProcess.reduceColdStep(state, first, step);
            firstTick++;
            if (state.humanPopulation().meals().containsKey(first)
                    && state.humanPopulation().meals().get(first).carriesFood()
                    && ResidentMealServiceAccess.available(state, depot, second))
                releasedDuringReturn = true;
        }
        assertFalse(state.humanPopulation().meals().containsKey(first),
                "confirmed consumption retires the meal without pretending that return travel is eating");
        assertTrue(releasedDuringReturn, "access must release before consumption, not after returning home");
        assertFalse(state.actorMovements().containsKey(first));
        assertTrue(state.humanPopulation().meals().containsKey(second),
                "the second resident's independent meal must survive the first return");
        assertTrue(ResidentMealServiceAccess.available(state, depot, second));
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), ResidentMeal.BREAD_KIND));
        for (int turn = 0; state.humanPopulation().meals().containsKey(second) && turn < 32; turn++) {
            secondTick = nextColdTick(state, second, Math.max(secondTick + 1L, firstTick));
            ResidentMealColdStep step = ResidentMealProcess.planColdStep(state, second, secondTick).orElseThrow();
            state = ResidentMealProcess.reduceColdStep(state, second, step);
        }
        assertFalse(state.humanPopulation().meals().containsKey(second));
        assertEquals(62, state.inventory().fungibleResources().totalQuantity(settlement.id(), ResidentMeal.BREAD_KIND));
    }

    @Test void postTakeNativeFixtureRetainsOneColdHandAndOnlyOneFutureAction() {
        var configuration = FrontierV3FixtureCatalog.configuration("resident-meal-after-cold-take",
                new WorldId("frontier:resident-after-take-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        SubjectId resident = new SubjectId("resident:6-1");
        ResidentMeal meal = state.humanPopulation().meals().get(resident);
        assertEquals(ResidentMeal.Phase.CLEAR_ACCESS, meal.phase());
        assertTrue(FrontierWorldStateSupport.workCapable(state, state.humanPopulation().resident(resident)),
                "self-care must not invalidate a previously retained work owner");
        assertFalse(FrontierWorldStateSupport.availableForNewAssignment(state, state.humanPopulation().resident(resident)),
                "a new work owner cannot recruit the resident while the meal owns their activity");
        assertEquals(1, state.inventory().fungibleResources().accounts().get(meal.actorAccountId())
                .lotQuantities().get(meal.portion().lotQuantities().keySet().iterator().next()));
        assertEquals(1, state.inventory().fungibleResources().claims().get(meal.claimId()).quantity());
        assertEquals(0, state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(meal.actorAccountId())).count());
        long instant = configuration.initialInstant().ticks();
        assertEquals(List.of(ResidentMealProcess.progress(meal, instant + 1_000L),
                ResidentActivityProcess.review(resident, instant + 1_000L),
                ResidentNeedProcess.review(resident, state.humanPopulation().nutrition(resident).nextThresholdTick(
                        state.bootstrap().ruleset().residentLife(),
                        state.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(instant)))),
                configuration.initialSchedules());
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(
                new FrontierWorldStateCodec().encode(state));
        assertEquals(meal, recovered.humanPopulation().meals().get(resident));
    }

    @Test void longStarvationRequiresOneCurrentPortionNotRepaymentOfMissedMeals() {
        WorldId world = new WorldId("frontier:resident-meal-two-portions");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 421L);
        FrontierWorldState initial = base.initialState();
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface();
        var actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(service, ActorKind.RESIDENT));
        var stock = initial.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:resident-two-portions-bread"), settlement.id(),
                        "minecraft:bread", 64, "test", List.of()));
        FrontierWorldState stocked = initial.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).inventory(initial.inventory().withFungibleResources(stock))
                .humanPopulation(initial.humanPopulation().accrueHunger(resident, 96_000L)));
        assertEquals(0, stocked.humanPopulation().nutrition(resident).satietyUnits());
        assertEquals(ResidentActivityChoice.Kind.EAT,
                ResidentActivityCoordinator.assess(stocked, resident, 96_001L).kind());
        assertTrue(ResidentMealProcess.selectSourceAtYield(stocked, resident, 96_001L).isPresent());
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                world, stocked, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(96_000L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(
                        ResidentActivityProcess.review(resident, 96_001L),
                        ResidentNeedProcess.review(resident, stocked.humanPopulation().nutrition(resident)
                                .nextThresholdTick(stocked.bootstrap().ruleset().residentLife(),
                                        stocked.humanPopulation().resident(resident).characteristics()
                                                .effectiveMetabolismPermille(96_000L)))),
                base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        for (long due = 96_001L; due <= 100_001L; due += 20L)
            engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(due),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1_024, 4_096));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(63, after.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"),
                "need=" + after.humanPopulation().nutrition(resident) + " meal=" + after.humanPopulation().meals()
                        + " schedules=" + engine.checkpoint().schedules());
        assertEquals(ResidentNutritionStatus.NOURISHED, after.humanPopulation().nutrition(resident).status());
        assertTrue(after.humanPopulation().meals().isEmpty(),
                "meal=" + after.humanPopulation().meals() + " body=" + after.actorLocations().get(resident)
                        + " status=" + engine.status() + " instant=" + engine.checkpoint().instant()
                        + " schedules=" + engine.checkpoint().schedules() + " route="
                        + clearingRoute(after, resident));
    }

    @Test void preConsumptionClearanceRetainsItsPortionAcrossColdHotColdAndRestart() {
        var fixture = FrontierV3FixtureCatalog.configuration("resident-meal-after-cold-take",
                new WorldId("frontier:meal-clearance-handoff"), 41L);
        SubjectId resident = new SubjectId("resident:6-1");
        FrontierWorldState state = fixture.initialState();
        ResidentMeal meal = state.humanPopulation().meals().get(resident);
        long now = fixture.initialInstant().ticks() + 1L;
        state = ResidentMealProcess.reduceColdStep(state, resident,
                ResidentMealProcess.planColdStep(state, resident, now).orElseThrow());
        var route = state.humanPopulation().meals().get(resident).coldTravel().orElseThrow();
        now += route.ticksPerEdge();
        BodyPosition handoff = ResidentMealProcess.bodyAt(state, resident, now);
        var lease = AmbientActorProcess.nextLease(state, resident, new SimInstant(now));
        assertEquals(handoff, lease.handoffBody());
        state = AmbientLeaseStateProcess.prepare(state, lease);
        state = ModeledActorBodyFacts.present(state, resident);
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.HOT);
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(resident,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident), 0),
                meal.portion().itemKind(), meal.portion().quantity());
        state = ResidentMealProcess.reduceHotHandMaterialized(state, resident,
                new ResidentMealHotHandMaterialized(resident, lease.revision(), hand, meal.executionId()));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(ResidentMeal.Phase.CLEAR_ACCESS, state.humanPopulation().meals().get(resident).phase());
        assertTrue(ResidentMealProcess.planColdStep(state, resident, now + 1L).isEmpty());
        state = ResidentMealProcess.reduceHotHandReleased(state, resident,
                new ResidentMealHotHandReleased(resident, lease.revision(), hand, meal.executionId()));
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.DRAINING);
        state = AmbientLeaseStateProcess.release(state,
                new AmbientLeaseReleased(resident, handoff, state.actorLocations().get(resident).condition().health()));
        state = ModeledActorBodyFacts.unloaded(state, resident);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        for (int step = 0; state.humanPopulation().meals().containsKey(resident) && step < 16; step++) {
            now = nextColdTick(state, resident, now + 1L);
            state = ResidentMealProcess.reduceColdStep(state, resident,
                    ResidentMealProcess.planColdStep(state, resident, now).orElseThrow());
        }
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertFalse(state.actorMovements().containsKey(resident));
        assertTrue(ServiceAccessCoordinator.boundary(state, meal.depotId()).cleared(state.actorLocations().get(resident).body()),
                "eating retires outside access, not at an exact parking destination");
        assertFalse(state.inventory().fungibleResources().claims().containsKey(meal.claimId()));
        assertFalse(state.inventory().fungibleResources().accounts().containsKey(meal.actorAccountId()));
        assertEquals(ResidentNutritionStatus.NOURISHED, state.humanPopulation().nutrition(resident).status());
    }

    @Test void sparseEngineWakesOneColdResidentThroughAnEntireMealWithoutProvision() {
        WorldId world = new WorldId("frontier:resident-meal-engine");
        var base = FrontierWorldRuntimeDefinition.configuration(world, 421L);
        FrontierWorldState initial = base.initialState();
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface();
        var actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(service, ActorKind.RESIDENT));
        var stock = initial.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:resident-engine-bread"), settlement.id(),
                        "minecraft:bread", 64, "test", List.of()));
        FrontierWorldState stocked = initial.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).inventory(initial.inventory().withFungibleResources(stock)));
        long firstNeed = initial.humanPopulation().nutrition(resident).nextThresholdTick(
                initial.bootstrap().ruleset().residentLife(),
                initial.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(0L));
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                world, stocked, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(
                ResidentNeedProcess.review(resident, firstNeed),
                ResidentActivityProcess.review(resident, 1L)), base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        // The scheduler admits one generation of a rescheduled action per call;
        // drive its 20-tick cadence instead of leaping over hundreds of pending turns.
        for (long due = 1L; due <= firstNeed + 5_000L; due += 20L)
            engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(due),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1_024, 4_096));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(63, after.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"),
                "need=" + after.humanPopulation().nutrition(resident) + " meal=" + after.humanPopulation().meals()
                        + " schedules=" + engine.checkpoint().schedules());
        assertEquals(ResidentNutritionStatus.NOURISHED, after.humanPopulation().nutrition(resident).status());
        assertTrue(after.humanPopulation().meals().isEmpty(),
                "meal=" + after.humanPopulation().meals() + " body=" + after.actorLocations().get(resident)
                        + " route=" + clearingRoute(after, resident) + " status=" + engine.status()
                        + " schedules=" + engine.checkpoint().schedules());
        assertTrue(after.inventory().fungibleResources().claims().isEmpty());
    }

    private static String clearingRoute(FrontierWorldState state, SubjectId resident) {
        ResidentMeal meal = state.humanPopulation().meals().get(resident);
        if (meal == null) return "finished";
        try { return ResidentMealKnownNavigation.returnPath(state, meal).toString(); }
        catch (RuntimeException blocked) { return blocked.toString(); }
    }

    @Test void hotResidentTakesAndConsumesBoundBreadOnceAfterSnapshotRecovery() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-hot"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface();
        var actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(service, ActorKind.RESIDENT));
        SubjectId account = ReferenceContainerCustody.scopeId(depot);
        SubjectId bread = new SubjectId("lot:resident-meal-hot-bread");
        var cold = initial.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(bread, settlement.id(), "minecraft:bread", 64, "test", List.of()));
        var source = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:bread", 64);
        var bound = cold.rebind(account, 1L, FungiblePhysicalObservation.bind(cold, account, 1L, List.of(source)));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).inventory(initial.inventory().withFungibleResources(bound))
                .humanPopulation(initial.humanPopulation().accrueHunger(resident, 48_000L)));
        var population = state.humanPopulation().withStarvation(resident, new ResidentStarvation(100, 0, 0));
        var needs = new LinkedHashMap<>(population.nutrition());
        needs.put(resident, new ResidentNutrition(ResidentNutritionStatus.STARVING, 0, 48_000, 0));
        state = state.withHumanPopulation(new HumanPopulation(population.households(), population.residents(), population.birthJobs(),
                population.health(), population.quarantines(), population.migrations(), population.provisions(), needs,
                population.medicalOperations(), population.schedules(), population.meals(), population.mealResourceObligations()));
        PhysicalReplicaRecord replica = PhysicalReplicaRecord.expected(depot,
                ReferenceContainerCustody.semanticKind(state, depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(state, depot), ReferenceContainerCustody.provenance(depot));
        var custody = PhysicalReplicaCustodyState.empty().declare(replica)
                .observe(depot, 7L, 1L, replica.fingerprint(), replica.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(account, depot, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        AmbientActorLease ambient = new AmbientActorLease(resident, service.standingBody(),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(48_000L), 1L,
                AmbientLeaseStatus.PREPARED, AmbientGoalKind.PATROL, service.standingBody());
        state = AmbientLeaseStateProcess.transition(ModeledActorBodyFacts.present(AmbientLeaseStateProcess.prepare(state, ambient), resident),
                resident, AmbientLeaseStatus.HOT);
        ResidentMealStarted started = ResidentMealProcess.selectSourceAtYield(state, resident, 48_000L).orElseThrow();
        state = ResidentActivityProcess.reduceMealStarted(state, resident, started);
        ResidentMeal meal = started.meal();
        assertTrue(FrontierWorldRuntimeDefinition.scheduledHeld(state,
                ResidentMealProcess.progress(meal, 48_001L)),
                "the HOT executor owns this meal; its COLD timer must not append no-op retries");
        assertEquals(AmbientGoalKind.MEAL, state.ambientLeases().get(resident).goal());
        state = ResidentMealProcess.reduceHotArrived(state, resident,
                new ResidentMealHotArrived(resident, 1L, service.standingBody(),
                        ModeledActorBodyFacts.hotObservation(state, meal.executionId(), 1L)));
        assertEquals(ResidentMeal.Phase.TAKE, state.humanPopulation().meals().get(resident).phase());
        state = ResidentMealProcess.reduceHotPrepared(state, resident,
                new ResidentMealHotEffectPrepared(resident,
                        new ResidentMealPhysicalStep(ResidentMeal.Phase.TAKE, 0, 64, 1L, 1L, 1L, meal.executionId())));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertFatalityRetainsMealResources(state, resident, 421L, 48_001L,
                ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING);
        var remaining = new FungiblePhysicalObservation.Stack(source.address(), "minecraft:bread", 63);
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(resident,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident), 0), "minecraft:bread", 1);
        state = ResidentActivityProcess.reduceMealEffectObserved(state, resident, new ResidentMealHotEffectObserved(
                resident, ResidentMeal.Phase.TAKE, 1L, service.standingBody(), List.of(remaining), List.of(hand), meal.executionId()), 48_001L);
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        assertEquals(ResidentMeal.Phase.CLEAR_ACCESS, state.humanPopulation().meals().get(resident).phase());
        assertFatalityRetainsMealResources(state, resident, 421L, 48_001L,
                ResidentMealResourceObligation.CustodyState.ACTOR_PORTION);
        var boundary = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).accessBoundary();
        SurfaceAnchor firstExit = ResidentMealKnownNavigation.returnPath(state, state.humanPopulation().meals().get(resident))
                .stream().filter(surface -> boundary.cleared(surface.standingBody())).findFirst().orElseThrow();
        assertEquals(firstExit, ResidentMealKnownNavigation.returnPath(state,
                state.humanPopulation().meals().get(resident)).getLast(),
                "HOT navigation must target the first semantic exit, not the remaining parking route");
        state = ModeledActorBodyFacts.inspected(state, resident, firstExit.standingBody());
        state = ResidentMealProcess.reduceHotAccessCleared(state, resident,
                new ResidentMealHotAccessCleared(resident, 1, firstExit.standingBody(),
                        ModeledActorBodyFacts.hotObservation(state, meal.executionId(), 1L)));
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"),
                "releasing the access point is not consumption");
        assertEquals(ResidentMeal.Phase.CONSUME, state.humanPopulation().meals().get(resident).phase(),
                "a supported exit permits eating without waiting for the exact parking point");
        FrontierWorldState outside = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotAccessCleared(outside, resident,
                new ResidentMealHotAccessCleared(resident, 1, meal.clearingSurface().standingBody(),
                        ModeledActorBodyFacts.hotObservation(outside, meal.executionId(), 1L))),
                "a duplicate clearance cannot advance an already consumable portion");
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        state = ResidentMealProcess.reduceHotPrepared(state, resident,
                new ResidentMealHotEffectPrepared(resident,
                        new ResidentMealPhysicalStep(ResidentMeal.Phase.CONSUME, -1, 1, 1L, 0L, 1L, meal.executionId())));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        BodyPosition displaced = firstExit.standingBody();
        assertNotEquals(meal.clearingSurface().standingBody(), displaced);
        assertTrue(ResidentMealProcess.mayConsumeAt(state, state.humanPopulation().meals().get(resident), displaced));
        FrontierWorldState beforeConsumption = state;
        assertFatalityRetainsMealResources(state, resident, 421L, 48_002L,
                ResidentMealResourceObligation.CustodyState.ACTOR_CONSUMPTION_PENDING);
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotObserved(beforeConsumption,
                resident, new ResidentMealHotEffectObserved(resident, ResidentMeal.Phase.CONSUME, 1L,
                        service.standingBody(), List.of(), List.of(), meal.executionId()), 48_002L));
        ResidentMealHotEffectObserved consumed = new ResidentMealHotEffectObserved(resident,
                ResidentMeal.Phase.CONSUME, 1L, displaced, List.of(), List.of(), meal.executionId());
        var otherClearedPose = meal.clearingSurface().standingBody();
        FrontierWorldState resourceAcknowledged = ResidentMealProcess.reduceHotObserved(state, resident,
                new ResidentMealHotEffectObserved(resident, ResidentMeal.Phase.CONSUME, 1L,
                        otherClearedPose, List.of(), List.of(), meal.executionId()), 48_002L);
        assertEquals(state.actorLocations(), resourceAcknowledged.actorLocations(),
                "an exact resource receipt must not become a competing physical position writer");
        assertFalse(resourceAcknowledged.humanPopulation().meals().containsKey(resident));
        var plannedEvents = ResidentMealProcess.planHotObserved(state, consumed, 48_002L);
        var healthFact = assertInstanceOf(ResidentStarvationIntegrated.class, plannedEvents.getFirst().payload());
        assertEquals(100, healthFact.next().severityUnits());
        assertEquals(2, healthFact.next().exposureRemainder());
        var hotEvents = plannedEvents.subList(1, plannedEvents.size());
        assertEquals(4, hotEvents.size());
        int rate = state.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(48_002L);
        FrontierRuleset.ResidentLife rules = state.bootstrap().ruleset().residentLife();
        long oldNeedDue = state.humanPopulation().nutrition(resident).nextThresholdTick(rules, rate);
        long nextNeedDue = state.humanPopulation().nutrition(resident)
                .consumeBreadAt(48_002L, rules, rate).nextThresholdTick(rules, rate);
        var hotNeed = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                hotEvents.get(1).payload());
        assertEquals(ResidentNeedProcess.review(resident, oldNeedDue).id(), hotNeed.scheduleId());
        assertEquals(ResidentNeedProcess.review(resident, nextNeedDue), hotNeed.replacement());
        var retiredMeal = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Cancelled.class,
                hotEvents.get(2).payload());
        assertEquals(ResidentMealProcess.progress(meal, 48_001L).id(), retiredMeal.scheduleId());
        var activityWake = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                hotEvents.get(3).payload());
        assertEquals(ResidentActivityProcess.review(resident, 48_003L), activityWake.replacement());
        state = ResidentStarvationProcess.reduce(state, resident, healthFact);
        state = ResidentActivityProcess.reduceMealEffectObserved(state, resident, consumed, 48_002L);
        assertEquals(displaced, state.actorLocations().get(resident).body());
        assertEquals(100, state.humanPopulation().health(resident).starvation().severityUnits());
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        assertEquals(ResidentNutritionStatus.NOURISHED, state.humanPopulation().nutrition(resident).status());
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertFalse(state.actorMovements().containsKey(resident));
        FrontierWorldState resumedCold = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        resumedCold = AmbientLeaseStateProcess.transition(resumedCold, resident, AmbientLeaseStatus.DRAINING);
        resumedCold = AmbientLeaseStateProcess.release(resumedCold, new AmbientLeaseReleased(resident,
                resumedCold.actorLocations().get(resident).body(), resumedCold.actorLocations().get(resident).condition().health()));
        assertEquals(displaced, resumedCold.actorLocations().get(resident).body(), "presentation release cannot move the body back to its former meal station");
        assertFalse(resumedCold.actorMovements().containsKey(resident),
                "confirmed eating does not retain an obligatory return journey");
        assertEquals(63, resumedCold.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        FrontierWorldState terminal = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotObserved(terminal, resident, consumed, 48_002L));
        assertFalse(state.ambientLeases().get(resident).goal() == AmbientGoalKind.MEAL);
        assertFalse(state.inventory().fungibleResources().claims().containsKey(meal.claimId()));
        assertEquals(ResidentActivityChoice.Kind.IDLE,
                ResidentActivityCoordinator.assess(state, resident, 48_003L).kind());
    }

    /** Forks the real ordinary meal path; no outcome is inserted by a fixture. */
    private static void assertFatalityRetainsMealResources(FrontierWorldState state, SubjectId resident,
            long seed, long atTick, ResidentMealResourceObligation.CustodyState custody) {
        var meal = state.humanPopulation().meals().get(resident);
        var body = ActorBodyAuthority.current(state, resident);
        var location = state.actorLocations().get(resident);
        var death = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(body,
                location.body(), location.condition().health(), java.util.Optional.empty(),
                java.util.Optional.of(meal.executionId()), "environment");
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), seed);
        var codec = new FrontierWorldStateCodec();
        var due = ResidentMealProcess.progress(meal, Math.addExact(atTick, 100L));
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), state,
                        new SimInstant(atTick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), codec,
                        base.projectionMapper(), base.limits(), List.of(due), base.transactionCommitter(),
                        base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter()));
        var checkpoint = engine.checkpoint();
        var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:meal-fatality-" + custody.name().toLowerCase(java.util.Locale.ROOT));
        var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(
                io.farfrontier.palemirror.frontier.v3.api.FrontierCommand.LEGACY_SCHEMA_VERSION, id,
                checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), death);
        var result = engine.submit(command);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, result, result::toString);
        var after = codec.decode(engine.checkpoint().canonicalState());
        var obligation = after.humanPopulation().mealResourceObligations().get(resident);
        assertEquals(custody, obligation.custodyState());
        assertEquals(body, obligation.body());
        assertEquals(meal.executionId(), obligation.executionId());
        assertEquals(meal.portion(), obligation.portion());
        assertEquals(meal.pendingPhysicalStep(), obligation.pendingPhysicalStep());
        assertEquals(atTick, obligation.retiredAtTick());
        assertEquals(custody == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING,
                ResidentMealPhysicalAuthority.pendingForContainer(after, meal.depotId()),
                "retiring the activity must not release a possibly changed source container");
        assertEquals(custody == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING,
                ContainerPhysicalAuthorityComposition.pending(after, meal.depotId()));
        assertFalse(ContainerPhysicalAuthorityComposition.pending(after, new SubjectId("container:other")));
        assertEquals(ActorLifeStatus.DEAD, after.actorLocations().get(resident).condition().status());
        assertFalse(after.humanPopulation().meals().containsKey(resident));
        assertTrue(after.actorExecutions().actors().get(resident).current().isEmpty());
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(after, resident));
        assertEquals(state.inventory(), after.inventory(), "fatality is not a take, consumption, drop or destruction receipt");
        assertEquals(state.humanPopulation().nutrition(resident), after.humanPopulation().nutrition(resident));
        assertFalse(engine.checkpoint().schedules().stream().anyMatch(action -> action.id().equals(due.id())));
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotArrived(after, resident,
                new ResidentMealHotArrived(resident, 1L, location.body(),
                        ModeledActorBodyFacts.hotObservation(state, meal.executionId(), 1L))));
        if (meal.pendingPhysicalStep().isPresent()) {
            var stale = new ResidentMealHotEffectObserved(resident, meal.phase(),
                    meal.pendingPhysicalStep().orElseThrow().ambientRevision(), location.body(), List.of(), List.of(), meal.executionId());
            assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.planHotObserved(after, stale, atTick));
        }
        var population = after.humanPopulation();
        population = population.withSchedule(meal.settlementId(), population.schedule(meal.settlementId()));
        population = population.withStarvation(resident, population.health(resident).starvation());
        var recovered = codec.decode(codec.encode(after.withHumanPopulation(population)));
        assertEquals(obligation, recovered.humanPopulation().mealResourceObligations().get(resident));
        var oldSchema = codec.encode(after);
        oldSchema[4] = (byte) 231;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(oldSchema),
                "an old snapshot cannot silently hydrate without retired resource obligations");
        var foreignEpoch = new ResidentMealResourceObligation(obligation.executionId(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(resident, body.physicalEpoch() + 1L),
                obligation.settlementId(), obligation.depotId(), obligation.sourceAccountId(), obligation.actorAccountId(),
                obligation.portion(), obligation.custodyState(), obligation.pendingPhysicalStep(), obligation.retiredAtTick());
        var forgedObligations = new LinkedHashMap<>(after.humanPopulation().mealResourceObligations());
        forgedObligations.put(resident, foreignEpoch);
        var original = after.humanPopulation();
        var forged = after.withHumanPopulation(new HumanPopulation(original.households(), original.residents(), original.birthJobs(),
                original.health(), original.quarantines(), original.migrations(), original.provisions(), original.nutrition(),
                original.medicalOperations(), original.schedules(), original.meals(), forgedObligations));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(codec.encode(forged)),
                "historical food evidence cannot claim a different physical incarnation");
        assertThrows(IllegalArgumentException.class, () -> new ResidentMealResourceObligation(meal.executionId(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(new SubjectId("resident:foreign"), body.physicalEpoch()),
                meal.settlementId(), meal.depotId(), meal.sourceAccountId(), meal.actorAccountId(), meal.portion(),
                custody, meal.pendingPhysicalStep(), atTick));
        if (custody == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING) {
            var sourceLayout = after.inventory().fungibleResources().bindings().values().stream()
                    .filter(binding -> binding.accountId().equals(meal.sourceAccountId()))
                    .map(binding -> new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), binding.quantity())).toList();
            var unapplied = new ResidentMealResourceEffectObserved(body, meal.executionId(), meal.pendingPhysicalStep().orElseThrow(),
                    ResidentMealResourceEffectObserved.Outcome.TAKE_UNAPPLIED, sourceLayout, List.of());
            var cancelled = assertRetiredMealReceipt(after, unapplied, seed, atTick);
            assertFalse(cancelled.humanPopulation().mealResourceObligations().containsKey(resident));
            assertFalse(ContainerPhysicalAuthorityComposition.pending(cancelled, meal.depotId()));
            assertEquals(after.inventory().fungibleResources().lots(), cancelled.inventory().fungibleResources().lots());
            assertEquals(after.inventory().fungibleResources().accounts().get(meal.sourceAccountId()).lotQuantities(),
                    cancelled.inventory().fungibleResources().accounts().get(meal.sourceAccountId()).lotQuantities());
            assertFalse(cancelled.inventory().fungibleResources().claims().containsKey(meal.claimId()));
            // The ordinary fixture has one bread stack; model the alternative already-applied take.
            assertEquals(1, sourceLayout.size());
            var sourceStack = sourceLayout.getFirst();
            var remainder = new FungiblePhysicalObservation.Stack(sourceStack.address(), sourceStack.itemKind(),
                    sourceStack.quantity() - meal.portion().quantity());
            var held = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(resident,
                    SceneLease.deterministicEntityId(after.bootstrap().worldId(), resident), 0),
                    meal.portion().itemKind(), meal.portion().quantity());
            var applied = new ResidentMealResourceEffectObserved(body, meal.executionId(), meal.pendingPhysicalStep().orElseThrow(),
                    ResidentMealResourceEffectObserved.Outcome.TAKE_APPLIED, List.of(remainder), List.of(held));
            var transferred = assertRetiredMealReceipt(after, applied, seed, atTick);
            assertEquals(ResidentMealResourceObligation.CustodyState.ACTOR_PORTION,
                    transferred.humanPopulation().mealResourceObligations().get(resident).custodyState());
            assertFalse(ContainerPhysicalAuthorityComposition.pending(transferred, meal.depotId()));
            assertEquals(after.inventory().fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind()),
                    transferred.inventory().fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind()));
            assertEquals(meal.portion().quantity(), transferred.inventory().fungibleResources().accounts()
                    .get(meal.actorAccountId()).lotQuantities().values().stream().mapToInt(Integer::intValue).sum());
            assertRetiredPortionDispositions(transferred, resident, seed, atTick);
            assertThrows(IllegalArgumentException.class, () -> ResidentMealResourceProcess.reduce(after, resident,
                    new ResidentMealResourceEffectObserved(body, meal.executionId(), meal.pendingPhysicalStep().orElseThrow(),
                            ResidentMealResourceEffectObserved.Outcome.TAKE_UNAPPLIED, List.of(remainder), List.of())),
                    "a changed source cannot release the pending fence as an unapplied take");
        } else if (custody == ResidentMealResourceObligation.CustodyState.ACTOR_CONSUMPTION_PENDING) {
            assertRetiredPortionDispositions(after, resident, seed, atTick);
            var receipt = new ResidentMealResourceEffectObserved(body, meal.executionId(), meal.pendingPhysicalStep().orElseThrow(),
                    ResidentMealResourceEffectObserved.Outcome.CONSUMPTION_APPLIED, List.of(), List.of());
            var consumed = assertRetiredMealReceipt(after, receipt, seed, atTick);
            assertFalse(consumed.humanPopulation().mealResourceObligations().containsKey(resident));
            assertFalse(consumed.inventory().fungibleResources().accounts().containsKey(meal.actorAccountId()));
            assertFalse(consumed.inventory().fungibleResources().claims().containsKey(meal.claimId()));
            assertEquals(after.inventory().fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind())
                    - meal.portion().quantity(), consumed.inventory().fungibleResources().totalQuantity(meal.settlementId(), meal.portion().itemKind()));
        } else if (!after.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(meal.actorAccountId())).toList().isEmpty()) {
            assertRetiredPortionDispositions(after, resident, seed, atTick);
        }
    }

    /** Actual TAKE/CONSUME path forks above retain the original claim, pocket and death fence. */
    private static void assertRetiredPortionDispositions(FrontierWorldState state, SubjectId resident, long seed, long atTick) {
        var retained = state.humanPopulation().mealResourceObligations().get(resident);
        var binding = state.inventory().fungibleResources().bindings().values().stream()
                .filter(value -> value.accountId().equals(retained.actorAccountId())).findFirst().orElseThrow();
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var codec = new FrontierWorldStateCodec();
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), seed);
        for (var outcome : ResidentMealPortionDispositionObserved.Outcome.values()) {
            var carrier = java.util.UUID.nameUUIDFromBytes((resident.value() + ":food-drop").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var receipt = new ResidentMealPortionDispositionObserved(retained.body(), retained.executionId(),
                    binding.authorityEpoch(), outcome, outcome == ResidentMealPortionDispositionObserved.Outcome.WORLD_DROP
                            ? java.util.Optional.of(carrier) : java.util.Optional.empty());
            byte[] encoded = codecs.encode(receipt);
            assertEquals(receipt, codecs.decode(receipt.type(), encoded));
            assertThrows(IllegalArgumentException.class, () -> codecs.decode(receipt.type(),
                    java.util.Arrays.copyOf(encoded, encoded.length - 1)));
            var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                    new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), state,
                            new SimInstant(atTick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), codec,
                            base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(),
                            base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter()));
            var wrongBody = new ResidentMealPortionDispositionObserved(
                    new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(resident, retained.body().physicalEpoch() + 1L),
                    retained.executionId(), receipt.sourceEpoch(), outcome, receipt.worldCarrier());
            var wrongEpoch = new ResidentMealPortionDispositionObserved(retained.body(), retained.executionId(),
                    binding.authorityEpoch() + 1L, outcome, receipt.worldCarrier());
            int attempt = 0;
            for (var payload : List.of(wrongBody, wrongEpoch, receipt, receipt)) {
                var checkpoint = engine.checkpoint();
                var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:portion-disposition-" + attempt);
                var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(
                        io.farfrontier.palemirror.frontier.v3.api.FrontierCommand.LEGACY_SCHEMA_VERSION, id,
                        checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                        io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), payload);
                var result = engine.submit(command);
                if (attempt++ == 2) assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, result, result::toString);
                else assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, result, result::toString);
                assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
            }
            var after = codec.decode(engine.checkpoint().canonicalState());
            assertFalse(after.humanPopulation().mealResourceObligations().containsKey(resident));
            assertFalse(after.inventory().fungibleResources().accounts().containsKey(retained.actorAccountId()));
            assertFalse(after.inventory().fungibleResources().claims().containsKey(retained.claimId()));
            assertEquals(state.actorLocations(), after.actorLocations());
            assertEquals(state.actorExecutions(), after.actorExecutions());
            assertEquals(state.fencedRecovery(), after.fencedRecovery());
            assertEquals(state.humanPopulation().nutrition(), after.humanPopulation().nutrition());
            var totalBefore = state.inventory().fungibleResources().totalQuantity(retained.settlementId(), retained.portion().itemKind());
            if (outcome == ResidentMealPortionDispositionObserved.Outcome.WORLD_DROP) {
                var drop = after.inventory().fungibleResources().accounts().get(new SubjectId("custody:world-" + carrier));
                assertEquals(new ResourceCustody.WorldCarrier(carrier), drop.custody());
                assertEquals(retained.portion().lotQuantities(), drop.lotQuantities());
                assertTrue(drop.claimQuantities().isEmpty());
                var droppedBinding = after.inventory().fungibleResources().bindings().values().stream()
                        .filter(value -> value.accountId().equals(drop.id())).findFirst().orElseThrow();
                assertEquals(new PhysicalStackAddress.WorldEntity(carrier), droppedBinding.address());
                assertEquals(totalBefore, after.inventory().fungibleResources().totalQuantity(retained.settlementId(), retained.portion().itemKind()));
                assertEquals(drop, codec.decode(codec.encode(after)).inventory().fungibleResources().accounts().get(drop.id()));
            } else assertEquals(totalBefore - retained.portion().quantity(),
                    after.inventory().fungibleResources().totalQuantity(retained.settlementId(), retained.portion().itemKind()));
        }
    }

    private static FrontierWorldState assertRetiredMealReceipt(FrontierWorldState state,
            ResidentMealResourceEffectObserved receipt, long seed, long atTick) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        byte[] encoded = codecs.encode(receipt);
        assertEquals(receipt, codecs.decode(receipt.type(), encoded));
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(receipt.type(),
                java.util.Arrays.copyOf(encoded, encoded.length - 1)));
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), seed);
        var codec = new FrontierWorldStateCodec();
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), state,
                        new SimInstant(atTick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), codec,
                        base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(),
                        base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter()));
        var foreign = new ResidentMealResourceEffectObserved(new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(
                receipt.body().actorId(), receipt.body().physicalEpoch() + 1), receipt.executionId(), receipt.step(),
                receipt.outcome(), receipt.remainingSource(), receipt.destination());
        int attempt = 0;
        for (var payload : List.of(foreign, receipt, receipt)) {
            var checkpoint = engine.checkpoint();
            var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:retired-food-" + checkpoint.revision().value()
                    + "-" + (payload == foreign ? "stale" : "receipt"));
            var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(
                    io.farfrontier.palemirror.frontier.v3.api.FrontierCommand.LEGACY_SCHEMA_VERSION, id,
                    checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                    io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), payload);
            boolean expected = attempt++ == 1;
            var result = engine.submit(command);
            if (expected) assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, result, result::toString);
            else assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, result, result::toString);
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
        }
        var after = codec.decode(engine.checkpoint().canonicalState());
        assertEquals(state.humanPopulation().nutrition(), after.humanPopulation().nutrition());
        assertEquals(state.actorLocations(), after.actorLocations());
        assertEquals(state.actorExecutions(), after.actorExecutions());
        assertEquals(state.fencedRecovery(), after.fencedRecovery());
        assertTrue(after.humanPopulation().meals().isEmpty());
        return after;
    }

    @Test void coldCarriedPortionSurvivesFatalityAsResourcesWithoutAnActiveMeal() {
        var fixture = FrontierV3FixtureCatalog.configuration("resident-meal-after-cold-take",
                new WorldId("frontier:cold-meal-fatality"), 41L);
        var resident = new SubjectId("resident:6-1");
        var state = ModeledActorBodyFacts.present(fixture.initialState(), resident);
        assertFatalityRetainsMealResources(state, resident, 41L, fixture.initialInstant().ticks() + 1L,
                ResidentMealResourceObligation.CustodyState.ACTOR_PORTION);
    }

    @Test void coldMealMovesOneClaimThroughHandAndConsumesExactlyOneBreadAcrossRestart() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-cold"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SettlementStructure depotStructure = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(depotStructure).serviceSurface();
        var actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(service, ActorKind.RESIDENT));
        SubjectId bread = new SubjectId("lot:resident-meal-cold-bread");
        SubjectId depotAccount = ReferenceContainerCustody.scopeId(depot);
        var ledger = initial.inventory().fungibleResources().transformCold(depotAccount,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(bread, settlement.id(), "minecraft:bread", 64, "test", List.of()));
        FrontierWorldState stocked = initial.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).inventory(initial.inventory().withFungibleResources(ledger)));
        ResidentMealStarted started = ResidentMealProcess.selectSourceAtYield(stocked, resident, 24_000L).orElseThrow();
        FrontierWorldState state = ResidentMealProcess.reduceStarted(stocked, resident, started);
        ResidentMeal meal = started.meal();
        var due = ResidentMealProcess.progress(meal, 24_001L);
        for (ResidentMeal.Phase phase : List.of(ResidentMeal.Phase.MOVE, ResidentMeal.Phase.TAKE)) {
            var events = ResidentMealProcess.planProgress(state, due);
            var step = (ResidentMealColdStep) events.getFirst().payload();
            assertEquals(phase, step.expectedPhase());
            assertEquals(due.dueAt().ticks(), step.atTick());
            assertEquals(step, FrontierWorldRuntimeDefinition.payloadCodecs().decode(step.type(),
                    FrontierWorldRuntimeDefinition.payloadCodecs().encode(step)));
            state = ResidentMealProcess.reduceColdStep(state, resident, step);
            due = ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled)
                    events.getLast().payload()).replacement();
        }
        assertEquals(ResidentMeal.Phase.CLEAR_ACCESS, state.humanPopulation().meals().get(resident).phase());
        assertEquals(new ResourceCustody.Actor(resident),
                state.inventory().fungibleResources().accounts().get(meal.actorAccountId()).custody());
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        for (int phaseStep = 0; state.humanPopulation().meals().get(resident).phase() != ResidentMeal.Phase.CONSUME
                && phaseStep < 16; phaseStep++) {
            var events = ResidentMealProcess.planProgress(state, due);
            state = ResidentMealProcess.reduceColdStep(state, resident, (ResidentMealColdStep) events.getFirst().payload());
            due = ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled)
                    events.getLast().payload()).replacement();
            state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        }
        assertTrue(ResidentMealProcess.mayConsumeAt(state, state.humanPopulation().meals().get(resident),
                state.actorLocations().get(resident).body()), "COLD also eats at the supported service exit");
        var consumeEvents = ResidentMealProcess.planProgress(state, due);
        ResidentMealColdStep consume = (ResidentMealColdStep) consumeEvents.getFirst().payload();
        assertEquals(ResidentMeal.Phase.CONSUME, consume.expectedPhase());
        int rate = state.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(due.dueAt().ticks());
        FrontierRuleset.ResidentLife rules = state.bootstrap().ruleset().residentLife();
        long oldNeedDue = state.humanPopulation().nutrition(resident).nextThresholdTick(rules, rate);
        long nextNeedDue = state.humanPopulation().nutrition(resident)
                .consumeBreadAt(due.dueAt().ticks(), rules, rate).nextThresholdTick(rules, rate);
        var coldNeed = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                consumeEvents.get(1).payload());
        assertEquals(ResidentNeedProcess.review(resident, oldNeedDue).id(), coldNeed.scheduleId());
        assertEquals(ResidentNeedProcess.review(resident, nextNeedDue), coldNeed.replacement());
        state = ResidentMealProcess.reduceColdStep(state, resident, consume);
        var activityDue = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                consumeEvents.get(2).payload()).replacement();
        assertEquals(ResidentActivityProcess.review(resident, due.dueAt().ticks() + 1L), activityDue);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Cancelled.class,
                consumeEvents.getLast().payload());
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        assertFalse(state.inventory().fungibleResources().claims().containsKey(meal.claimId()));
        assertFalse(state.inventory().fungibleResources().accounts().containsKey(meal.actorAccountId()));
        assertEquals(ResidentNutritionStatus.NOURISHED, state.humanPopulation().nutrition(resident).status());
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertFalse(state.actorMovements().containsKey(resident));
        FrontierWorldState afterConsumption = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceColdStep(afterConsumption,
                resident, consume));
        assertFalse(state.actorMovements().containsKey(resident));
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
    }

    @Test void oneHungryIdleResidentReservesOneDepotBreadUnitAndSurvivesRestart() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-start"), 420L));
        SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        SubjectId resident = initial.bootstrap().settlements().getFirst().residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        SubjectId account = ReferenceContainerCustody.scopeId(depot);
        SubjectId wheat = new SubjectId("lot:bootstrap-1-wheat");
        SubjectId bread = new SubjectId("lot:resident-meal-test-bread");
        ResourceLot output = new ResourceLot(bread, settlement, "minecraft:bread", 64,
                "test-station-output", List.of());
        FungibleResourceLedger resources = initial.inventory().fungibleResources()
                .transformCold(account, Map.of(wheat, 64), Map.of(), output);
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(initial.inventory().withFungibleResources(resources))
                .humanPopulation(initial.humanPopulation().accrueHunger(resident, 24_000L)));
        ResidentMealStarted start = ResidentMealProcess.selectSourceAtYield(state, resident, 24_000L).orElseThrow();
        assertEquals(start, FrontierWorldRuntimeDefinition.payloadCodecs().decode(start.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(start)));
        FrontierWorldState reserved = ResidentMealProcess.reduceStarted(state, resident, start);
        assertEquals(64, reserved.inventory().fungibleResources().totalQuantity(settlement, "minecraft:bread"));
        assertEquals(1, reserved.inventory().fungibleResources().claims().get(start.meal().claimId()).quantity());
        assertEquals(start.meal(), reserved.humanPopulation().meals().get(resident));
        assertFalse(ResidentMealProcess.selectSourceAtYield(reserved, resident, 24_000L).isPresent());
        assertTrue(FrontierWorldRuntimeDefinition.payloadCodecs().types().contains(start.type()));
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reserved));
        assertEquals(reserved.humanPopulation().meals(), recovered.humanPopulation().meals());
        assertEquals(reserved.inventory().fungibleResources(), recovered.inventory().fungibleResources());
    }
}
