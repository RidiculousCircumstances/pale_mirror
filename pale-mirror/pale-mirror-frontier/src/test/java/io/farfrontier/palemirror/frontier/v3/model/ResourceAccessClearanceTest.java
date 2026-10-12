package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceAccessClearanceTest {
    @Test void confirmationCreatesOnlyAGoalAndMovementOwnsPendingPlanningAcrossRecovery() throws Exception {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:clearance-planning"), 41));
        var home = initial.bootstrap().settlements().getFirst();
        var depot = FrontierWorldState.depotId(home.id());
        var actor = home.residents().getFirst().id();
        var point = SettlementDepotServicePort.forDepot(home.structures().stream()
                .filter(s -> s.kind() == StructureKind.DEPOT).findFirst().orElseThrow());
        var execution = initial.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        var state = ActorExecutionComposition.LIFECYCLE.prepareVacant(initial, execution)
                .commit(initial, FrontierWorldStateUpdate.begin()).withActorBody(actor, point.serviceSurface().standingBody());
        ActorMovement movement;
        try (var binding = PedestrianRoutePlanning.bind((g, from, to) -> {
            throw new AssertionError("resource confirmation cannot search routes");
        })) {
            movement = ResourceAccessClearance.select(state, depot, execution, 3L).orElseThrow();
            assertEquals(execution, movement.executionId());
            state = ActorMovementProcess.reduceStarted(state, actor, new ActorMovementStarted(movement));
        }
        var waits = new PedestrianPlanningWakeIndex();
        try (var binding = PedestrianRoutePlanning.bind((g, from, to) ->
                new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING,
                    List.of(), 1, 1, "pending"));
             var continuation = PedestrianPlanningContinuations.bind(waits)) {
            var action = ActorMovementProcess.progress(movement, 4L);
            var events = FrontierWorldRuntimeDefinition.planScheduled(state, action);
            assertEquals(1, events.size());
            var retry = assertInstanceOf(ScheduleEffect.Rescheduled.class,
                    events.getFirst().payload());
            assertEquals(1, waits.waitingCount());
            assertEquals(movement, state.actorMovements().get(actor));
            assertEquals(point.serviceSurface().standingBody(), state.actorLocations().get(actor).body());
            waits.remove(action.id());
            var codec = new FrontierWorldStateCodec();
            var recovered = codec.decode(codec.encode(state));
            FrontierWorldProcessCatalog.restorePlanningWaits(FrontierWorldRuntimeDefinition.processRegistry(), recovered,
                    new FrontierExecutionView(initial.bootstrap().worldId(), new Revision(0), new SimInstant(4),
                            List.of(retry.replacement())));
            assertEquals(1, waits.waitingCount());
            assertEquals(execution, recovered.actorMovements().get(actor).executionId());
        }
    }
    @Test void occupiedExitRetainsCallerMovementInsteadOfPretendingThatServiceSpaceWasCleared() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:clearance-obligation"), 41));
        var home = new SubjectId("settlement:1"); var depot = FrontierWorldState.depotId(home);
        var point = SettlementDepotServicePort.forDepot(FrontierWorldStateSupport.settlement(state.bootstrap(), home)
                .structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow());
        var actors = state.humanPopulation().residents().values().stream().filter(r -> r.settlementId().equals(home))
                .map(ResidentProfile::id).sorted().toList();
        var actor = actors.getFirst();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        var positions = new LinkedHashMap<>(state.actorLocations());
        positions.put(actor, positions.get(actor).withBody(point.serviceSurface().standingBody()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(positions));
        var exits = KnownServiceExitNavigation.supportedExitStations(state, home, depot, point.serviceSurface());
        assertFalse(exits.isEmpty()); assertTrue(exits.size() < actors.size());
        for (int i = 0; i < exits.size(); i++) positions.put(actors.get(i + 1), positions.get(actors.get(i + 1)).withBody(exits.get(i).standingBody()));
        var blocked = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(positions));
        var movement = ResourceAccessClearance.select(blocked, depot, execution, 3).orElseThrow();
        assertEquals(execution, movement.executionId());
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> new ServiceExitMovementProvider().route(blocked, movement, point.serviceSurface()));
        var free = actors.get(1); positions.put(free, positions.get(free).withBody(exits.getFirst().offset(12, 0, 12).standingBody()));
        var released = blocked.withChanges(FrontierWorldStateUpdate.begin().actorLocations(positions));
        assertFalse(new ServiceExitMovementProvider().route(released, movement, point.serviceSurface()).isEmpty(), "same retained goal can continue after a place is freed");
        positions.put(actor, positions.get(actor).withBody(exits.getFirst().standingBody()));
        var cleared = released.withChanges(FrontierWorldStateUpdate.begin().actorLocations(positions));
        assertTrue(ResourceAccessClearance.select(cleared, depot, execution, 4).isEmpty(), "only actual spatial clearance has no remaining movement obligation");
    }
}
