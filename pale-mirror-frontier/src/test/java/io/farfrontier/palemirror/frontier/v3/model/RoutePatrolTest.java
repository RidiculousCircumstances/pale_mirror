package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutePatrolTest {
    @Test
    void clearAndFailedPatrolsRetainNoFictitiousObstructionEvidence() {
        RoutePatrol patrol = new RoutePatrol(new SubjectId("task:patrol"), new SubjectId("settlement:test"), new SubjectId("resident:guard"),
                List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0)), 0, RoutePatrolStatus.EN_ROUTE, Optional.empty());
        assertEquals(RoutePatrolStatus.ROUTE_CLEAR, patrol.advance(1).status());
        assertEquals(RoutePatrolStatus.FAILED, patrol.fail().status());
    }
}
