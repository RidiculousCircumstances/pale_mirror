package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierResourceSitePlanTest {
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
            assertEquals(ResourceSiteKind.WHEAT_FIELD, site.kind()); assertEquals(64, site.cropSlots().size());
            assertEquals(site.cropSlots().stream().map(slot -> slot.offset(0, -1, 0)).toList(), site.soilSlots());
            assertTrue(site.managedSlots().stream().allMatch(bootstrap.bounds()::contains));
            assertTrue(site.managedSlots().stream().noneMatch(structureCells::contains),
                    () -> "field must not claim an immutable structure, route or public-access cell: " + site.id());
            assertTrue(cropCells.addAll(site.cropSlots()), "crop sites must not overlap each other");
            assertTrue(managedCells.addAll(site.managedSlots()), "no managed field cell may overlap another field: " + site.id());
        }
    }
}
