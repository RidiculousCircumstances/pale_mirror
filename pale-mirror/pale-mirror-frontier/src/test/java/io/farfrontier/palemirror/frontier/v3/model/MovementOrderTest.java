package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MovementOrderTest {
    @Test
    void farmerGoalBecomesAWorkNeutralOrderWithBoundedKnownRoute() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(hot.state(), hot.job());
        MovementOrder order = goal.movementOrder();
        assertEquals(hot.job().id(), order.ownerId());
        assertEquals(hot.job().workerId(), order.actorId());
        assertEquals(goal.nextWorkSlot(), order.goalOrdinal());
        assertEquals(goal.layoutRevision(), order.goalRevision());
        assertEquals(MovementOrder.ArrivalPolicy.EXACT_STATION, order.arrivalPolicy());

        SurfaceAnchor target = order.legalStations().getFirst();
        SurfaceAnchor start = hot.state().resourceSite(hot.site()).layout().cells().get(1).workstation();
        List<SurfaceAnchor> route = KnownPedestrianNavigation.route(hot.state().bootstrap(), start,
                order, Set.of(), (x, z) -> SurfaceAnchor.at(x, target.y(), z));
        assertEquals(start, route.getFirst());
        assertEquals(target, route.getLast());
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> KnownPedestrianNavigation.route(hot.state().bootstrap(), start, order,
                        Set.of(start.support()), (x, z) -> SurfaceAnchor.at(x, target.y(), z)),
                "a blocked starting body cannot be accepted as a zero-length route");
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> KnownPedestrianNavigation.route(hot.state().bootstrap(), start, order,
                        Set.of(target.support()), (x, z) -> SurfaceAnchor.at(x, target.y(), z)),
                "a blocked work station cannot be reported as reached");
    }

    @Test
    void routeOrderCannotSilentlyChangeCapabilityOrArrivalContract() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        MovementOrder order = ResourceSiteHarvestGoal.current(hot.state(), hot.job()).movementOrder();
        assertThrows(IllegalArgumentException.class,
                () -> new MovementOrder(order.ownerId(), order.actorId(), order.goalOrdinal(),
                        order.goalRevision(), List.of(order.legalStations().getFirst(),
                                hot.state().resourceSite(hot.site()).layout().cells().get(1).workstation()),
                        order.capability(), MovementOrder.ArrivalPolicy.EXACT_STATION));
        MovementOrder rail = new MovementOrder(order.ownerId(), order.actorId(), order.goalOrdinal(),
                order.goalRevision(), order.legalStations(), TraversalCapability.RAIL_VEHICLE,
                order.arrivalPolicy());
        assertThrows(IllegalArgumentException.class,
                () -> KnownPedestrianNavigation.route(hot.state().bootstrap(),
                        hot.state().actorLocations().get(hot.job().workerId()).supportingSurface(), rail,
                        Set.of(), (x, z) -> SurfaceAnchor.at(x, order.legalStations().getFirst().y(), z)));
    }

    @Test
    void malformedSurveyIsNotReclassifiedAsOrdinaryRouteUnavailability() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        MovementOrder order = ResourceSiteHarvestGoal.current(hot.state(), hot.job()).movementOrder();
        SurfaceAnchor start = hot.state().resourceSite(hot.site()).layout().cells().get(1).workstation();
        IllegalArgumentException malformed = new IllegalArgumentException("invalid retained terrain survey");
        assertEquals(malformed, assertThrows(IllegalArgumentException.class,
                () -> KnownPedestrianNavigation.route(hot.state().bootstrap(), start, order, Set.of(),
                        (x, z) -> { throw malformed; })));
    }
}
