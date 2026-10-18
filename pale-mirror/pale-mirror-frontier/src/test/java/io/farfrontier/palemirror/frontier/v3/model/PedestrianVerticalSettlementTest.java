package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianVerticalSettlementTest {
    private final SurfaceAnchor floor = SurfaceAnchor.at(0, 63, 0);
    private final BodyPosition airborne = new BodyPosition(0, 65, 0);
    private PedestrianRouteGeometry geometry(Map<BlockPosition, Optional<NavigationCell>> edits, boolean upperFloor) {
        return new PedestrianRouteGeometry() {
            @Override public WorldBounds bounds() { return new WorldBounds(-16, -16, 32, 32); }
            @Override public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            @Override public List<SurfaceAnchor> surfacesAt(int x, int z) {
                return upperFloor ? List.of(supportAt(x,z), SurfaceAnchor.at(x,64,z)) : List.of(supportAt(x,z));
            }
            @Override public boolean blocked(SurfaceAnchor surface) { return false; }
            @Override public Optional<NavigationCollisionView> collisions() {
                return Optional.of(position -> edits.getOrDefault(position,
                        Optional.of(position.y() == 63 ? NavigationCell.SOLID : NavigationCell.AIR)));
            }
        };
    }
    @Test void settlesAirbornePoseWithoutInventingSupportOrCrossingColumns() {
        var geometry = geometry(Map.of(), false);
        assertEquals(Optional.of(floor), PedestrianVerticalSettlement.confirmed(airborne, geometry, UnitNavigationProfile.VILLAGER));
        assertFalse(geometry.knowsSupport(airborne.supportingSurface()));
        assertTrue(PedestrianVerticalSettlement.confirmed(new BodyPosition(0,66,0), geometry, UnitNavigationProfile.VILLAGER).isEmpty());
    }
    @Test void neverSettlesThroughKnownFloorUnknownBlocksFluidsOrObstructions() {
        assertTrue(PedestrianVerticalSettlement.confirmed(airborne, geometry(Map.of(),true), UnitNavigationProfile.VILLAGER).isEmpty());
        for (var cell : List.of(Optional.<NavigationCell>empty(), Optional.of(NavigationCell.SOLID),
                Optional.of(new NavigationCell(NavigationShape.EMPTY,false)))) {
            assertTrue(PedestrianVerticalSettlement.confirmed(airborne,
                    geometry(Map.of(new BlockPosition(0,64,0),cell),false), UnitNavigationProfile.VILLAGER).isEmpty());
        }
    }
}
