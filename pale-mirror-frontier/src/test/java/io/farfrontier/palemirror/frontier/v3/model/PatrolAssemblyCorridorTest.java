package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatrolAssemblyCorridorTest {
    @Test
    void compilesDistinctResidentIngressesThroughTheNamedHallPortAndFirstRouteEdge() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:patrol-ingress"), 41L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        List<ResidentProfile> candidates = FrontierWorldStateSupport.availableRouteResidents(state, settlement.id(), ResidentProfession.SECURITY_WORKER);
        RouteUnitManifest unit = RouteUnitManifest.patrol(new SubjectId("task:patrol-ingress"), candidates.getFirst().id(), List.of(candidates.get(1).id()));

        PatrolAssembly assembly = PatrolAssemblyCorridor.compile(state, unit.ownerId(), settlement, unit);
        TraversalTopology route = state.routeTopology().supplyTraversalTopology(state.bootstrap(), settlement.id());
        PatrolAssembly.Member leader = assembly.members().get(unit.leaderId());
        SubjectId scoutId = unit.memberIds().stream().filter(id -> !id.equals(unit.leaderId())).findFirst().orElseThrow();
        PatrolAssembly.Member scout = assembly.members().get(scoutId);

        assertEquals(route.linearCorridorSurfaces().get(1), leader.corridor().getLast());
        assertEquals(route.linearCorridorSurfaces().getFirst(), scout.corridor().getLast());
        assertTrue(leader.topology().edges().stream().allMatch(edge -> edge.grade() <= 1 && edge.clearance() >= 2),
                "the compiler retains only declared pedestrian edges; it does not infer a bypass through terrain");
        assertTrue(completes(assembly));
    }

    @Test
    void refusesAnAlreadyBlockedFirstInspectionEdge() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:patrol-ingress-blocked"), 41L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        TraversalTopology route = initial.routeTopology().supplyTraversalTopology(initial.bootstrap(), settlement.id());
        TraversalEdgeId edge = route.edges().getFirst().id();
        FrontierWorldState blocked = initial.withRouteTopology(new RouteTopology(initial.routeTopology().replacementSupplyRoutes(),
                java.util.Map.of(settlement.id(), java.util.Map.of(edge, TraversalAvailability.BLOCKED))));
        List<ResidentProfile> candidates = FrontierWorldStateSupport.availableRouteResidents(blocked, settlement.id(), ResidentProfession.SECURITY_WORKER);
        RouteUnitManifest unit = RouteUnitManifest.patrol(new SubjectId("task:patrol-ingress-blocked"), candidates.getFirst().id(), List.of(candidates.get(1).id()));

        assertThrows(IllegalArgumentException.class, () -> PatrolAssemblyCorridor.compile(blocked, unit.ownerId(), settlement, unit));
    }

    private static boolean completes(PatrolAssembly initial) {
        PatrolAssembly current = initial;
        for (int moves = 0; moves < 256 && !current.complete(); moves++) {
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) return false;
            current = current.advanceOne(safe.getFirst());
        }
        return current.complete();
    }
}
