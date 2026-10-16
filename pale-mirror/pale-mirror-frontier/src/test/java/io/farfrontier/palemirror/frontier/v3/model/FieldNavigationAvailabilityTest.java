package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldNavigationAvailabilityTest {
    @Test void unknownDepartureIsTypedAndRecoversWhenItsObservationArrives() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var state = fixture.state(); var job = fixture.job();
        var start = state.actorLocations().get(job.workerId()).supportingSurface();
        var unknown = new NavigationGeometryObserved(state.navigationGeometry().revision(),
                Map.of(start.support(), NavigationObservation.unavailable(NavigationObservation.Kind.WRITE_PENDING))).apply(state, NavigationGeometryObserved.OWNER);
        var planned = assertThrows(ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable.class,
                () -> ResourceSiteHarvestKnownNavigation.plannedPath(unknown, job));
        var retained = assertThrows(ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable.class,
                () -> ResourceSiteHarvestKnownNavigation.retainedPath(unknown, job));
        assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, planned.status());
        assertEquals(planned.status(), retained.status());
        var restored = new NavigationGeometryObserved(unknown.navigationGeometry().revision(),
                Map.of(start.support(), NavigationObservation.known(NavigationCell.SOLID))).apply(unknown, NavigationGeometryObserved.OWNER);
        assertFalse(ResourceSiteHarvestKnownNavigation.plannedPath(restored, job).isEmpty());
        var blocked = new NavigationGeometryObserved(restored.navigationGeometry().revision(),
                Map.of(start.support().offset(0, 1, 0), NavigationObservation.known(NavigationCell.SOLID)))
                .apply(restored, NavigationGeometryObserved.OWNER);
        assertEquals(PedestrianRouteResult.Status.NO_PATH,
                assertThrows(ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable.class,
                        () -> ResourceSiteHarvestKnownNavigation.plannedPath(blocked, job)).status());
        assertFalse(ResourceSiteHarvestKnownNavigation.plannedPath(restored, job).isEmpty(),
                "a previous refused query cannot poison the next immutable geometry");
    }
}
