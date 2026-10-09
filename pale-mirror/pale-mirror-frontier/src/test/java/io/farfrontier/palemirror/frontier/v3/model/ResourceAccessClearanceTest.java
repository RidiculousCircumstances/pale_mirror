package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.process.ServiceExitMovementProvider;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceAccessClearanceTest {
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
