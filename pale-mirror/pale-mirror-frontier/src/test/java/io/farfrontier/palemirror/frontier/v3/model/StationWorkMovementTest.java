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
    @Test void warmedMovementReferencesStillRejectWithdrawalOfTheirExactJob() {
        var fixture = started(); var job = fixture.job(); var start = order(fixture);
        var state = ActorMovementProcess.reduceStarted(fixture.state(), job.workerId(), start);
        ActorMovementStateSupport.validate(state.bootstrap(), state.actorMovements(), state.actorLocations(), state.humanPopulation(),
                state.inventory(), state.actorExecutions(), state.shipments(), state.unitGroups(), state.extractionSites(),
                state.strategicPlans(), state.forest(), state.productionJobs());
        var jobs = new HashMap<>(state.productionJobs()); jobs.remove(job.id());
        assertThrows(IllegalArgumentException.class, () -> ActorMovementStateSupport.validate(state.bootstrap(), state.actorMovements(),
                state.actorLocations(), state.humanPopulation(), state.inventory(), state.actorExecutions(), state.shipments(), state.unitGroups(),
                state.extractionSites(), state.strategicPlans(), state.forest(), jobs),
                "unchanged movement and execution cannot retain an absent producer job");
        assertDoesNotThrow(() -> ImmutableInvariantCheck.full(() -> {
            ActorMovementStateSupport.validate(state.bootstrap(), state.actorMovements(), state.actorLocations(), state.humanPopulation(),
                    state.inventory(), state.actorExecutions(), state.shipments(), state.unitGroups(), state.extractionSites(),
                    state.strategicPlans(), state.forest(), state.productionJobs()); return null;
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
        assertEquals(new SimInstant(interval.arrivalTick()), waiting.nextExecutionBoundary().orElseThrow(),
                "the waiting production owner cannot keep an unaddressed old poll before the actual travel deadline");
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
