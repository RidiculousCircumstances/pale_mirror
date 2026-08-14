package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualAuditView;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VisualAuditPlannerTest {
    private static final VisualPoint ANCHOR = new VisualPoint(8_000, 72, -4_000);

    @Test void everyClimateAndArchetypeHasTheCompleteSemanticCameraGrammar() {
        for (FrontierClimate climate : FrontierClimate.values()) {
            for (int relief : new int[]{2, 7, 14}) {
                var region = new FrontierRegionPlanner().plan(9_182_733L + relief, climate.ordinal(),
                        new TerrainCandidate(ANCHOR, relief, 0, 0, relief), climate,
                        (requirement, candidates) -> new MountainMineAnchor(candidates.getFirst(), 0),
                        FrontierRegionPlanner::gradedManhattanRail);
                var views = VisualAuditPlanner.views(region);

                assertEquals(25, views.size(), climate + "/" + relief);
                assertEquals(views.size(), views.stream().map(VisualAuditView::id).distinct().count());
                assertEquals(Set.of("settlement", "mine", "railway"), views.stream()
                        .map(VisualAuditView::targetKind).collect(Collectors.toSet()));
                assertTrue(views.stream().allMatch(view -> view.dimensionId().equals("minecraft:overworld")));
                assertTrue(views.stream().allMatch(view -> Float.isFinite(view.yaw())
                        && Float.isFinite(view.pitch()) && view.pitch() >= -90.0F && view.pitch() <= 90.0F));
                assertContains(views, "settlement/market_square");
                assertContains(views, "settlement/residential_lane");
                assertContains(views, "primary_mine/industrial_campus");
                assertContains(views, "alternate_mine/portal");
                assertContains(views, "railway/corridor");
            }
        }
    }

    private static void assertContains(java.util.List<VisualAuditView> views, String id) {
        assertTrue(views.stream().anyMatch(view -> view.id().equals(id)), "missing semantic view " + id);
    }
}
