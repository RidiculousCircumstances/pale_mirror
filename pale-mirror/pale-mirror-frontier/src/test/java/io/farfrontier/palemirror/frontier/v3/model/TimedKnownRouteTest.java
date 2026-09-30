package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimedKnownRouteTest {
    @Test
    void reconstructsTheSameMidRouteBodyWithoutPerEdgeEventsOrLateExecutionDrift() {
        var route = List.of(SurfaceAnchor.at(1, 64, 1), SurfaceAnchor.at(2, 64, 1),
                SurfaceAnchor.at(3, 64, 1), SurfaceAnchor.at(3, 64, 2));
        SubjectId actor = new SubjectId("resident:timed-route");
        MovementOrder order = new MovementOrder(actor, actor, 1L, 1L, List.of(route.getLast()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        TimedKnownRoute travel = new TimedKnownRoute(order, route, 100L, 20L, 1L);
        assertEquals(160L, travel.arrivalTick());
        assertEquals(route.getFirst(), travel.surfaceAt(119L));
        assertEquals(route.get(1), travel.surfaceAt(120L));
        assertEquals(route.get(2), travel.surfaceAt(159L));
        assertEquals(route.getLast(), travel.surfaceAt(160L));
        assertEquals(route.getLast(), travel.surfaceAt(10_000L));
        assertFalse(travel.arrivedBy(159L));
        assertTrue(travel.arrivedBy(160L));
    }

    @Test
    void rejectsAnUnorderedOrImpossiblePedestrianJourney() {
        SubjectId actor = new SubjectId("resident:timed-route");
        SurfaceAnchor start = SurfaceAnchor.at(1, 64, 1), goal = SurfaceAnchor.at(3, 64, 1);
        MovementOrder order = new MovementOrder(actor, actor, 1L, 1L, List.of(goal),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        assertThrows(IllegalArgumentException.class,
                () -> new TimedKnownRoute(order, List.of(start, goal), 100L, 20L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new TimedKnownRoute(order, List.of(start), 100L, 20L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new TimedKnownRoute(order, List.of(start, SurfaceAnchor.at(2, 64, 1), goal), 100L, 0L, 1L));
    }
}
