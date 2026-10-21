package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionalNavigationProjectionTest {
    @Test void layeredContactsRegionRemovalUnknownAndCatalogChangesUseOneProjectionContract() {
        var stone = new NavigationBlockState("minecraft:stone", Map.of());
        var air = new NavigationBlockState("minecraft:air", Map.of());
        var lower = new BlockPosition(15, 60, 0); var upper = lower.offset(0, 3, 0); var neighbour = new BlockPosition(16, 60, 0);
        var values = new HashMap<BlockPosition, Optional<NavigationBlockState>>();
        for (int y = 60; y <= 66; y++) values.put(new BlockPosition(15, y, 0), Optional.of(y == 60 || y == 63 ? stone : air));
        values.put(neighbour, Optional.of(stone));
        var original = RegionalBlockMap.copyOf(values); var catalog = NavigationGeometryState.empty();
        var projector = new RegionalNavigationProjection(); var before = projector.read(original, catalog);
        assertEquals(List.of(new SurfaceAnchor(lower), new SurfaceAnchor(upper)), before.geometry().supports().get(new TerrainColumn(15, 0)));
        var broken = original.with(Map.of(upper, Optional.of(air), lower.offset(0, 1, 0), Optional.empty()));
        var after = projector.read(broken, catalog);
        assertTrue(after.geometry().absentSupports().contains(upper));
        assertTrue(after.geometry().unknown().contains(lower.offset(0, 1, 0)));
        assertFalse(after.blocks().containsKey(lower.offset(0, 1, 0)));
        assertSame(before.blocks().in(Region.at(neighbour)), after.blocks().in(Region.at(neighbour)));
        assertSame(after, projector.read(broken, catalog));
        var removed = broken.replace(Map.of(Region.at(neighbour), Map.of()));
        var retired = projector.read(removed, catalog);
        assertFalse(retired.blocks().containsKey(neighbour)); assertFalse(retired.geometry().volumes().contains(neighbour));
        var unavailable = catalog.observe(0, Map.of(), Map.of(stone, NavigationObservation.unavailable(NavigationObservation.Kind.POSITION_REQUIRED)));
        var unknown = projector.read(removed, unavailable);
        assertTrue(unknown.geometry().unknown().contains(lower));
        assertFalse(unknown.geometry().supports().containsKey(new TerrainColumn(15, 0)));
        assertEquals(before, projector.read(original, catalog), "returning to older evidence must not keep successor facts");
    }
}
