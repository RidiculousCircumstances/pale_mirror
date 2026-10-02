package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierResourceSitePlanTest {
    @Test void currentLiveSeedPlacesAllTwelveLargerFieldsWithoutOverwritingOtherPlans() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:graybox"), 20260918065L);
        var sites = FrontierResourceSitePlan.compile(bootstrap);
        assertEquals(12, sites.size());
        assertTrue(sites.values().stream().allMatch(site -> site.layout().cells().size() == 252));
        assertEquals(3024, sites.values().stream().flatMap(site -> site.cropSlots().stream()).distinct().count());
        assertTrue(sites.values().stream().flatMap(site -> site.managedSlots().stream()).allMatch(bootstrap.bounds()::contains));
    }

    @Test
    void equalButIndependentlyRecoveredBootstrapsDoNotShareAnIdentityScopedDerivedPlan() {
        FrontierBootstrap initial = FrontierBootstrapper.create(new WorldId("frontier:resource-sites-identity"), 91L);
        FrontierBootstrap recovered = FrontierBootstrapper.create(new WorldId("frontier:resource-sites-identity"), 91L);

        assertEquals(initial, recovered, "the recovery fixture must retain the same immutable geometry");
        assertNotSame(initial, recovered, "the recovery fixture must not reuse the live canonical bootstrap object");
        assertNotSame(FrontierResourceSitePlan.compile(initial), FrontierResourceSitePlan.compile(recovered),
                "derived geometry is scoped to its exact immutable bootstrap owner, not value-equality cache work");
    }

    @Test
    void bootstrapDerivesTwelveBoundedNonOverlappingWheatFieldsOutsideStructuresAndRoutes() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:resource-sites"), 91L);
        Map<SubjectId, ResourceSite> first = FrontierResourceSitePlan.compile(bootstrap);
        Map<SubjectId, ResourceSite> second = FrontierResourceSitePlan.compile(bootstrap);
        assertSame(first, second, "one immutable bootstrap must reuse only its derived field geometry");
        assertThrows(UnsupportedOperationException.class, () -> first.clear(), "cached field geometry must remain immutable");
        Set<BlockPosition> structureCells = FrontierGrayboxPlan.compile(FrontierWorldState.initial(bootstrap)).cells().keySet();
        Set<BlockPosition> cropCells = new HashSet<>();
        Set<BlockPosition> managedCells = new HashSet<>();

        assertEquals(first, second); assertEquals(12, first.size());
        for (ResourceSite site : first.values()) {
            assertEquals(ResourceSiteKind.WHEAT_FIELD, site.kind()); assertEquals(252, site.cropSlots().size());
            assertEquals(4, site.irrigationSlots().size());
            assertEquals(15, site.cropSlots().stream().mapToInt(BlockPosition::x).max().orElseThrow()
                    - site.cropSlots().stream().mapToInt(BlockPosition::x).min().orElseThrow());
            assertEquals(15, site.cropSlots().stream().mapToInt(BlockPosition::z).max().orElseThrow()
                    - site.cropSlots().stream().mapToInt(BlockPosition::z).min().orElseThrow());
            assertTrue(site.soilSlots().stream().allMatch(soil -> site.irrigationSlots().stream().anyMatch(water ->
                    water.y() == soil.y() && Math.abs(water.x() - soil.x()) <= 4 && Math.abs(water.z() - soil.z()) <= 4)),
                    "every crop must be within vanilla hydration range of its declared source");
            assertEquals(site.cropSlots().stream().map(slot -> slot.offset(0, -1, 0)).toList(), site.soilSlots());
            assertTrue(site.managedSlots().stream().allMatch(bootstrap.bounds()::contains));
            assertTrue(site.managedSlots().stream().noneMatch(structureCells::contains),
                    () -> "field must not claim an immutable structure, route or public-access cell: " + site.id());
            assertTrue(cropCells.addAll(site.cropSlots()), "crop sites must not overlap each other");
            assertTrue(managedCells.addAll(site.managedSlots()), "no managed field cell may overlap another field: " + site.id());
        }
    }
}
