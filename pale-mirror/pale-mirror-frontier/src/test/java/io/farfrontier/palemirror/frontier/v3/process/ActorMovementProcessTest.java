package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ActorMovementProcessTest {
    @Test void idleOccupantLeavesTemporaryBufferThroughPersistedInterruptibleMovement() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:service-buffer-turnover"), 421L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId actor = settlement.residents().getFirst().id();
        ServiceAccessPoint point = SettlementServiceAccessPoints.forSettlement(state, settlement.id()).getFirst();
        state = state.withActorBody(actor, point.waitingSurfaces().getFirst().standingBody());
        ActorMovement movement = ResidentServiceTurnover.select(state, actor, 1L).orElseThrow();
        assertTrue(point.waitingSurfaces().stream().noneMatch(movement.order()::arrivedAt));
        assertTrue(point.boundary().cleared(movement.order().legalStations().getFirst().standingBody()));
        var planned = ResidentActivityProcess.plan(state, ResidentActivityProcess.review(actor, 1L));
        ActorMovementStarted started = assertInstanceOf(ActorMovementStarted.class, planned.getFirst().payload());
        assertEquals(movement, started.movement());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(started, codecs.decode(started.type(), codecs.encode(started)));
        FrontierWorldState before = state;
        assertThrows(IllegalArgumentException.class, () -> ActorMovementProcess.reduceStarted(before,
                settlement.residents().get(1).id(), started));
        state = ActorMovementProcess.reduceStarted(state, actor, started);
        assertEquals(before.humanPopulation().nutrition(actor), state.humanPopulation().nutrition(actor),
                "leaving cannot create a feeding receipt");
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        FrontierWorldState retained = state;
        assertThrows(IllegalArgumentException.class, () -> ActorMovementProcess.reduceStarted(retained, actor, started));
        for (int leg = 0; leg < 4 && state.actorMovements().containsKey(actor); leg++) {
            var order = state.actorMovements().get(actor);
            long due = order.coldTravel().map(value -> value.arrivalTick()).orElse(2L);
            var advanced = assertInstanceOf(ActorMovementColdAdvanced.class,
                    ActorMovementProcess.plan(state, ActorMovementProcess.progress(order, due), due).getFirst().payload());
            state = ActorMovementProcess.reduceColdAdvanced(state, actor, advanced);
            if (state.actorMovements().get(actor) != null
                    && state.actorMovements().get(actor).coldTravel().isPresent()) {
                assertInstanceOf(ActivityInterruptionPlanner.Waiting.class,
                        new ActorMovementInterruptionPlanner().assess(state, actor, 1L));
                FrontierWorldState travelling = state;
                assertDoesNotThrow(() -> ResidentActivityProcess.admission(travelling,
                        ResidentActivityProcess.review(actor, 1L)));
            }
        }
        assertFalse(state.actorMovements().containsKey(actor));
        assertEquals(movement.order().legalStations().getFirst(), state.actorLocations().get(actor).supportingSurface());
        assertTrue(ResidentServiceTurnover.select(state, actor, 100L).isEmpty(),
                "resting outside the temporary area must not start an endless idle journey");
    }
    @Test void unavailableKnownRouteIsCheckedOnlyWhenAdmittedAndKeepsTheExactOrder() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:actor-movement-route-wait"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId actorId = settlement.residents().getFirst().id();
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        FrontierWorldState state = initial.withActorBody(actorId,
                SettlementDepotServicePort.forDepot(depot).serviceSurface().standingBody());
        SurfaceAnchor destination = ServiceAccessCoordinator.mealClearingSurface(state, actorId).orElseThrow();
        MovementOrder order = new MovementOrder(actorId, actorId, 0L, 1L, List.of(destination),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        ActorMovement movement = new ActorMovement(order, 27_000L,
                new ActorMovementContext.ServiceExit(settlement.id(), FrontierWorldState.depotId(settlement.id())),
                state.actorExecutions().next(actorId, ActorActivityKind.SERVICE_EXIT, actorId));
        state = retainMovement(state, movement);
        state = state.recordPhysicalDelta(new PhysicalDelta(destination.support(), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:blocked-movement-destination"));
        var action = ActorMovementProcess.progress(movement, 27_001L);
        assertFalse(ActorMovementProcess.held(state, action));
        var events = ActorMovementProcess.plan(state, action, 27_001L);
        assertEquals(1, events.size());
        var retry = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                events.getFirst().payload());
        assertEquals(action.id(), retry.scheduleId());
        assertEquals(ActorMovementProcess.progress(movement, 27_021L), retry.replacement());
        assertEquals(movement, state.actorMovements().get(actorId));
    }

    @Test void separateMovementKeepsTheActorUnavailableToWorkAndAnotherMealUntilArrival() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:actor-movement-exclusive"), 421L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId actorId = settlement.residents().getFirst().id();
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        SurfaceAnchor destination = ServiceAccessCoordinator.mealClearingSurface(state, actorId).orElseThrow();
        MovementOrder order = new MovementOrder(actorId, actorId, 0L, 1L, List.of(destination),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        ActorMovement movement = new ActorMovement(order, 27_000L,
                new ActorMovementContext.ServiceExit(settlement.id(), FrontierWorldState.depotId(settlement.id())),
                state.actorExecutions().next(actorId, ActorActivityKind.SERVICE_EXIT, actorId));
        assertTrue(FrontierWorldStateSupport.availableForNewAssignment(state, resident));
        state = retainMovement(state, movement);
        assertFalse(FrontierWorldStateSupport.availableForNewAssignment(state, resident));
        assertFalse(ResidentActivityCoordinator.mayStartOrdinaryWork(state, actorId, 27_001L));
        assertFalse(ResidentActivityCoordinator.ordinaryWorkPermitted(state, actorId, 27_001L));
        assertTrue(ResidentActivityCoordinator.requestsYield(state, actorId, 27_001L));
        assertTrue(ResidentMealOpportunity.find(state, actorId).isEmpty());
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(Map.of())
                .actorExecutions(state.actorExecutions().finish(movement.executionId())));
        assertTrue(FrontierWorldStateSupport.availableForNewAssignment(state, resident));
    }

    @Test void changedKnownRouteAtomicallyCheckpointsTheSameActorBeforeHotOrRestartCanSeeIt() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:actor-movement-obstruction"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId actorId = settlement.residents().getFirst().id();
        SubjectId depotId = FrontierWorldState.depotId(settlement.id());
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SurfaceAnchor station = SettlementDepotServicePort.forDepot(depot).serviceSurface();
        FrontierWorldState state = initial.withActorBody(actorId, station.standingBody());
        SurfaceAnchor destination = ServiceAccessCoordinator.mealClearingSurface(state, actorId).orElseThrow();
        MovementOrder order = new MovementOrder(actorId, actorId, 0L, 1L, List.of(destination),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        ActorMovement movement = new ActorMovement(order, 27_000L,
                new ActorMovementContext.ServiceExit(settlement.id(), depotId),
                state.actorExecutions().next(actorId, ActorActivityKind.SERVICE_EXIT, actorId));
        SubjectId foreignSettlement = initial.bootstrap().settlements().get(1).id();
        FrontierWorldState beforeOrder = state;
        assertThrows(IllegalArgumentException.class, () -> retainMovement(beforeOrder,
                new ActorMovement(order, 27_000L,
                        new ActorMovementContext.ServiceExit(foreignSettlement, depotId), movement.executionId())));
        assertThrows(IllegalArgumentException.class, () -> retainMovement(beforeOrder,
                new ActorMovement(order, 27_000L, new ActorMovementContext.ServiceExit(settlement.id(),
                        FrontierWorldState.depotId(foreignSettlement)), movement.executionId())));
        assertThrows(IllegalArgumentException.class, () -> beforeOrder.withChanges(
                FrontierWorldStateUpdate.begin().actorMovements(Map.of(foreignSettlement, movement))));
        state = retainMovement(state, movement);
        var initialAction = ActorMovementProcess.progress(movement, 27_001L);
        var started = ActorMovementProcess.plan(state, initialAction, 27_001L);
        state = ActorMovementProcess.reduceColdAdvanced(state, actorId,
                assertInstanceOf(ActorMovementColdAdvanced.class, started.getFirst().payload()));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var route = state.actorMovements().get(actorId).coldTravel().orElseThrow();
        assertTrue(route.route().size() > 2);
        var premature = ActorMovementProcess.progress(state.actorMovements().get(actorId),
                route.departedAtTick() + 1L);
        assertFalse(ActorMovementProcess.held(state, premature));
        var corrected = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                ActorMovementProcess.plan(state, premature, premature.dueAt().ticks()).getFirst().payload());
        assertEquals(route.arrivalTick(), corrected.replacement().dueAt().ticks());
        long midpoint = route.departedAtTick() + route.ticksPerEdge();
        BodyPosition asOf = ActorMovementProcess.bodyAt(state, actorId, midpoint);
        assertNotEquals(station.standingBody(), asOf);
        assertNotEquals(destination.standingBody(), asOf);
        PhysicalDelta changed = new PhysicalDelta(route.route().get(2).support(), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:movement-route-obstruction");
        CommandPlan.Accepted planned = assertInstanceOf(CommandPlan.Accepted.class,
                FrontierWorldPhysicalObservationProcess.plan(state, new PhysicalDeltaObserved(changed), midpoint));
        ActorMovementColdAdvanced checkpoint = assertInstanceOf(ActorMovementColdAdvanced.class,
                planned.events().get(1).payload());
        assertEquals(midpoint, checkpoint.atTick());
        assertTrue(checkpoint.arrivedSurface().isEmpty());
        assertEquals(checkpoint, FrontierWorldRuntimeDefinition.payloadCodecs().decode(checkpoint.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(checkpoint)));
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 421L);
        var configured = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                state.bootstrap().worldId(), state, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(midpoint),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(ActorMovementProcess.progress(
                        state.actorMovements().get(actorId), route.arrivalTick())), base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configured);
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:actor-movement-obstruction");
        var accepted = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1,
                commandId, state.bootstrap().worldId(), engine.checkpoint().revision(),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(midpoint),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId),
                new PhysicalDeltaObserved(changed)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, accepted,
                accepted::toString);
        FrontierWorldState committed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(asOf, committed.actorLocations().get(actorId).body());
        assertTrue(committed.actorMovements().get(actorId).coldTravel().isEmpty());
        state = state.recordPhysicalDelta(changed);
        state = ActorMovementProcess.reduceColdAdvanced(state, actorId, checkpoint);
        assertEquals(asOf, state.actorLocations().get(actorId).body());
        assertTrue(state.actorMovements().get(actorId).coldTravel().isEmpty());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void lateMovementCompletionCannotFinishSuccessorEvenWithReusedRouteRevision() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:execution-late-movement"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId actor = settlement.residents().getFirst().id();
        SurfaceAnchor body = initial.actorLocations().get(actor).supportingSurface();
        var order = new MovementOrder(actor, actor, 0L, 1L, List.of(body), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        var old = new ActorMovement(order, 1L,
                new ActorMovementContext.ServiceExit(settlement.id(), FrontierWorldState.depotId(settlement.id())),
                initial.actorExecutions().next(actor, ActorActivityKind.SERVICE_EXIT, actor));
        var first = retainMovement(initial, old);
        var oldEvent = new ActorMovementColdAdvanced(actor, 1L, 2L, old.executionId());
        var afterFirst = ActorMovementProcess.reduceColdAdvanced(first, actor, oldEvent);
        assertFalse(afterFirst.actorMovements().containsKey(actor));
        var successor = new ActorMovement(order, 1L, old.context(),
                afterFirst.actorExecutions().next(actor, ActorActivityKind.SERVICE_EXIT, actor));
        var next = retainMovement(afterFirst, successor);
        assertNotEquals(ActorMovementProcess.progress(old, 2L).id(), ActorMovementProcess.progress(successor, 2L).id());
        assertThrows(IllegalArgumentException.class, () -> ActorMovementProcess.reduceColdAdvanced(next, actor, oldEvent));
        assertEquals(successor, next.actorMovements().get(actor));
        next.actorExecutions().requireCurrent(successor.executionId());
        assertEquals(next, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(next)));
    }

    private static FrontierWorldState retainMovement(FrontierWorldState state, ActorMovement movement) {
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .actorMovements(Map.of(movement.order().actorId(), movement))
                .actorExecutions(state.actorExecutions().begin(movement.executionId(),
                        movement.executionId().generation() - 1L)));
    }
}
