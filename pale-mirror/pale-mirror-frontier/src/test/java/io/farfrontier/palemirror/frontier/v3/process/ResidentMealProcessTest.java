package io.farfrontier.palemirror.frontier.v3.process;

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
        assertTrue(ServiceAccessCoordinator.depotAvailableForMeal(state, depot, second),
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
                    && ServiceAccessCoordinator.depotAvailableForMeal(state, depot, second))
                releasedDuringReturn = true;
        }
        assertFalse(boundary.cleared(state.actorLocations().get(first).body()));
        assertFalse(ServiceAccessCoordinator.depotAvailableForMeal(state, depot, second));
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
                    && ServiceAccessCoordinator.depotAvailableForMeal(state, depot, second))
                releasedDuringReturn = true;
        }
        assertFalse(state.humanPopulation().meals().containsKey(first),
                "confirmed consumption retires the meal without pretending that return travel is eating");
        assertTrue(releasedDuringReturn, "access must release before consumption, not after returning home");
        assertFalse(state.actorMovements().containsKey(first));
        assertTrue(state.humanPopulation().meals().containsKey(second),
                "the second resident's independent meal must survive the first return");
        assertTrue(ServiceAccessCoordinator.depotAvailableForMeal(state, depot, second));
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
        actors.put(resident, ActorLocation.standingOn(service));
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
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.HOT);
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(resident,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident)),
                meal.portion().itemKind(), meal.portion().quantity());
        state = ResidentMealProcess.reduceHotHandMaterialized(state, resident,
                new ResidentMealHotHandMaterialized(resident, lease.revision(), hand));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(ResidentMeal.Phase.CLEAR_ACCESS, state.humanPopulation().meals().get(resident).phase());
        assertTrue(ResidentMealProcess.planColdStep(state, resident, now + 1L).isEmpty());
        state = ResidentMealProcess.reduceHotHandReleased(state, resident,
                new ResidentMealHotHandReleased(resident, lease.revision(), hand));
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.DRAINING);
        state = AmbientLeaseStateProcess.release(state,
                new AmbientLeaseReleased(resident, handoff, state.actorLocations().get(resident).condition().health()));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        for (int step = 0; state.humanPopulation().meals().containsKey(resident) && step < 16; step++) {
            now = nextColdTick(state, resident, now + 1L);
            state = ResidentMealProcess.reduceColdStep(state, resident,
                    ResidentMealProcess.planColdStep(state, resident, now).orElseThrow());
        }
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertFalse(state.actorMovements().containsKey(resident));
        assertEquals(meal.clearingSurface().standingBody(), state.actorLocations().get(resident).body());
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
        actors.put(resident, ActorLocation.standingOn(service));
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
        actors.put(resident, ActorLocation.standingOn(service));
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
                population.medicalOperations(), population.schedules(), population.meals()));
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
        state = AmbientLeaseStateProcess.transition(AmbientLeaseStateProcess.prepare(state, ambient),
                resident, AmbientLeaseStatus.HOT);
        ResidentMealStarted started = ResidentMealProcess.selectSourceAtYield(state, resident, 48_000L).orElseThrow();
        state = ResidentActivityProcess.reduceMealStarted(state, resident, started);
        ResidentMeal meal = started.meal();
        assertTrue(FrontierWorldRuntimeDefinition.scheduledHeld(state,
                ResidentMealProcess.progress(meal, 48_001L)),
                "the HOT executor owns this meal; its COLD timer must not append no-op retries");
        assertEquals(AmbientGoalKind.MEAL, state.ambientLeases().get(resident).goal());
        state = ResidentMealProcess.reduceHotArrived(state, resident,
                new ResidentMealHotArrived(resident, 1L, service.standingBody()));
        assertEquals(ResidentMeal.Phase.TAKE, state.humanPopulation().meals().get(resident).phase());
        state = ResidentMealProcess.reduceHotPrepared(state, resident,
                new ResidentMealHotEffectPrepared(resident,
                        new ResidentMealPhysicalStep(ResidentMeal.Phase.TAKE, 0, 64, 1L, 1L, 1L)));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var remaining = new FungiblePhysicalObservation.Stack(source.address(), "minecraft:bread", 63);
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(resident,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident)), "minecraft:bread", 1);
        state = ResidentActivityProcess.reduceMealEffectObserved(state, resident, new ResidentMealHotEffectObserved(
                resident, ResidentMeal.Phase.TAKE, 1L, service.standingBody(), List.of(remaining), List.of(hand)), 48_001L);
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        assertEquals(ResidentMeal.Phase.CLEAR_ACCESS, state.humanPopulation().meals().get(resident).phase());
        var boundary = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).accessBoundary();
        SurfaceAnchor firstExit = ResidentMealKnownNavigation.returnPath(state, state.humanPopulation().meals().get(resident))
                .stream().filter(surface -> boundary.cleared(surface.standingBody())).findFirst().orElseThrow();
        state = ResidentMealProcess.reduceHotAccessCleared(state, resident,
                new ResidentMealHotAccessCleared(resident, 1, firstExit.standingBody()));
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"),
                "releasing the access point is not consumption");
        state = ResidentMealProcess.reduceHotAccessCleared(state, resident,
                new ResidentMealHotAccessCleared(resident, 1, meal.clearingSurface().standingBody()));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        state = ResidentMealProcess.reduceHotPrepared(state, resident,
                new ResidentMealHotEffectPrepared(resident,
                        new ResidentMealPhysicalStep(ResidentMeal.Phase.CONSUME, -1, 1, 1L, 0L, 1L)));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        ResidentMealHotEffectObserved consumed = new ResidentMealHotEffectObserved(resident,
                ResidentMeal.Phase.CONSUME, 1L, meal.clearingSurface().standingBody(), List.of(), List.of());
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
        assertEquals(100, state.humanPopulation().health(resident).starvation().severityUnits());
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        assertEquals(ResidentNutritionStatus.NOURISHED, state.humanPopulation().nutrition(resident).status());
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertFalse(state.actorMovements().containsKey(resident));
        FrontierWorldState resumedCold = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        resumedCold = AmbientLeaseStateProcess.transition(resumedCold, resident, AmbientLeaseStatus.DRAINING);
        resumedCold = AmbientLeaseStateProcess.release(resumedCold, new AmbientLeaseReleased(resident,
                meal.clearingSurface().standingBody(), resumedCold.actorLocations().get(resident).condition().health()));
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
        actors.put(resident, ActorLocation.standingOn(service));
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
        assertEquals(meal.clearingSurface().standingBody(), state.actorLocations().get(resident).body());
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
