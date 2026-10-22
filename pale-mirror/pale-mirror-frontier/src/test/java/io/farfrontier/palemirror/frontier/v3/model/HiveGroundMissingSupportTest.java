package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class HiveGroundMissingSupportTest {
    @Test void missingColumnIsSkippedAndAnIsolatedStartReportsRouteUnavailable() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:missing-hive-support"), 91L));
        var home = state.bootstrap().hive().organs().stream()
                .filter(organ -> organ.kind() == HiveOrganKind.HIBERNACULUM).findFirst().orElseThrow();
        var ground = KnownPedestrianGround.forFrontier(state);
        var start = ground.at(-480, -480);
        var destination = ground.at(-476, -480);
        var missing = ground.at(-479, -480);
        assertNotNull(start); assertNotNull(destination); assertNotNull(missing);
        assertFalse(HiveGroundNavigation.route(state, home, start, destination, Set.of(), Set.of(), Set.of()).isEmpty());
        state = new NavigationGeometryObserved(0, Map.of(missing.support(), NavigationObservation.known(NavigationCell.AIR)))
                .apply(state, NavigationGeometryObserved.OWNER);
        assertNull(KnownPedestrianGround.forFrontier(state).at(missing.x(), missing.z()));
        var detour = HiveGroundNavigation.route(state, home, start, destination, Set.of(), Set.of(), Set.of());
        assertEquals(start, detour.getFirst()); assertEquals(destination, detour.getLast());
        assertFalse(detour.contains(missing), "a missing floor cannot be traversed or synthesized");
        var absent = new HashMap<BlockPosition, NavigationObservation>();
        for (var offset : new int[][] {{-1,0}, {1,0}, {0,-1}, {0,1}}) {
            var support = ground.at(start.x() + offset[0], start.z() + offset[1]);
            absent.put(support.support(), NavigationObservation.known(NavigationCell.AIR));
        }
        var isolated = new NavigationGeometryObserved(state.navigationGeometry().revision(), absent)
                .apply(state, NavigationGeometryObserved.OWNER);
        assertThrows(HiveGroundNavigation.RouteUnavailable.class,
                () -> HiveGroundNavigation.route(isolated, home, start, destination, Set.of(), Set.of(), Set.of()));
    }
}
