package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownBlockNavigationProjectionTest {
    @Test void sameSolidIsCeilingAndUpperFloorWithoutExcludingItsTop() {
        var facts = facts(); var view = KnownBlockNavigationProjection.project(facts.keySet(), p -> Optional.of(facts.get(p)));
        var lower = SurfaceAnchor.at(0, 60, 0); var upper = SurfaceAnchor.at(0, 63, 0);
        assertEquals(List.of(lower, upper), view.supports().get(new TerrainColumn(0, 0)));
        var geometry = geometry(view, Set.of());
        assertFalse(geometry.blocked(lower)); assertFalse(geometry.blocked(upper));
        assertTrue(geometry.blocked(SurfaceAnchor.at(0, 62, 0)), "upper floor is solid from below");
        assertTrue(geometry.canTraverse(upper, SurfaceAnchor.at(1, 63, 0)));
        var denied = geometry(view, Set.of(upper.support()));
        assertTrue(denied.supportExcluded(upper)); assertTrue(denied.blocked(upper));
        assertFalse(denied.blocked(lower), "denying a contact must not fill the space below it");
    }
    @Test void removalWithdrawsSupportAndMissingFactsStayUnknownRatherThanAirOrProvedNoPath() {
        var facts = facts(); var upper = SurfaceAnchor.at(0, 63, 0);
        facts.put(upper.support(), KnownBlockNavigationProjection.Cell.EMPTY);
        var removed = KnownBlockNavigationProjection.project(facts.keySet(), p -> Optional.of(facts.get(p)));
        assertTrue(removed.absentSupports().contains(upper.support()));
        assertFalse(geometry(removed, Set.of()).knowsSupport(upper));
        var footprint = Set.copyOf(facts.keySet()); facts.remove(new BlockPosition(0, 61, 0));
        var unknown = KnownBlockNavigationProjection.project(footprint, p -> Optional.ofNullable(facts.get(p)));
        var failure = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> KnownPedestrianNavigation.plannedBetween(geometry(unknown, Set.of()), SurfaceAnchor.at(0, 60, 0), SurfaceAnchor.at(1, 60, 0)));
        assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, failure.status());
    }
    private static Map<BlockPosition, KnownBlockNavigationProjection.Cell> facts() {
        var facts = new HashMap<BlockPosition, KnownBlockNavigationProjection.Cell>();
        for (int x = 0; x <= 1; x++) for (int y = 60; y <= 66; y++)
            facts.put(new BlockPosition(x, y, 0), y == 60 || y == 63
                    ? KnownBlockNavigationProjection.Cell.FULL_SUPPORT : KnownBlockNavigationProjection.Cell.EMPTY);
        return facts;
    }
    private static PedestrianRouteGeometry geometry(KnownBlockNavigationProjection.View view, Set<BlockPosition> forbidden) {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:block-projection"), 41);
        var survey = new BoundedPedestrianApproach.SurveyedSurface() {
            public SurfaceAnchor at(int x, int z) { var levels = surfacesAt(x, z); return levels.isEmpty() ? null : levels.getLast(); }
            public List<SurfaceAnchor> surfacesAt(int x, int z) { return view.supports().getOrDefault(new TerrainColumn(x, z), List.of()); }
            public boolean excluded(SurfaceAnchor surface) { return view.absentSupports().contains(surface.support()); }
            public boolean clearanceKnown(SurfaceAnchor surface) {
                for (int y = 0; y <= 3; y++) if (view.unknown().contains(surface.support().offset(0, y, 0))) return false;
                return true;
            }
        };
        return new ProfiledPedestrianGeometry(KnownPedestrianNavigation.geometry(bootstrap, view.volumes(),
                BoundedPedestrianApproach.excluding(survey, forbidden)), UnitNavigationProfile.VILLAGER, view.volumes()::contains);
    }
}
