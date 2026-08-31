package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutePatrolTest {
    @Test
    void clearAndFailedPatrolsRetainNoFictitiousObstructionEvidence() {
        SubjectId task = new SubjectId("task:patrol");
        RoutePatrol patrol = new RoutePatrol(task, new SubjectId("settlement:test"), RouteUnitManifest.patrol(task, new SubjectId("resident:guard"), List.of(new SubjectId("resident:scout"))),
                List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0)), 0, RoutePatrolStatus.EN_ROUTE, Optional.empty());
        assertEquals(RoutePatrolStatus.ROUTE_CLEAR, patrol.advance(1).status());
        assertEquals(RoutePatrolStatus.FAILED, patrol.fail().status());
    }

    @Test
    void newRouteOwnersCannotCreateAnUnderstrengthPatrolOrCargoEscort() {
        SubjectId task = new SubjectId("task:understrength");
        assertThrows(IllegalArgumentException.class, () -> new RouteUnitManifest(RouteUnitManifest.idFor(RouteUnitKind.PATROL, task), task,
                RouteUnitKind.PATROL, List.of(new RouteUnitMember(new SubjectId("resident:guard"), RouteUnitDuty.PATROL_LEADER)),
                new SubjectId("resident:guard"), false));
        SubjectId operation = new SubjectId("operation:understrength");
        assertThrows(IllegalArgumentException.class, () -> new RouteUnitManifest(RouteUnitManifest.idFor(RouteUnitKind.CARGO_ESCORT, operation), operation,
                RouteUnitKind.CARGO_ESCORT, List.of(new RouteUnitMember(new SubjectId("resident:crew"), RouteUnitDuty.CARGO_CREW),
                        new RouteUnitMember(new SubjectId("resident:guard"), RouteUnitDuty.ESCORT)), new SubjectId("resident:guard"), false));
    }
}
