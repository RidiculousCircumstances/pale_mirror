package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutePatrolTest {
    @Test
    void clearAndFailedPatrolsRetainNoFictitiousObstructionEvidence() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:patrol-model"), 41L));
        Settlement settlement = state.bootstrap().settlements().getFirst(); SubjectId task = new SubjectId("task:patrol");
        List<ResidentProfile> guards = FrontierWorldStateSupport.availableRouteResidents(state, settlement.id(), HumanCapability.SECURITY);
        var staged = DeclaredRouteFixtures.stagedPatrol(state, settlement, task, guards.getFirst().id(), guards.get(1).id());
        state = staged.state();
        RoutePatrol patrol = RoutePatrol.planned(state, task, settlement, staged.unit());
        RoutePatrol active = patrol;
        while (patrol.active()) patrol = patrol.advance(patrol.safeAdvances().getFirst());
        assertEquals(RoutePatrolStatus.ROUTE_CLEAR, patrol.status());
        assertEquals(RoutePatrolStatus.FAILED, active.fail().status());
    }

    @Test
    void newRouteOwnersCannotCreateAnUnderstrengthPatrol() {
        SubjectId task = new SubjectId("task:understrength");
        assertThrows(IllegalArgumentException.class, () -> new RouteUnitManifest(RouteUnitManifest.idFor(RouteUnitKind.PATROL, task), task,
                RouteUnitKind.PATROL, List.of(new RouteUnitMember(new SubjectId("resident:guard"), RouteUnitDuty.PATROL_LEADER)),
                new SubjectId("resident:guard"), false));

    }
}
