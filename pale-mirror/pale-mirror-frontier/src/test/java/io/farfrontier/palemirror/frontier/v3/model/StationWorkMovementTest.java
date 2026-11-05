package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Integration contracts for production goals, common travel, and durable owner continuation. */
class StationWorkMovementTest {
    @Test void arrivalWakesAParkedProductionOwnerAfterItsCompletionReviewWasConsumed() throws Exception {
        var fixture = started(); var start = order(fixture); var job = fixture.job();
        var state = ActorMovementProcess.reduceStarted(fixture.state(), job.workerId(), start);
        var base = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var movement = start.movement();
        // While the movement owns progress, production's one-shot review may be absent.
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(),state,
                        new SimInstant(301),base.commandPlanner(),base.scheduledPlanner(),base.reducer(),base.stateCodec(),base.projectionMapper(),
                        base.limits(),List.of(ActorMovementProcess.progress(movement,301)),base.transactionCommitter(),base.stateValidator()));
        var review = ProductionContinuation.at(job,302).id();
        for (int step=0; step<64 && engine.checkpoint().schedules().stream().noneMatch(a->a.id().equals(review)); step++) {
            long due=engine.checkpoint().schedules().stream().mapToLong(a->a.dueAt().ticks()).min().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(due,engine.checkpoint().instant().ticks()+1)),new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1,64));
            assertEquals(EngineStatus.Kind.ACTIVE,result.status().kind(),result.status().toString());
        }
        assertEquals(1,engine.checkpoint().schedules().stream().filter(a->a.id().equals(review)).count(), "remaining="+engine.checkpoint().schedules()+"; instant="+engine.checkpoint().instant());
        var resumed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(fixture.state().inventory(),resumed.inventory(),"arrival only wakes work; it cannot consume inputs");
        assertEquals(job,resumed.productionJobs().get(job.id()));
    }
    record Fixture(FrontierWorldState state, ProductionJob job) { }
    private static Fixture started() {
        var state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:station-common-movement"), 41L)), StrategicTaskStatus.PENDING);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var start = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        return new Fixture(ProductionProcess.reduceStarted(state, task.ownerId(), start), start.job());
    }
    private static ActorMovementStarted order(Fixture fixture) {
        return ProductionProcess.planCompletion(fixture.state(), ProductionProcess.complete(fixture.job(), 300L)).stream()
                .map(ProposedEvent::payload).filter(ActorMovementStarted.class::isInstance).map(ActorMovementStarted.class::cast).findFirst().orElseThrow();
    }
    @Test void loadedWorkerReleasesTheSourceAtItsExitAndRetainsCargoAndStationGoalAcrossRecovery() throws Exception {
        var fixture = started(); var job = fixture.job();
        var state = BakeryTestClock.arrive(fixture.state(), job, 300L);
        var pickup = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 10_000L)).stream()
                .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow();
        assertEquals(BakeryColdStep.Action.PICKUP, pickup.action());
        state = ProductionProcess.reduceBakeryColdStep(state, job.settlementId(), pickup);
        job = state.productionJobs().get(job.id());
        assertEquals(StationWorkState.Phase.STATION_LOAD, job.stationWork().orElseThrow().phase());
        var source = StationWorkStorage.source(job);
        var boundary = ServiceAccessCoordinator.boundary(state, source);
        assertTrue(boundary.occupied(state.actorLocations().get(job.workerId()).body()));
        var start = order(new Fixture(state, job));
        state = ActorMovementProcess.reduceStarted(state, job.workerId(), start);
        var movement = start.movement();
        assertTrue(movement.context().clearancePoint().isEmpty(), "ordinary station travel is not a standalone service exit");
        var planned = ActorMovementProcess.plan(state, ActorMovementProcess.progress(movement, 10_001L), 10_001L);
        var receipt = planned.stream().map(ProposedEvent::payload).filter(ActorMovementColdAdvanced.class::isInstance)
                .map(ActorMovementColdAdvanced.class::cast).findFirst().orElseThrow();
        var fullRoute = receipt.plannedRoute().orElseThrow().route();
        int exit = boundary.firstExit(fullRoute);
        assertTrue(exit > 0 && exit < fullRoute.size() - 1, "the source exit precedes the station approach");
        assertTrue(sourceOccupied(state, source, job.workerId()));
        assertFalse(ServiceAccessCoordinator.witnessedActorMovementExit(state, movement,
                state.actorLocations().get(job.workerId()).body()), "a phase change or accepted route is not physical departure");
        assertTrue(ServiceAccessCoordinator.witnessedActorMovementExit(state, movement,
                fullRoute.get(exit).standingBody()),
                "HOT must publish the source exit even though ordinary station travel has no service-exit context");
        var prefix = fullRoute.subList(0, exit + 1);
        var obstructed = state.recordPhysicalDelta(new PhysicalDelta(fullRoute.get(1).support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:source-exit-obstructed"));
        var worker = job.workerId();
        assertThrows(IllegalArgumentException.class, () -> ActorMovementProcess.reduceColdAdvanced(obstructed, worker, receipt),
                "an exit checkpoint cannot bypass the owner's current geometry validation");
        try (var ignored = PedestrianRoutePlanning.bind((geometry, from, to) -> { throw new AssertionError("route receipt replay searched"); })) {
            state = ActorMovementProcess.reduceColdAdvanced(state, job.workerId(), receipt);
        }
        var travel = state.actorMovements().get(job.workerId()).coldTravel().orElseThrow();
        assertEquals(prefix, travel.route());
        assertEquals(10_001L + exit * travel.ticksPerEdge(), travel.arrivalTick());
        assertEquals(travel.arrivalTick(), planned.stream().map(ProposedEvent::payload)
                .filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class::cast)
                .findFirst().orElseThrow().replacement().dueAt().ticks());
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertTrue(boundary.occupied(state.actorLocations().get(job.workerId()).body()), "admission alone cannot release the source");
        var moving = state.actorMovements().get(job.workerId());
        long due = travel.arrivalTick();
        var arrival = ActorMovementProcess.plan(state, ActorMovementProcess.progress(moving, due), due).stream()
                .map(ProposedEvent::payload).filter(ActorMovementColdAdvanced.class::isInstance).map(ActorMovementColdAdvanced.class::cast).findFirst().orElseThrow();
        var occupied = state;
        state = ActorMovementProcess.reduceColdAdvanced(state, job.workerId(), arrival);
        assertTrue(boundary.cleared(state.actorLocations().get(job.workerId()).body()));
        assertTrue(ServiceAccessCoordinator.wakePoints(occupied, state).contains(source));
        assertFalse(sourceOccupied(state, source, job.workerId()));
        assertFalse(ServiceAccessCoordinator.witnessedActorMovementExit(state, movement,
                state.actorLocations().get(job.workerId()).body()), "the already witnessed exit is not repeated");
        assertEquals(occupied.inventory(), state.inventory(), "exiting moves no goods and awards no processing");
        assertEquals(occupied.productionJobs(), state.productionJobs());
        assertEquals(movement.order(), state.actorMovements().get(job.workerId()).order());
        var cleared = state; var actor = job.workerId();
        assertThrows(IllegalArgumentException.class, () -> ActorMovementProcess.reduceColdAdvanced(cleared, actor, arrival));
        state = codec.decode(codec.encode(state));
        state = BakeryTestClock.arrive(state, job, due + 1L);
        assertFalse(state.actorMovements().containsKey(actor));
        assertEquals(StationWorkGoal.current(state, job).station(), state.actorLocations().get(actor).supportingSurface());
    }
    @Test void stationBecomingUnavailableRetainsItsExactApproachAsAWaitWithoutAnEntryGrant() {
        var fixture = started(); var job = fixture.job();
        var state = BakeryTestClock.arrive(fixture.state(), job, 300L);
        var pickup = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, 10_000L)).stream()
                .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow();
        state = ProductionProcess.reduceBakeryColdStep(state, job.settlementId(), pickup);
        job = state.productionJobs().get(job.id());
        var start = order(new Fixture(state, job));
        state = ActorMovementProcess.reduceStarted(state, job.workerId(), start);
        var point = ProductionServiceAccess.point(state, job);
        var blocked = state.withInventory(state.inventory().withSurfaceStatus(point, ContainerSurfaceStatus.CONFLICT));
        var provider = ActorMovementProviders.require(start.movement());
        provider.validate(blocked, start.movement());
        var identity = provider.serviceApproach(blocked, start.movement()).orElseThrow();
        ServiceAccessCoordinator.requireDeclared(blocked, identity);
        assertFalse(ServiceAccessCoordinator.available(blocked, identity));
        var demand = new ProductionServiceAccess().demands(blocked, point).stream()
                .filter(value -> value.identity().equals(identity)).findFirst().orElseThrow();
        assertFalse(demand.readyToEnter());
        assertTrue(ServiceAccessCoordinator.mayAdvance(blocked, identity, state.actorLocations().get(job.workerId()).supportingSurface()));
        assertFalse(ServiceAccessCoordinator.mayAdvance(blocked, identity, StationWorkGoal.current(blocked, job).station()));
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(blocked));
        ServiceAccessCoordinator.requireDeclared(recovered, identity);
        assertFalse(ServiceAccessCoordinator.available(recovered, identity));
        assertEquals(state.productionJobs(), blocked.productionJobs());
        ServiceAccessCoordinator.requireDeclared(state, identity);
        assertTrue(new ProductionServiceAccess().demands(state, point).stream().filter(value -> value.identity().equals(identity))
                .findFirst().orElseThrow().readyToEnter(), "restored readiness belongs to the same retained approach");
    }
    private static boolean sourceOccupied(FrontierWorldState state, SubjectId source, SubjectId actor) {
        return new ProductionServiceAccess().demands(state, source).stream()
                .anyMatch(demand -> demand.identity().actorId().equals(actor) && demand.presence() == ServiceAccessDemand.Presence.OCCUPIED);
    }
    @Test void warmedMovementReferencesStillRejectWithdrawalOfTheirExactJob() {
        var fixture = started(); var job = fixture.job(); var start = order(fixture);
        var state = ActorMovementProcess.reduceStarted(fixture.state(), job.workerId(), start);
        ActorMovementStateSupport.validate(state.bootstrap(), state.actorMovements(), state.actorLocations(), state.humanPopulation(),
                state.inventory(), state.actorExecutions(), state.shipments(), state.unitGroups(), state.extractionSites(),
                state.strategicPlans(), state.forest(), state.productionJobs(),state.combat());
        var jobs = new HashMap<>(state.productionJobs()); jobs.remove(job.id());
        assertThrows(IllegalArgumentException.class, () -> ActorMovementStateSupport.validate(state.bootstrap(), state.actorMovements(),
                state.actorLocations(), state.humanPopulation(), state.inventory(), state.actorExecutions(), state.shipments(), state.unitGroups(),
                state.extractionSites(), state.strategicPlans(), state.forest(), jobs,state.combat()),
                "unchanged movement and execution cannot retain an absent producer job");
        assertDoesNotThrow(() -> ImmutableInvariantCheck.full(() -> {
            ActorMovementStateSupport.validate(state.bootstrap(), state.actorMovements(), state.actorLocations(), state.humanPopulation(),
                    state.inventory(), state.actorExecutions(), state.shipments(), state.unitGroups(), state.extractionSites(),
                    state.strategicPlans(), state.forest(), state.productionJobs(),state.combat()); return null;
        }));
    }
    @Test void wholeLegRetainsOneClockAndRestartsWithoutAwardingItemsOrPerCellWork() throws Exception {
        var fixture = started(); var job = fixture.job(); var start = order(fixture);
        var state = ActorMovementProcess.reduceStarted(fixture.state(), job.workerId(), start);
        assertEquals(fixture.state().actorLocations(), state.actorLocations(), "goal admission cannot move a body");
        assertEquals(fixture.state().inventory(), state.inventory());
        assertTrue(ProductionProcess.completionHeld(state, ProductionProcess.complete(job, 301L)));
        assertTrue(FrontierProductionWorkSceneSupport.candidate(state, job).isEmpty(), "travel is not a station effect scene");
        var leg = ActorMovementProcess.plan(state, ActorMovementProcess.progress(start.movement(), 301L), 301L).stream()
                .map(ProposedEvent::payload).filter(ActorMovementColdAdvanced.class::isInstance).map(ActorMovementColdAdvanced.class::cast).findFirst().orElseThrow();
        state = ActorMovementProcess.reduceColdAdvanced(state, job.workerId(), leg);
        var movement = state.actorMovements().get(job.workerId()); var interval = movement.coldTravel().orElseThrow();
        assertTrue(interval.route().size() > 2);
        var base = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var waiting = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(
                new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(state.bootstrap().worldId(), state,
                        new SimInstant(301), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                        base.projectionMapper(), base.limits(), List.of(ProductionProcess.complete(job, 301),
                            ActorMovementProcess.progress(movement, interval.arrivalTick())), base.transactionCommitter(), base.stateValidator()));
        var held = waiting.advanceTo(new SimInstant(302), new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(8, 64));
        assertTrue(held.transactions().isEmpty());
        long priorBoundary = 302;
        var nextBoundary = waiting.nextExecutionBoundary().orElseThrow();
        while (nextBoundary.ticks() < interval.arrivalTick()) {
            assertTrue(nextBoundary.ticks() > priorBoundary, "read-only queue audits must advance their own boundary");
            assertTrue(waiting.advanceTo(nextBoundary, new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(8, 64))
                    .transactions().isEmpty(), "a safety audit cannot poll the held producer or advance travel");
            priorBoundary = nextBoundary.ticks();
            nextBoundary = waiting.nextExecutionBoundary().orElseThrow();
        }
        assertEquals(new SimInstant(interval.arrivalTick()), nextBoundary,
                "after read-only audits the retained movement is the next actual work boundary");
        assertEquals(fixture.state().actorLocations(), state.actorLocations(), "retaining an interval cannot replay all its cells");
        var codecs = FrontierWorldPayloadCodecs.create();
        assertEquals(start, codecs.decode(start.type(), codecs.encode(start)));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertEquals(movement, state.actorMovements().get(job.workerId()));
        long due = interval.arrivalTick();
        var events = ActorMovementProcess.plan(state, ActorMovementProcess.progress(movement, due), due);
        var arrival = events.stream().map(ProposedEvent::payload).filter(ActorMovementColdAdvanced.class::isInstance)
                .map(ActorMovementColdAdvanced.class::cast).findFirst().orElseThrow();
        var before = state;
        try (var ignored = PedestrianRoutePlanning.bind((geometry, from, to) -> { throw new AssertionError("arrival replay searched"); })) {
            state = ActorMovementProcess.reduceColdAdvanced(state, job.workerId(), arrival);
        }
        assertEquals(before.inventory(), state.inventory());
        assertEquals(before.productionJobs(), state.productionJobs());
        // A service approach can end outside the throat; the same command retains its remaining goal.
        state = BakeryTestClock.arrive(state, job, due);
        assertFalse(state.actorMovements().containsKey(job.workerId()));
        assertEquals(StationWorkGoal.current(state, job).station(), state.actorLocations().get(job.workerId()).supportingSurface());
        assertFalse(ProductionProcess.completionHeld(state, ProductionProcess.complete(job, due + 1)));
        assertEquals(BakeryColdStep.Action.PICKUP, ProductionProcess.planCompletion(state, ProductionProcess.complete(job, due + 1)).stream()
                .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow().action());
    }
    @Test void planningWaitBelongsToMovementAndCannotConsumeInputOrBecomeAStationFailure() throws Exception {
        var fixture = started(); var count = new java.util.concurrent.atomic.AtomicInteger();
        try (var ignored = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            count.incrementAndGet(); return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING,
                    List.of(), 0, 0, "station travel pending");
        })) {
            var start = order(fixture);
            assertEquals(0, count.get(), "production goal admission does not compute a path");
            var state = ActorMovementProcess.reduceStarted(fixture.state(), fixture.job().workerId(), start);
            var observation = PedestrianRoutePlanning.observe(() -> ActorMovementProcess.plan(state,
                    ActorMovementProcess.progress(start.movement(), 301L), 301L));
            assertTrue(count.get() > 0); assertFalse(observation.requests().isEmpty());
            assertTrue(observation.result().stream().noneMatch(event -> event.payload() instanceof ActorMovementColdAdvanced));
            assertEquals(fixture.state().inventory(), state.inventory());
            assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        }
    }
}
