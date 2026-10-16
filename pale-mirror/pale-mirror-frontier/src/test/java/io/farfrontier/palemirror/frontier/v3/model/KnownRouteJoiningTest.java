package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KnownRouteJoiningTest {
    @Test void offRouteApproachJoinsItineraryButChangedTailStillUsesCurrentGeometry() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:route-join"), 41L));
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var itinerary = java.util.stream.IntStream.rangeClosed(0, 30).mapToObj(x -> SurfaceAnchor.at(x, 63, 0)).toList();
        var start = SurfaceAnchor.at(0, 63, 8);
        var actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var order = new MovementOrder(actor, actor, 1L, 1L, List.of(itinerary.getLast()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        var route = KnownRouteJoining.path(knowledge, start, order, itinerary, true);
        assertEquals(start, route.getFirst()); assertEquals(itinerary.getLast(), route.getLast());
        assertTrue(route.contains(itinerary.get(10)), "off-route departure must join before the distant goal");
        assertEquals(route.size(), new HashSet<>(route).size(), "joining cannot introduce an approach/tail loop");
        var blocked = itinerary.get(15);
        var changed = knowledge.withTaskExclusions(Set.of(blocked));
        var detour = KnownRouteJoining.path(changed, start, order, itinerary, true);
        assertFalse(detour.contains(blocked)); assertEquals(order.legalStations().getFirst(), detour.getLast());
        changed.requireRoute(detour);
        var unavailable = knowledge.withTaskExclusions(Set.of(itinerary.getLast()));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> KnownRouteJoining.path(unavailable, start, order, itinerary, true));
    }
}
