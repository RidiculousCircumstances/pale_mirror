package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierResourceSitePlanTest {
    @Test
    void bootstrapDerivesTwelveBoundedNonOverlappingWheatFieldsOutsideStructuresAndRoutes() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:resource-sites"), 91L);
        Map<SubjectId, ResourceSite> first = FrontierResourceSitePlan.compile(bootstrap);
        Map<SubjectId, ResourceSite> second = FrontierResourceSitePlan.compile(bootstrap);
        Set<BlockPosition> structureCells = FrontierGrayboxPlan.compile(FrontierWorldState.initial(bootstrap)).cells().keySet();
        Set<BlockPosition> cropCells = new HashSet<>();

        assertEquals(first, second); assertEquals(12, first.size());
        for (ResourceSite site : first.values()) {
            assertEquals(ResourceSiteKind.WHEAT_FIELD, site.kind()); assertEquals(64, site.cropSlots().size());
            assertTrue(site.cropSlots().stream().allMatch(bootstrap.bounds()::contains));
            assertTrue(site.cropSlots().stream().noneMatch(structureCells::contains));
            assertTrue(cropCells.addAll(site.cropSlots()), "crop sites must not overlap each other");
        }
    }
}
